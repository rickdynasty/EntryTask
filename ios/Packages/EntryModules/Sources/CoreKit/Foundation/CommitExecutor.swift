import Foundation

/// 会话变更与模拟写事务共用的串行提交边界；延迟/KDF在队列外，主线程只能await。
public final class CommitExecutor: Sendable {
    private let queue = DispatchQueue(label: "EntryTask.Commit", qos: .userInitiated)
    public init() {}
    public func sync<T>(_ operation: () throws -> T) rethrows -> T { try queue.sync(execute: operation) }
    public func perform<T>(_ operation: @escaping () throws -> T) async throws -> T {
        let cancellation = CancellationState()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    do {
                        // 排队期间取消的操作不再开始写入；已经开始的短事务由数据库保证原子性。
                        try cancellation.check()
                        continuation.resume(returning: try operation())
                    } catch { continuation.resume(throwing: error) }
                }
            }
        }, onCancel: { cancellation.cancel() })
    }
}
