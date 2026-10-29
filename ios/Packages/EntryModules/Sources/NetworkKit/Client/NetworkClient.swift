// 文件职责：受控网络客户端，集中处理白名单、取消、超时、限流和一次终态。
import Foundation
import CoreKit

/// 专用Demo client：请求登记、deadline、字节上限和一次终态统一管理。
public final class NetworkClient: NSObject, NetworkPort, URLSessionDataDelegate, @unchecked Sendable {
    /// 单次在途请求的响应累积、超时及continuation状态。
    private final class Call {
        /// 本次API描述，完成时用于安全日志关联。
        let request: APIRequest
        /// 本次URLSession传输任务，可独立终止。
        let task: URLSessionDataTask
        /// 单调时钟开始值，用于计算耗时而不受系统校时影响。
        let started = ProcessInfo.processInfo.systemUptime
        /// 异步调用恢复点，只有一次终态能完成。
        let continuation: CheckedContinuation<HTTPReply, Error>
        /// 本次响应累积缓冲区，超过256KiB即取消。
        var data = Data()
        /// 从响应头取得的HTTP状态码，尚未收到时为0。
        var status = 0
        /// 主动取消、超时或体积超限原因，优先于底层错误。
        var reason: String?
        /// 本次请求超时任务，完成后必须取消。
        var deadline: DispatchWorkItem?
        /// 记录本次请求、底层任务与异步恢复点，响应缓冲区从空开始。
        init(_ request: APIRequest, _ task: URLSessionDataTask, _ continuation: CheckedContinuation<HTTPReply, Error>) {
            self.request = request; self.task = task; self.continuation = continuation
        }
    }
    /// 安装包资源提供者，运行时不访问开发机文件。
    private let resources: ResourceProvider
    /// 专用Mock处理器，不安装到全局网络会话。
    private let handler: MockHandler
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 受锁保护的在途请求索引，最大并发32且requestId唯一。
    private var calls: [String: Call] = [:]
    /// 先取消后登记的短期记录，防止竞态下请求复活。
    private var tombstones: [String: TimeInterval] = [:]
    /// 仅本客户端使用的无缓存URLSession，所有请求进入专用Mock协议。
    private var session: URLSession!
    /// 创建无Cookie/缓存的专用URLSession，仅此会话安装DemoURLProtocol。
    public init(resources: ResourceProvider, handler: MockHandler) {
        self.resources = resources; self.handler = handler; super.init()
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [DemoURLProtocol.self]
        config.httpShouldSetCookies = false; config.httpCookieStorage = nil; config.urlCache = nil
        let queue = OperationQueue(); queue.maxConcurrentOperationCount = 1; queue.qualityOfService = .utility
        session = URLSession(configuration: config, delegate: self, delegateQueue: queue)
    }
    /// 关闭专用URLSession并取消其在途任务。
    public func close() { session.invalidateAndCancel() }
    /// 校验契约与身份，登记请求及deadline，再通过可取消URLSession执行。
    public func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply {
        try Task.checkCancellation()
        guard request.requestID.range(of: "^[A-Za-z0-9-]{1,100}$", options: .regularExpression) != nil else { throw AppFailure("INVALID_REQUEST_ID") }
        // 仅按已打包manifest选择路由；失败不会自动切换公网或另一条通路。
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
        // 认证使用表单编码，普通业务使用JSON；特殊字符必须保留原始密码语义。
        if mutable.httpMethod == "POST" {
            if request.nativeOnly {
                /// 逐UTF-8字节做表单百分号编码，保留加号、空格及非ASCII密码。
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
        // 把处理器、身份及原始body附着到当前请求，避免URLSession改为body stream后丢失输入。
        URLProtocol.setProperty(ProtocolContext(handler: handler, context: context, body: mutable.httpBody), forKey: DemoURLProtocol.contextKey, in: mutable)
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                let task = session.dataTask(with: mutable as URLRequest)
                lock.lock()
                // 同ID在途拒绝，整体最多32个请求；不能覆盖旧continuation。
                if calls.count >= 32 || calls[request.requestID] != nil {
                    lock.unlock(); task.cancel(); continuation.resume(throwing: AppFailure("REQUEST_BUSY_OR_DUPLICATE")); return
                }
                let now = ProcessInfo.processInfo.systemUptime
                // 先消费有效取消墓碑，再登记请求，覆盖取消先到的竞态。
                tombstones = tombstones.filter { now - $0.value < 30 }
                if tombstones.removeValue(forKey: request.requestID) != nil || Task.isCancelled {
                    lock.unlock(); task.cancel(); continuation.resume(throwing: AppFailure("CANCELLED")); return
                }
                let call = Call(request, task, continuation)
                calls[request.requestID] = call
                let timeout = DispatchWorkItem { [weak self, weak call] in
                    guard let call else { return }; self?.stop(request.requestID, reason: "TIMEOUT", expected: call)
                }
                // 超时闭包捕获具体Call，旧deadline不得取消后来复用同ID的新请求。
                call.deadline = timeout
                lock.unlock()
                AppLogger.log("request.started", ["apiId": request.apiID, "requestId": request.requestID])
                DispatchQueue.global().asyncAfter(deadline: .now() + .milliseconds(min(10000, max(1000, request.timeoutMS))), execute: timeout)
                task.resume()
            }
        }, onCancel: { self.cancel(request.requestID) })
    }
    /// 取消指定请求或当前任务；取消与业务失败分开处理。
    public func cancel(_ requestID: String) { stop(requestID, reason: "CANCELLED") }
    /// 记录第一个终止原因；未登记的取消以有界短期墓碑保留。
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
    /// 收到响应头时记录HTTP状态，并提前拒绝超限响应。
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        lock.lock(); defer { lock.unlock() }
        guard let call = calls.values.first(where: { $0.task === dataTask }) else { completionHandler(.cancel); return }
        call.status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if response.expectedContentLength > 262144 { call.reason = "PAYLOAD_TOO_LARGE"; completionHandler(.cancel) }
        else { completionHandler(.allow) }
    }
    /// 逐块累积响应，超过字节上限立即终止传输。
    public func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock(); defer { lock.unlock() }
        guard let call = calls.values.first(where: { $0.task === dataTask }) else { return }
        if call.data.count + data.count > 262144 { call.reason = "PAYLOAD_TOO_LARGE"; dataTask.cancel() }
        else { call.data.append(data) }
    }
    /// 移除在途登记并撤销deadline，再以安全错误或响应恢复continuation一次。
    public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        guard let (id, call) = calls.first(where: { $0.value.task === task }) else { lock.unlock(); return }
        // 先移除登记并撤销超时，再恢复等待方；多个终态竞争只能完成一次。
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
