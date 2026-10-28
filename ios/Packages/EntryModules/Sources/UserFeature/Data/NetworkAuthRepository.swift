import Foundation
import CoreKit

/// 只请求并提交会话；账号持久化是Mock服务端职责，Repository不直接写库。
public final class NetworkAuthRepository: AuthRepository {
    private let network: NetworkPort
    private let session: SessionPort
    private let commits: CommitExecutor
    public init(network: NetworkPort, session: SessionPort, commits: CommitExecutor) { self.network = network; self.session = session; self.commits = commits }
    public func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser {
        let cancellation = CancellationState()
        return try await withTaskCancellationHandler(operation: {
            try await request(username: username, password: password, registration: registration, cancellation: cancellation)
        }, onCancel: { cancellation.cancel() })
    }
    private func request(username: String, password: String, registration: Bool, cancellation: CancellationState) async throws -> AuthenticatedUser {
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
        let accepted = try await commits.perform {
            try cancellation.check()
            return self.session.commit(username: name, token: token, expectedEpoch: context.epoch)
        }
        guard accepted else { throw AppFailure("STALE_SESSION") }
        AppLogger.log("auth.session.committed", ["epoch": session.snapshot().epoch])
        return AuthenticatedUser(id: user["id"] as? Int ?? 0, username: name)
    }
}
