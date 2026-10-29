// 文件职责：单张认证表单的输入、提交状态和待消费提示。
import Foundation
import Combine
import CoreKit

/// 单张认证表单的输入、提交状态和待消费提示。
public struct AuthUiState {
    /// 当前表单账号输入，不通过日志输出。
    public var username = ""
    /// 仅当前表单内存持有的密码，离页或成功后清除。
    public var password = ""
    /// 正在提交，驱动按钮禁用与加载展示。
    public var isLoading = false
    /// Native会话认证标志，不等同某次Bridge命令ACK。
    public var authenticated = false
    /// 待消费的用户提示，不携带底层敏感异常。
    public var message: String?
    /// 为当前登录或注册页注入仓储，表单状态由本VM独立持有。
    public init() {}
}
/// 每个导航页独立持有VM；版本号隔离切页后的迟到结果，成功事实保留到前台观察。
@MainActor public final class AuthViewModel: ObservableObject {
    /// 当前可观察UI状态或模拟账本，不允许跨会话迟到写回。
    @Published public private(set) var state = AuthUiState()
    /// 通过契约注入的数据来源，VM不直接操作网络或数据库。
    private let repository: AuthRepository
    /// 当前是否为注册表单；登录与注册分别持有状态。
    private let registration: Bool
    /// 当前表单异步提交任务，离页或释放时取消。
    private var request: Task<Void, Never>?
    /// 请求代次，每次取消/替换递增以隔离旧回调。
    private var version = 0
    /// 为当前登录或注册页注入仓储，表单状态由本VM独立持有。
    public init(repository: AuthRepository, registration: Bool) { self.repository = repository; self.registration = registration }
    /// 在非提交、未认证状态更新当前表单账号。
    public func username(_ value: String) { guard !state.isLoading && !state.authenticated else { return }; state.username = value }
    /// 在非提交、未认证状态更新内存密码，不持久化。
    public func password(_ value: String) { guard !state.isLoading && !state.authenticated else { return }; state.password = value }
    /// 消费已显示的提示，防止前台恢复重复展示。
    public func consumeMessage() { state.message = nil }
    /// 递增版本并取消请求，清除密码、加载态及未展示提示。
    public func leave() { version += 1; request?.cancel(); request = nil; state.password = ""; state.isLoading = false; state.message = nil }
    /// 校验并防重提交；结果只允许写回当前活动生命周期。
    public func submit() {
        guard !state.isLoading && !state.authenticated else { return }
        guard !InputText.trim(state.username).isEmpty, !InputText.trim(state.password).isEmpty else { state.message = "账号或密码不能为空"; return }
        state.isLoading = true; state.message = nil; version += 1
        // 捕获本次输入与版本；用户切页后递增版本，旧任务不再更改状态。
        let current = version; let name = state.username; let password = state.password
        AppLogger.log("auth.requested", ["pageId": registration ? "register" : "login"])
        request = Task { [weak self, repository, registration] in
            do {
                _ = try await repository.authenticate(username: name, password: password, registration: registration)
                // 成功和失败都先核对版本与取消；取消不作为Toast错误展示。
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state.authenticated = true; self.state.password = ""; self.state.isLoading = false
            } catch {
                // 成功和失败都先核对版本与取消；取消不作为Toast错误展示。
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state.isLoading = false
                if error is CancellationError || ["CANCELLED", "STALE_SESSION"].contains((error as? AppFailure)?.code ?? "") { return }
                if let rejection = error as? AuthRejection { self.state.message = rejection.message }
                else if (error as? AppFailure)?.code == "TIMEOUT" { self.state.message = "网络请求超时，请稍后重试" }
                else { self.state.message = "网络连接失败，请稍后重试" }
                AppLogger.log("auth.failed", ["code": (error as? AppFailure)?.code ?? "AUTH_REJECTED"], error: true)
            }
        }
    }
    /// 释放所有者时取消仍在运行的任务，避免继续交付页面结果。
    deinit { request?.cancel() }
}
