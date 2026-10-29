// 文件职责：可公开的认证标志与代次，不包含Token或用户名。
import Foundation
import CryptoKit
import CoreKit

/// 可公开的认证标志与代次，不包含Token或用户名。
public struct SessionSnapshot: Equatable, Sendable {
    /// Native会话认证标志，不等同某次Bridge命令ACK。
    public let authenticated: Bool
    /// 会话代次；退出和认证提交后变化，旧结果不得跨代次回写。
    public let epoch: Int
    /// 保存调用方传入的值或依赖，不在构造期间发起业务请求。
    public init(authenticated: Bool, epoch: Int) { self.authenticated = authenticated; self.epoch = epoch }
}
/// App的VM/Bridge只依赖公开会话Port，基础技术库通过App适配器获取上下文。
public protocol SessionPort: AnyObject, Sendable {
    /// 返回当前事实快照，不暴露可修改的内部状态。
    func snapshot() -> SessionSnapshot
    /// 返回当前epoch和scope的原子快照，供请求捕获身份。
    func requestContext() -> RequestContext
    /// 核对预期epoch后提交内存凭证与隔离scope，成功时递增代次。
    @discardableResult func commit(username: String, token: String, expectedEpoch: Int) -> Bool
    /// 串行清除Native会话并通知UI，旧请求不能跨epoch回写。
    func logout()
}
/// Native会话权威。锁只保护短内存操作；提交顺序由App注入的CommitExecutor协调。
public final class SessionStore: SessionPort, @unchecked Sendable {
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 会话代次；退出和认证提交后变化，旧结果不得跨代次回写。
    private var epoch = 0
    /// 仅Native内存持有的凭证，退出时清除。
    private var credential: String?
    /// Native生成的用户隔离范围，未认证时为guest。
    private var scope: String?
    /// 创建默认初始状态，不执行网络或磁盘操作。
    public init() {}
    /// 返回当前事实快照，不暴露可修改的内部状态。
    public func snapshot() -> SessionSnapshot { lock.lock(); defer { lock.unlock() }; return SessionSnapshot(authenticated: credential != nil, epoch: epoch) }
    /// 返回当前epoch和scope的原子快照，供请求捕获身份。
    public func requestContext() -> RequestContext { lock.lock(); defer { lock.unlock() }; return RequestContext(epoch: epoch, scope: scope ?? "guest") }
    /// 核对预期epoch后提交内存凭证与隔离scope，成功时递增代次。
    @discardableResult public func commit(username: String, token: String, expectedEpoch: Int) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard epoch == expectedEpoch, !username.isEmpty, !token.isEmpty else { return false }
        credential = token; scope = SHA256.hash(data: Data(username.utf8)).map { String(format: "%02x", $0) }.joined(); epoch += 1
        return true
    }
    /// 串行清除Native会话并通知UI，旧请求不能跨epoch回写。
    public func logout() { lock.lock(); defer { lock.unlock() }; credential = nil; scope = nil; epoch += 1 }
}
/// 认证成功的业务返回值，不向调用方回传Token。
public struct AuthenticatedUser: Sendable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// username：当前表单账号输入，不通过日志输出。
    public let id: Int; public let username: String
    /// 构造认证成功的非凭证业务返回值。
    public init(id: Int, username: String) { self.id = id; self.username = username }
}
/// 登录注册业务契约，隐藏网络与会话提交实现。
public protocol AuthRepository {
    /// 经认证契约发起登录或注册；网络和会话提交由实现负责。
    func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser
}
/// 可展示的业务拒绝提示，与技术错误码区分。
public struct AuthRejection: Error { public let message: String }
