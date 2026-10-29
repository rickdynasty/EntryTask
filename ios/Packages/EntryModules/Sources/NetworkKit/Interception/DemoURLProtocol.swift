// 文件职责：URLSession复制请求时保留的处理器、身份上下文和原始请求体。
import Foundation
import CoreKit

/// URLSession复制请求时保留的Native上下文，不使用可变全局handler。
final class ProtocolContext: NSObject {
    /// 专用Mock处理器，不安装到全局网络会话。
    let handler: MockHandler
    /// 本次请求捕获的epoch与scope，不能由传输头覆盖。
    let context: RequestContext
    /// 原始受控请求体，仅供传输或Mock读取，不写入日志。
    let body: Data?
    /// 保留处理器、身份与原始请求体，使URLSession复制后仍可供Mock读取。
    init(handler: MockHandler, context: RequestContext, body: Data?) { self.handler = handler; self.context = context; self.body = body }
}
/// 仅安装到Demo专用URLSession的本地网络拦截器。
final class DemoURLProtocol: URLProtocol {
    /// URLProtocol传递受控上下文的内部键。
    static let contextKey = "EntryTask.MockContext"
    /// 拦截回调与停止操作的递归锁，保护一次终态。
    private let gate = NSRecursiveLock()
    /// 终态标记，取消或交付后不再回调URLSession。
    private var stopped = false
    /// 本次拦截处理任务，stopLoading时取消。
    private var work: Task<Void, Never>?
    /// 仅处理专用会话交来的HTTP/HTTPS请求，不全局拦截网络。
    override class func canInit(with request: URLRequest) -> Bool { ["https", "http"].contains(request.url?.scheme ?? "") }
    /// 保留原始受控请求，避免改写路由或丢失上下文。
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    /// 创建可取消Mock任务，恢复原始body后交给模拟服务。
    override func startLoading() {
        gate.lock(); defer { gate.unlock() }
        guard !stopped else { return }
        work = Task { [weak self] in
            guard let self else { return }
            do {
                guard let box = URLProtocol.property(forKey: Self.contextKey, in: request) as? ProtocolContext else { throw AppFailure("MOCK_CONTEXT_MISSING") }
                guard request.url?.host == "entrytask.invalid" else { throw AppFailure("ENDPOINT_NOT_ALLOWED") }
                // URLSession可能将httpBody改为流；保留原始受控body供本地拦截，避免空表单/空JSON。
                var localRequest = request
                if localRequest.httpBody == nil { localRequest.httpBody = box.body }
                let reply = try await box.handler.handle(localRequest, context: box.context)
                try Task.checkCancellation()
                deliver(reply)
            } catch { fail(error) }
        }
    }
    // 锁内没有await；短同步函数避免在异步上下文持锁或跨挂起点。
    private func deliver(_ reply: HTTPReply) {
        gate.lock(); defer { gate.unlock() }
        guard !stopped, let url = request.url, let response = HTTPURLResponse(url: url, statusCode: reply.status, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json; charset=utf-8"]) else { return }
        // 每次通知后重新检查停止标记，允许URLSession在回调中同步取消。
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        guard !stopped else { return }
        client?.urlProtocol(self, didLoad: reply.data)
        guard !stopped else { return }
        stopped = true; client?.urlProtocolDidFinishLoading(self); work = nil
    }
    /// 只在尚未停止时交付一次失败，并释放工作任务。
    private func fail(_ error: Error) {
        gate.lock(); defer { gate.unlock() }
        guard !stopped else { return }
        stopped = true; client?.urlProtocol(self, didFailWithError: error); work = nil
    }
    /// 先封闭后续回调，再取消后台处理任务。
    override func stopLoading() {
        // 停止标记先于取消任务，后台迟到成功或失败都不再通知client。
        gate.lock(); stopped = true; let pending = work; work = nil; gate.unlock()
        pending?.cancel()
    }
}
