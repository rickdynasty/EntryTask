import Foundation
import Combine
import CoreKit

public struct AuthUiState {
    public var username = ""
    public var password = ""
    public var isLoading = false
    public var authenticated = false
    public var message: String?
    public init() {}
}
/// 每个导航页独立持有VM；版本号隔离切页后的迟到结果，成功事实保留到前台观察。
@MainActor public final class AuthViewModel: ObservableObject {
    @Published public private(set) var state = AuthUiState()
    private let repository: AuthRepository
    private let registration: Bool
    private var request: Task<Void, Never>?
    private var version = 0
    public init(repository: AuthRepository, registration: Bool) { self.repository = repository; self.registration = registration }
    public func username(_ value: String) { guard !state.isLoading && !state.authenticated else { return }; state.username = value }
    public func password(_ value: String) { guard !state.isLoading && !state.authenticated else { return }; state.password = value }
    public func consumeMessage() { state.message = nil }
    public func leave() { version += 1; request?.cancel(); request = nil; state.password = ""; state.isLoading = false; state.message = nil }
    public func submit() {
        guard !state.isLoading && !state.authenticated else { return }
        guard !InputText.trim(state.username).isEmpty, !InputText.trim(state.password).isEmpty else { state.message = "账号或密码不能为空"; return }
        state.isLoading = true; state.message = nil; version += 1
        let current = version; let name = state.username; let password = state.password
        AppLogger.log("auth.requested", ["pageId": registration ? "register" : "login"])
        request = Task { [weak self, repository, registration] in
            do {
                _ = try await repository.authenticate(username: name, password: password, registration: registration)
                guard let self, self.version == current, !Task.isCancelled else { return }
                self.state.authenticated = true; self.state.password = ""; self.state.isLoading = false
            } catch {
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
    deinit { request?.cancel() }
}
