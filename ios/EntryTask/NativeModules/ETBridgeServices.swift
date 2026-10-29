// 文件职责：将五个TurboModule调用转给受控Native服务，并管理调用方生命周期。
import Foundation
import CoreFoundation
import CoreKit
import UserFeature

/// RN模块持有的受控facade；App只注入此对象，不向JS暴露容器或凭证。
@objc(ETBridgeServices) final class ETBridgeServices: NSObject {
    /// 进程组合根；Bridge中使用弱引用避免反向延长应用图生命周期。
    private weak var container: AppContainer?
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 按模块owner分组的在途任务，失效时统一取消。
    private var tasks: [String: [UUID: Task<Void, Never>]] = [:]
    /// 每个owner的Shell事件订阅，失效时移除。
    private var listeners: [String: (String) -> Void] = [:]
    /// 仍有效的模块实例身份集合，限制销毁后的回调。
    private var activeOwners = Set<String>()
    /// 最近已接收日志编号的去重集合。
    private var seen = Set<String>()
    /// 日志去重编号的有界淘汰顺序。
    private var order: [String] = []
    /// 上一条宿主命令的队尾，保证导航和认证命令串行。
    @MainActor private var commandTail: Task<Void, Never>?
    /// 排队中的宿主命令数，限制突发调用。
    @MainActor private var commandCount = 0
    /// 注入组合根并订阅Shell快照变化，模块owner由adapter分别注册。
    @MainActor init(container: AppContainer) {
        self.container = container; super.init()
        container.shell.changed = { [weak self] value in self?.broadcast(value) }
    }
    /// 登记模块实例owner，只有有效owner才能提交调用和接收回调。
    @objc func activate(_ owner: String) { lock.lock(); activeOwners.insert(owner); lock.unlock() }
    /// 登记当前owner的Shell事件监听，失效时按owner移除。
    @objc func observe(_ owner: String, handler: @escaping (String) -> Void) { lock.lock(); listeners[owner] = handler; lock.unlock() }
    /// 使页面/模块失效，清理其请求与监听，阻止旧结果回调。
    @objc func invalidate(_ owner: String) {
        lock.lock(); activeOwners.remove(owner); listeners.removeValue(forKey: owner)
        let pending = tasks.removeValue(forKey: owner); lock.unlock()
        pending?.values.forEach { $0.cancel() }
    }
    /// 移除完成的owner任务，空分组同步回收。
    private func finish(_ owner: String, id: UUID) { lock.lock(); tasks[owner]?.removeValue(forKey: id); if tasks[owner]?.isEmpty == true { tasks.removeValue(forKey: owner) }; lock.unlock() }
    /// 在锁内查询模块是否仍有效，避免向销毁实例交付结果。
    private func valid(_ owner: String) -> Bool { lock.lock(); defer { lock.unlock() }; return activeOwners.contains(owner) }
    /// 编码裸Shell快照，限制事件体积后通知有效监听方。
    private func broadcast(_ snapshot: ShellSnapshot) {
        guard let text = try? JSON.text(snapshot.json), text.utf8.count <= 8192 else { return }
        lock.lock(); let sinks = listeners; lock.unlock()
        for (owner, sink) in sinks where valid(owner) { sink(text) }
    }
    /// 把请求编号交给共享网络客户端，取消不依赖其他Promise排队。
    @objc func cancel(_ requestID: String) {
        Task { @MainActor [weak self] in self?.container?.network.cancel(requestID) }
    }
    /// 通过受控操作入口执行IO或Bridge调用，并保证生命周期配对。
    @objc func perform(_ module: String, method: String, input: String, owner: String, completion: @escaping (String) -> Void) {
        lock.lock()
        guard activeOwners.contains(owner) else { lock.unlock(); return }
        guard (tasks[owner]?.count ?? 0) < 64 else { lock.unlock(); completion(Self.failure("REQUEST_BUSY_OR_DUPLICATE")); return }
        let id = UUID()
        // 持锁期间先登记owner任务；实际执行切到MainActor，完成后统一移除登记。
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
            // 任务成功不代表实例仍存活；交付Promise前再次核对owner。
            if self.valid(owner) { completion(reply) }
        }
        tasks[owner, default: [:]][id] = task; lock.unlock()
    }
    /// 将安全错误码编码为统一Bridge错误信封。
    private static func failure(_ code: String) -> String { (try? JSON.text(["outcome": "error", "error": ["code": code]])) ?? "{}" }
    /// 按五模块分派调用，Native身份与存储权限在此边界校验。
    @MainActor private func run(_ module: String, method: String, raw: String, graph: AppContainer) async throws -> Any {
        if module == "NativeTelemetry" { return try logs(raw) }
        let input = try JSON.object(raw)
        switch module {
        case "NativeHost":
            if method == "bootstrap" {
                let resources = graph.resources
                // 安装包配置读取移至后台；协议和组件名都匹配才能完成bootstrap。
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
                // 串行等待上一命令，但不把命令ACK当作登录成功；会话事实由后续快照确认。
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
            // 身份由Native在调用时捕获，执行结束还需再核对，防止跨用户返回。
            let context = graph.session.requestContext()
            let request = APIRequest(apiID: api, requestID: id, params: input["params"] as? JSONObject ?? [:], body: input["body"] as? JSONObject ?? [:], timeoutMS: input["timeoutMs"] as? Int ?? 10000)
            let result = try await graph.network.execute(request, context: context)
            guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
            return ["httpStatus": result.status, "bodyText": String(decoding: result.data, as: UTF8.self)]
        case "NativeStorage":
            guard let key = input["key"] as? String else { throw AppFailure("STORAGE_ERROR") }
            // 身份由Native在调用时捕获，执行结束还需再核对，防止跨用户返回。
            let context = graph.session.requestContext()
            guard context.scope != "guest" else { throw AppFailure("AUTH_REQUIRED") }
            // 偏好操作与退出提交共用队列；开始前和完成后分别拒绝旧scope。
            let result = try await graph.commits.perform {
                guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
                return try graph.preferences.perform(method, key: key, scope: context.scope, value: input["value"])
            }
            guard graph.session.requestContext() == context else { throw AppFailure("STALE_SESSION") }; return result
        default: throw AppFailure("MODULE_NOT_SUPPORTED")
        }
    }
    /// 拒绝布尔、非整数、非有限数及越界值，避免JS参数被隐式转换。
    private static func integer(_ value: Any?, range: ClosedRange<Int>) throws -> Int {
        guard let number = value as? NSNumber, CFGetTypeID(number) != CFBooleanGetTypeID(), number.doubleValue.isFinite,
              number.doubleValue == Double(number.intValue), range.contains(number.intValue) else { throw AppFailure("INVALID_HOST_COMMAND") }
        return number.intValue
    }
    /// 限制日志批次/字节数，按eventId去重后走白名单Logger。
    private func logs(_ raw: String) throws -> Bool {
        guard raw.utf8.count <= 32768, let events = try JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [JSONObject], events.count <= 20 else { throw AppFailure("INVALID_LOG_BATCH") }
        for event in events {
            guard let id = event["eventId"] as? String, id.count <= 160 else { throw AppFailure("INVALID_LOG_BATCH") }
            lock.lock()
            if seen.contains(id) { lock.unlock(); continue }
            seen.insert(id); order.append(id)
            // 去重记录最多保留1000项，避免日志长时间运行后无界增长。
            if order.count > 1000 { seen.remove(order.removeFirst()) }
            lock.unlock()
            AppLogger.log(event["event"] as? String ?? "rn.event", event, error: event["level"] as? String == "error")
        }
        return true
    }
}
