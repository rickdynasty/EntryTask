// 文件职责：通过网络认证后提交Native会话，不直接创建账号记录。
import Foundation
import CoreKit

/// 只请求并提交会话；账号持久化是Mock服务端职责，Repository不直接写库。
public final class NetworkAuthRepository: AuthRepository {
    /// 通过契约注入的网络能力，不让View直接访问传输层。
    private let network: NetworkPort
    /// Native会话权威的引用，身份不由页面自行指定。
    private let session: SessionPort
    /// 会话与写事务共用的串行提交协调器。
    private let commits: CommitExecutor
    /// 注入网络、会话与同一提交协调器，保证认证提交顺序。
    public init(network: NetworkPort, session: SessionPort, commits: CommitExecutor) { self.network = network; self.session = session; self.commits = commits }
    /// 经认证契约发起登录或注册；网络和会话提交由实现负责。
    public func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser {
        let cancellation = CancellationState()
        return try await withTaskCancellationHandler(operation: {
            try await request(username: username, password: password, registration: registration, cancellation: cancellation)
        }, onCancel: { cancellation.cancel() })
    }
    /// 捕获epoch发起仅Native认证API，校验结果后进入串行会话提交。
    private func request(username: String, password: String, registration: Bool, cancellation: CancellationState) async throws -> AuthenticatedUser {
        // 发起时捕获epoch，网络结束后不能将旧账号结果提交给新会话。
        let context = session.requestContext()
        let api = registration ? "auth.register" : "auth.login"
        let reply = try await network.execute(APIRequest(apiID: api, body: ["username": username, "password": password], nativeOnly: true), context: context)
        try Task.checkCancellation()
        let value = try JSON.object(reply.data)
        let code = value["code"] as? Int ?? 500
        guard code == 200 else {
            let message = registration ? (code == 409 ? "该用户名已注册，请更换用户名或直接登录" : "注册失败，请稍后重试") : ([400, 401].contains(code) ? "账号或密码错误，请检查后重试" : "登录失败，请稍后重试")
            throw AuthRejection(message: message)
        }
        guard let user = value["data"] as? JSONObject, let name = user["username"] as? String, !name.isEmpty, let token = user["token"] as? String, !token.isEmpty else { throw AppFailure("AUTH_RESPONSE_INVALID") }
        try Task.checkCancellation()
        // KDF/网络已完成，短会话提交进入共同串行队列；排队期间取消仍会阻止提交。
        let accepted = try await commits.perform {
            try cancellation.check()
            return self.session.commit(username: name, token: token, expectedEpoch: context.epoch)
        }
        guard accepted else { throw AppFailure("STALE_SESSION") }
        AppLogger.log("auth.session.committed", ["epoch": session.snapshot().epoch])
        return AuthenticatedUser(id: user["id"] as? Int ?? 0, username: name)
    }
}
