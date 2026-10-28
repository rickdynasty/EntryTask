import Foundation
import CoreKit

/// 专用Demo client：请求登记、deadline、字节上限和一次终态统一管理。
public final class NetworkClient: NSObject, NetworkPort, URLSessionDataDelegate, @unchecked Sendable {
    private final class Call {
        let request: APIRequest
        let task: URLSessionDataTask
        let started = ProcessInfo.processInfo.systemUptime
        let continuation: CheckedContinuation<HTTPReply, Error>
        var data = Data()
        var status = 0
        var reason: String?
        var deadline: DispatchWorkItem?
        init(_ request: APIRequest, _ task: URLSessionDataTask, _ continuation: CheckedContinuation<HTTPReply, Error>) {
            self.request = request; self.task = task; self.continuation = continuation
        }
    }
    private let resources: ResourceProvider
    private let handler: MockHandler
    private let lock = NSLock()
    private var calls: [String: Call] = [:]
    private var tombstones: [String: TimeInterval] = [:]
    private var session: URLSession!
    public init(resources: ResourceProvider, handler: MockHandler) {
        self.resources = resources; self.handler = handler; super.init()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [DemoURLProtocol.self]
        config.httpShouldSetCookies = false; config.httpCookieStorage = nil; config.urlCache = nil
        let queue = OperationQueue(); queue.maxConcurrentOperationCount = 1; queue.qualityOfService = .utility
        session = URLSession(configuration: config, delegate: self, delegateQueue: queue)
    }
    public func close() { session.invalidateAndCancel() }
    public func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply {
        try Task.checkCancellation()
        guard request.requestID.range(of: "^[A-Za-z0-9-]{1,100}$", options: .regularExpression) != nil else { throw AppFailure("INVALID_REQUEST_ID") }
        let manifest = try JSON.object(resources.read("mock/manifest.json"))
        guard let routes = manifest["routes"] as? [JSONObject], let route = routes.first(where: { $0["apiId"] as? String == request.apiID }), request.nativeOnly || route["exposure"] as? String == "jsAllowed" else { throw AppFailure("ENDPOINT_NOT_ALLOWED") }
        var path = route["path"] as? String ?? ""
        if path.contains("{id}") {
            guard let id = request.params["id"] as? String, id.range(of: "^[A-Za-z0-9_-]{1,100}$", options: .regularExpression) != nil else { throw AppFailure("INVALID_REQUEST") }
            path = path.replacingOccurrences(of: "{id}", with: id)
        }
        let allowed: Set<String> = ["page", "pageSize", "category", "scene", "id", "direction", "accountId", "type", "scope", "keyword"]
        guard request.params.keys.allSatisfy({ allowed.contains($0) }), route["requiresAuth"] as? Bool != true || context.scope != "guest" else { throw AppFailure("AUTH_REQUIRED") }
        var components = URLComponents(string: "https://entrytask.invalid" + path)!
        components.queryItems = request.params.keys.sorted().map { URLQueryItem(name: $0, value: String(describing: request.params[$0]!)) }
        let mutable = NSMutableURLRequest(url: components.url!)
        guard let method = route["method"] as? String, ["GET", "POST"].contains(method) else { throw AppFailure("ENDPOINT_NOT_ALLOWED") }
        mutable.httpMethod = method
        mutable.setValue(request.requestID, forHTTPHeaderField: "X-Request-Id")
        mutable.setValue(request.apiID, forHTTPHeaderField: "X-Demo-Api")
        mutable.setValue(request.nativeOnly ? "true" : "false", forHTTPHeaderField: "X-Demo-Native")
        if mutable.httpMethod == "POST" {
            if request.nativeOnly {
                func escaped(_ value: String) -> String {
                    value.utf8.map { byte in
                        if (65...90).contains(byte) || (97...122).contains(byte) || (48...57).contains(byte) || [45,46,95,126].contains(byte) { return String(UnicodeScalar(byte)) }
                        return String(format: "%%%02X", byte)
                    }.joined()
                }
                mutable.httpBody = Data(request.body.keys.sorted().map { escaped($0) + "=" + escaped(request.body[$0] as? String ?? "") }.joined(separator: "&").utf8)
                mutable.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            } else {
                mutable.httpBody = try JSON.data(request.body); mutable.setValue("application/json", forHTTPHeaderField: "Content-Type")
            }
        }
        URLProtocol.setProperty(ProtocolContext(handler: handler, context: context, body: mutable.httpBody), forKey: DemoURLProtocol.contextKey, in: mutable)
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                let task = session.dataTask(with: mutable as URLRequest)
                lock.lock()
                if calls.count >= 32 || calls[request.requestID] != nil {
                    lock.unlock(); task.cancel(); continuation.resume(throwing: AppFailure("REQUEST_BUSY_OR_DUPLICATE")); return
                }
                let now = ProcessInfo.processInfo.systemUptime
                tombstones = tombstones.filter { now - $0.value < 30 }
                if tombstones.removeValue(forKey: request.requestID) != nil || Task.isCancelled {
                    lock.unlock(); task.cancel(); continuation.resume(throwing: AppFailure("CANCELLED")); return
                }
                let call = Call(request, task, continuation)
                calls[request.requestID] = call
                let timeout = DispatchWorkItem { [weak self, weak call] in
                    guard let call else { return }; self?.stop(request.requestID, reason: "TIMEOUT", expected: call)
                }
                call.deadline = timeout
                lock.unlock()
                AppLogger.log("request.started", ["apiId": request.apiID, "requestId": request.requestID])
                DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(min(10000, max(1000, request.timeoutMS))), execute: timeout)
                task.resume()
            }
        }, onCancel: { self.cancel(request.requestID) })
    }
    public func cancel(_ requestID: String) { stop(requestID, reason: "CANCELLED") }
    private func stop(_ id: String, reason: String, expected: Call? = nil) {
        lock.lock(); defer { lock.unlock() }
        if let call = calls[id] {
            guard expected == nil || call === expected else { return }
            if call.reason == nil { call.reason = reason }
            call.task.cancel()
        } else if expected == nil && id.range(of: "^[A-Za-z0-9-]{1,100}$", options: .regularExpression) != nil {
            let now = ProcessInfo.processInfo.systemUptime
            tombstones = tombstones.filter { now - $0.value < 30 }
            if tombstones.count < 128 { tombstones[id] = now }
        }
    }
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        lock.lock(); defer { lock.unlock() }
        guard let call = calls.values.first(where: { $0.task === dataTask }) else { completionHandler(.cancel); return }
        call.status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if response.expectedContentLength > 262144 { call.reason = "PAYLOAD_TOO_LARGE"; completionHandler(.cancel) }
        else { completionHandler(.allow) }
    }
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock(); defer { lock.unlock() }
        guard let call = calls.values.first(where: { $0.task === dataTask }) else { return }
        if call.data.count + data.count > 262144 { call.reason = "PAYLOAD_TOO_LARGE"; dataTask.cancel() }
        else { call.data.append(data) }
    }
    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        guard let (id, call) = calls.first(where: { $0.value.task === task }) else { lock.unlock(); return }
        calls.removeValue(forKey: id); call.deadline?.cancel(); lock.unlock()
        let canceled = call.reason == "CANCELLED"
        let failed = call.reason != nil || error != nil
        AppLogger.log(canceled ? "request.cancelled" : failed ? "request.failed" : "request.completed", ["apiId": call.request.apiID, "requestId": id, "durationMs": Int((ProcessInfo.processInfo.systemUptime - call.started) * 1000), "code": call.reason ?? (error == nil ? "OK" : "NETWORK_UNAVAILABLE")], error: failed && !canceled)
        if let reason = call.reason { call.continuation.resume(throwing: AppFailure(reason)) }
        else if let failure = error as? AppFailure { call.continuation.resume(throwing: failure) }
        else if error != nil { call.continuation.resume(throwing: AppFailure("NETWORK_UNAVAILABLE")) }
        else { call.continuation.resume(returning: HTTPReply(status: call.status, data: call.data)) }
    }
}
