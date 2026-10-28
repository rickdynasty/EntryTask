import Foundation

/// 狭窄同步边界：唯一可变字段始终由同一锁保护，供任务与提交队列交接取消状态。
public final class CancellationState: @unchecked Sendable {
    private let lock = NSLock()
    private var canceled = false
    public init() {}
    public func cancel() { lock.lock(); canceled = true; lock.unlock() }
    public func check() throws { lock.lock(); defer { lock.unlock() }; if canceled { throw CancellationError() } }
}
