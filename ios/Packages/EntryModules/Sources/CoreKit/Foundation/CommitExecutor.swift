// 文件职责：会话变更和模拟写事务共用的后台串行提交边界。
import Foundation

/// 会话变更与模拟写事务共用的串行提交边界；延迟/KDF在队列外，主线程只能await。
public final class CommitExecutor: Sendable {
    /// 后台串行队列，隔离提交或阻塞IO。
    private let queue = DispatchQueue(label: "EntryTask.Commit", qos: .userInitiated)
    /// 创建默认初始状态，不执行网络或磁盘操作。
    public init() {}
    /// 串行执行短提交操作；调用方不能在同一队列递归同步等待。
    public func sync<T>(_ operation: () throws -> T) rethrows -> T { try queue.sync(execute: operation) }
    /// 把提交排入后台串行队列，执行前再次检查取消并恢复异步等待方。
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
