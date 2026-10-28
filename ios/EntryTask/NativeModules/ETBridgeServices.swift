import Foundation
import CoreFoundation
import CoreKit
import UserFeature

/// RN模块持有的受控facade；App只注入此对象，不向JS暴露容器或凭证。
@objc(ETBridgeServices) final class ETBridgeServices: NSObject {
    private weak var container: AppContainer?
    private let lock = NSLock()
    private var tasks: [String: [UUID: Task<Void, Never>]] = [:]
    private var listeners: [String: (String) -> Void] = [:]
    private var activeOwners = Set<String>()
    private var seen = Set<String>()
    private var order: [String] = []
    @MainActor private var commandTail: Task<Void, Never>?
    @MainActor private var commandCount = 0
    @MainActor init(container: AppContainer) {
        self.container = container; super.init()
        container.shell.changed = { [weak self] value in self?.broadcast(value) }
    }
    @objc func activate(_ owner: String) { lock.lock(); activeOwners.insert(owner); lock.unlock() }
    @objc func observe(_ owner: String, handler: @escaping (String) -> Void) { lock.lock(); listeners[owner] = handler; lock.unlock() }
    @objc func invalidate(_ owner: String) {
        lock.lock(); activeOwners.remove(owner); listeners.removeValue(forKey: owner)
        let pending = tasks.removeValue(forKey: owner); lock.unlock()
        pending?.values.forEach { $0.cancel() }
    }
    private func finish(_ owner: String, id: UUID) { lock.lock(); tasks[owner]?.removeValue(forKey: id); if tasks[owner]?.isEmpty == true { tasks.removeValue(forKey: owner) }; lock.unlock() }
    private func valid(_ owner: String) -> Bool { lock.lock(); defer { lock.unlock() }; return activeOwners.contains(owner) }
    private func broadcast(_ snapshot: ShellSnapshot) {
        guard let text = try? JSON.text(snapshot.json), text.utf8.count <= 8192 else { return }
        lock.lock(); let sinks = listeners; lock.unlock()
        for (owner, sink) in sinks where valid(owner) { sink(text) }
    }
    @objc func cancel(_ requestID: String) {
        Task { @MainActor [weak self] in self?.container?.network.cancel(requestID) }
    }
    @objc func perform(_ module: String, method: String, input: String, owner: String, completion: @escaping (String) -> Void) {
        lock.lock()
        guard activeOwners.contains(owner) else { lock.unlock(); return }
        guard (tasks[owner]?.count ?? 0) < 64 else { lock.unlock(); completion(Self.failure("REQUEST_BUSY_OR_DUPLICATE")); return }
        let id = UUID()
        let task = Task { @MainActor [weak self] in
            guard let self else { return }
            defer { self.finish(owner, id: id) }
            let reply: String
            do {
                guard let graph = self.container else { throw AppFailure("HOST_NOT_READY") }
                let data = try await self.run(module, method: method, raw: input, graph: graph)
                try Task.checkCancellation()
                let encoded = try JSON.text(["outcome": "ok", "data": data])
                guard encoded.utf8.count <= 262144 else { throw AppFailure("PAYLOAD_TOO_LARGE") }
                reply = encoded
            } catch {
                let code = error is CancellationError ? "CANCELLED" : (error as? AppFailure)?.code ?? "NATIVE_ERROR"
                AppLogger.log(code == "CANCELLED" ? "bridge.cancelled" : "bridge.failed", ["module": module, "code": code], error: code != "CANCELLED")
                reply = Self.failure(code)
            }
            if self.valid(owner) { completion(reply) }
        }
        tasks[owner, default: [:]][id] = task; lock.unlock()
    }
    private static func failure(_ code: String) -> String { (try? JSON.text(["outcome": "error", "error": ["code": code]])) ?? "{}" }
    @MainActor private func run(_ module: String, method: String, raw: String, graph: AppContainer) async throws -> Any {
        if module == "NativeTelemetry" { return try logs(raw) }
        let input = try JSON.object(raw)
        switch module {
        case "NativeHost":
            if method == "bootstrap" {
                let resources = graph.resources
                let runtime = try await Task.detached { try JSON.object(resources.read("contracts/bridge/runtime.json")) }.value
                guard input["protocolVersion"] as? String == runtime["protocolVersion"] as? String else { throw AppFailure("PROTOCOL_VERSION_MISMATCH") }
                guard runtime["componentName"] as? String == "EntryTask" else { throw AppFailure("HOST_RESOURCE_INVALID") }
                graph.shell.refresh()
                AppLogger.log("bootstrap.completed", ["flowId": input["flowId"] as? String ?? "", "epoch": graph.session.snapshot().epoch])
                return ["protocolVersion": "1.1", "componentName": "EntryTask", "bootId": graph.bootID,
                        "capabilities": ["host.events", "host.command", "network.execute", "storage.assetVisibility", "session.snapshot", "telemetry.emitBatch"], "shell": graph.shell.snapshot.json]
            }
            guard method == "command", commandCount < 64 else { throw AppFailure("HOST_COMMAND_BUSY") }
            commandCount += 1; defer { commandCount -= 1 }
            let previous = commandTail
            let command = Task { @MainActor in
                await previous?.value; try Task.checkCancellation()
                switch input["type"] as? String {
                case "selectTab": graph.shell.selectTab(try Self.integer(input["tabId"], range: 1...4))
                case "navigation": graph.shell.navigation(try Self.integer(input["depth"], range: 0...20))
                case "authenticate":
                    if graph.session.snapshot().authenticated { graph.shell.refresh(force: true) }
                    else { guard let host = graph.host else { throw AppFailure("HOST_NOT_READY") }; try host.requestAuthentication() }
                case "logout": try await graph.shell.logout()
                default: throw AppFailure("INVALID_HOST_COMMAND")
                }
            }
            commandTail = Task { _ = try? await command.value }
            try await withTaskCancellationHandler(operation: { try await command.value }, onCancel: { command.cancel() })
            return true
        case "NativeSession":
            let value = graph.session.snapshot(); return ["authenticated": value.authenticated, "sessionEpoch": value.epoch]
        case "NativeNetwork":
            guard let api = input["apiId"] as? String, let id = input["requestId"] as? String else { throw AppFailure("INVALID_REQUEST") }
            let context = graph.session.requestContext()
            let request = APIRequest(apiID: api, requestID: id, params: input["params"] as? JSONObject ?? [:], body: input["body"] as? JSONObject ?? [:], timeoutMS: input["timeoutMs"] as? Int ?? 10000)
            let result = try await graph.network.execute(request, context: context)
            guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
            return ["httpStatus": result.status, "bodyText": String(decoding: result.data, as: UTF8.self)]
        case "NativeStorage":
            guard let key = input["key"] as? String else { throw AppFailure("STORAGE_ERROR") }
            let context = graph.session.requestContext()
            guard context.scope != "guest" else { throw AppFailure("AUTH_REQUIRED") }
            let result = try await graph.commits.perform {
                guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
                return try graph.preferences.perform(method, key: key, scope: context.scope, value: input["value"])
            }
            guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }; return result
        default: throw AppFailure("MODULE_NOT_SUPPORTED")
        }
    }
    private static func integer(_ value: Any?, range: ClosedRange<Int>) throws -> Int {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(), number.doubleValue.isFinite,
              number.doubleValue == Double(number.intValue), range.contains(number.intValue) else { throw AppFailure("INVALID_HOST_COMMAND") }
        return number.intValue
    }
    private func logs(_ raw: String) throws -> Bool {
        guard raw.utf8.count <= 32768, let events = try JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [JSONObject], events.count <= 20 else { throw AppFailure("INVALID_LOG_BATCH") }
        for event in events {
            guard let id = event["eventId"] as? String, id.count <= 160 else { throw AppFailure("INVALID_LOG_BATCH") }
            lock.lock()
            if seen.contains(id) { lock.unlock(); continue }
            seen.insert(id); order.append(id)
            if order.count > 1000 { seen.remove(order.removeFirst()) }
            lock.unlock()
            AppLogger.log(event["event"] as? String ?? "rn.event", event, error: event["level"] as? String == "error")
        }
        return true
    }
}
