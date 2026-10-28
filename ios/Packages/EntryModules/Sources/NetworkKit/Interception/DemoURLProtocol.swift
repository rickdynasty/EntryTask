import Foundation
import CoreKit

/// URLSession复制请求时保留的Native上下文，不使用可变全局handler。
final class ProtocolContext: NSObject {
    let handler: MockHandler
    let context: RequestContext
    let body: Data?
    init(handler: MockHandler, context: RequestContext, body: Data?) { self.handler = handler; self.context = context; self.body = body }
}
final class DemoURLProtocol: URLProtocol {
    static let contextKey = "EntryTask.MockContext"
    private let gate = NSRecursiveLock()
    private var stopped = false
    private var work: Task<Void, Never>?
    override class func canInit(with request: URLRequest) -> Bool { ["https", "http"].contains(request.url?.scheme ?? "") }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
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
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        guard !stopped else { return }
        client?.urlProtocol(self, didLoad: reply.data)
        guard !stopped else { return }
        stopped = true; client?.urlProtocolDidFinishLoading(self); work = nil
    }
    private func fail(_ error: Error) {
        gate.lock(); defer { gate.unlock() }
        guard !stopped else { return }
        stopped = true; client?.urlProtocol(self, didFailWithError: error); work = nil
    }
    override func stopLoading() {
        gate.lock(); stopped = true; let pending = work; work = nil; gate.unlock()
        pending?.cancel()
    }
}
