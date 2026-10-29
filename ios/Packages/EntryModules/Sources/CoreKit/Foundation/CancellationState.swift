// 文件职责：用短锁把异步取消信号传给同步提交操作。
import Foundation

/// 狭窄同步边界：唯一可变字段始终由同一锁保护，供任务与提交队列交接取消状态。
public final class CancellationState: @unchecked Sendable {
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 受锁保护的取消标记，供同步操作开始前检查。
    private var canceled = false
    /// 创建默认初始状态，不执行网络或磁盘操作。
    public init() {}
    /// 在短锁内记录取消，后续同步检查会抛出CancellationError。
    public func cancel() { lock.lock(); canceled = true; lock.unlock() }
    /// 在同步执行边界检查取消标记，已取消则抛出CancellationError。
    public func check() throws { lock.lock(); defer { lock.unlock() }; if canceled { throw CancellationError() } }
}
