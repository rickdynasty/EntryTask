// 文件职责：区域级数据、加载和错误状态，刷新失败允许保留旧值。
import Foundation
import Combine
import CoreKit

/// 每个区域独立首载/刷新/错误；刷新失败保留已有内容，版本号阻止迟到结果回写。
public struct ResourceState<Value> {
    /// 最近成功数据；刷新中或失败时可保留展示。
    public var value: Value?
    /// 区域请求进行中，与写操作防重状态分开。
    public var loading = false
    /// 区域安全错误提示，取消不作为失败显示。
    public var error: String?
}
/// 管理单个区域请求、取消与代次，阻止迟到响应覆盖新状态。
@MainActor public final class PageResource<Value>: ObservableObject {
    /// 当前可观察UI状态或模拟账本，不允许跨会话迟到写回。
    @Published public private(set) var state = ResourceState<Value>()
    /// 当前区域查询任务，新查询、清空或停用时取消。
    private var request: Task<Void, Never>?
    /// 请求代次，每次取消/替换递增以隔离旧回调。
    private var version = 0
    /// 创建默认初始状态，不执行网络或磁盘操作。
    public init() {}
    /// 取消旧查询并递增代次；新结果或错误仅能更新当前代次。
    public func load(_ operation: @escaping () async throws -> Value) {
        // 每次load都先使旧版本失效；保留已有value以支持带数据刷新。
        cancel(); let current = version; state.loading = true; state.error = nil
        request = Task { [weak self] in
            do {
                let value = try await operation()
                // 调用方即使不响应取消，迟到结果也必须通过版本检查才能交付。
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state = ResourceState(value: value, loading: false, error: nil)
            } catch {
                // 调用方即使不响应取消，迟到结果也必须通过版本检查才能交付。
                guard let self, self.version == current, !Task.isCancelled else { return }
                // 刷新失败保留最近成功值；取消和旧会话失效不显示普通失败提示。
                self.state.loading = false
                let code = (error as? AppFailure)?.code ?? "NETWORK_UNAVAILABLE"
                guard !(error is CancellationError), !["CANCELLED", "STALE_SESSION"].contains(code) else { return }
                self.state.error = "内容加载失败（\(code)）"
                AppLogger.log("dashboard.resource.failed", ["code": code], error: true)
            }
        }
    }
    /// 取消指定请求或当前任务；取消与业务失败分开处理。
    public func cancel() { version += 1; request?.cancel(); request = nil; state.loading = false }
    /// 取消旧请求并清空区域数据及错误。
    public func clear() { cancel(); state = ResourceState() }
    /// 用已确认事实替换区域状态，同时使旧查询结果失效。
    public func accept(_ value: Value) { cancel(); state = ResourceState(value: value, loading: false, error: nil) }
    /// 释放所有者时取消仍在运行的任务，避免继续交付页面结果。
    deinit { request?.cancel() }
}
/// 页面只在已认证、前台和当前Tab时活跃；后台暂停而不清空当前会话内容。
@MainActor public class RootPageViewModel: ObservableObject {
    /// 页面或轮播是否允许工作，由当前Tab与前台状态决定。
    public private(set) var active = false
    /// 会话代次；退出和认证提交后变化，旧结果不得跨代次回写。
    public private(set) var epoch = -1
    /// 激活页面或Bridge所有者；只为有效生命周期接收工作。
    public func activate(epoch: Int) {
        let changed = self.epoch != epoch
        if changed { deactivate(); reset(); self.epoch = epoch }
        guard !active else { return }; active = true; refresh()
    }
    /// 暂停页面工作并取消在途操作，保留本会话已成功内容。
    public func deactivate() { active = false; cancel() }
    /// 使页面/模块失效，清理其请求与监听，阻止旧结果回调。
    public func invalidate() { deactivate(); reset(); epoch = -1 }
    /// 刷新当前页面数据或会话快照；具体资源由所属VM独立管理。
    public func refresh() {}
    /// 取消指定请求或当前任务；取消与业务失败分开处理。
    func cancel() {}
    /// 清空当前会话数据和分类；不同用户不复用旧展示值。
    func reset() {}
}
