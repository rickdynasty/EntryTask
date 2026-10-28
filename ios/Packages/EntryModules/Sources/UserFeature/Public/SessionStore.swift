import Foundation
import CryptoKit
import CoreKit

public struct SessionSnapshot: Equatable, Sendable {
    public let authenticated: Bool
    public let epoch: Int
    public init(authenticated: Bool, epoch: Int) { self.authenticated = authenticated; self.epoch = epoch }
}
/// App的VM/Bridge只依赖公开会话Port，基础技术库通过App适配器获取上下文。
public protocol SessionPort: AnyObject, Sendable {
    func snapshot() -> SessionSnapshot
    func requestContext() -> RequestContext
    @discardableResult func commit(username: String, token: String, expectedEpoch: Int) -> Bool
    func logout()
}
/// Native会话权威。锁只保护短内存操作；提交顺序由App注入的CommitExecutor协调。
public final class SessionStore: SessionPort, @unchecked Sendable {
    private let lock = NSLock()
    private var epoch = 0
    private var credential: String?
    private var scope: String?
    public init() {}
    public func snapshot() -> SessionSnapshot { lock.lock(); defer { lock.unlock() }; return SessionSnapshot(authenticated: credential != nil, epoch: epoch) }
    public func requestContext() -> RequestContext { lock.lock(); defer { lock.unlock() }; return RequestContext(epoch: epoch, scope: scope ?? "guest") }
    @discardableResult public func commit(username: String, token: String, expectedEpoch: Int) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard epoch == expectedEpoch, !username.isEmpty, !token.isEmpty else { return false }
        credential = token; scope = SHA256.hash(data: Data(username.utf8)).map { String(format: "%02x", $0) }.joined(); epoch += 1
        return true
    }
    public func logout() { lock.lock(); defer { lock.unlock() }; credential = nil; scope = nil; epoch += 1 }
}
public struct AuthenticatedUser: Sendable {
    public let id: Int; public let username: String
    public init(id: Int, username: String) { self.id = id; self.username = username }
}
public protocol AuthRepository {
    func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser
}
public struct AuthRejection: Error { public let message: String }
