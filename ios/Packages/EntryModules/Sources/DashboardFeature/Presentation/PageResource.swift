import Foundation
import Combine
import CoreKit

/// 每个区域独立首载/刷新/错误；刷新失败保留已有内容，版本号阻止迟到结果回写。
public struct ResourceState<Value> {
    public var value: Value?
    public var loading = false
    public var error: String?
}
@MainActor public final class PageResource<Value>: ObservableObject {
    @Published public private(set) var state = ResourceState<Value>()
    private var request: Task<Void, Never>?
    private var version = 0
    public init() {}
    public func load(_ operation: @escaping () async throws -> Value) {
        cancel(); let current = version; state.loading = true; state.error = nil
        request = Task { [weak self] in
            do {
                let value = try await operation()
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state = ResourceState(value: value, loading: false, error: nil)
            } catch {
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state.loading = false
                let code = (error as? AppFailure)?.code ?? "NETWORK_UNAVAILABLE"
                guard !(error is CancellationError), !["CANCELLED", "STALE_SESSION"].contains(code) else { return }
                self.state.error = "内容加载失败（\(code)）"
                AppLogger.log("dashboard.resource.failed", ["code": code], error: true)
            }
        }
    }
    public func cancel() { version += 1; request?.cancel(); request = nil; state.loading = false }
    public func clear() { cancel(); state = ResourceState() }
    public func accept(_ value: Value) { cancel(); state = ResourceState(value: value, loading: false, error: nil) }
    deinit { request?.cancel() }
}
/// 页面只在已认证、前台和当前Tab时活跃；后台暂停而不清空当前会话内容。
@MainActor public class RootPageViewModel: ObservableObject {
    public private(set) var active = false
    public private(set) var epoch = -1
    public func activate(epoch: Int) {
        let changed = self.epoch != epoch
        if changed { deactivate(); reset(); self.epoch = epoch }
        guard !active else { return }; active = true; refresh()
    }
    public func deactivate() { active = false; cancel() }
    public func invalidate() { deactivate(); reset(); epoch = -1 }
    public func refresh() {}
    func cancel() {}
    func reset() {}
}
