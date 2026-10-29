// 文件职责：验证持久化、认证取消、输入兼容及事务边界。
import XCTest
import Foundation
@testable import CoreKit
@testable import UserFeature
@testable import StorageKit
@testable import NetworkKit
@testable import DemoEngine

/// Fake Port和隔离数据库验证失败、事务与取消，不访问正式账号。
final class ArchitectureBehaviorTests: XCTestCase {
    /// 为用例创建唯一临时目录，结束后由用例自行清理。
    private func temporary() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("entrytask-test-" + UUID().uuidString) }
    /// 验证唯一账号不被覆盖，重开数据库仍能读取原校验值。
    func testUniqueInsertAndReopen() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let key = "mock_auth_v1." + String(repeating: "a", count: 64)
        let db = LocalDatabase(directory: root)
        XCTAssertTrue(try db.insertCredential(key: key, verifier: "original"))
        XCTAssertFalse(try db.insertCredential(key: key, verifier: "replacement"))
        XCTAssertEqual(try LocalDatabase(directory: root).credential(key), "original")
    }
    /// 注入转换失败，断言之前提交的账本没有被破坏。
    func testTransactionRollsBack() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let db = LocalDatabase(directory: root)
        _ = try db.state(scope: "test", write: true) { _ in (["energy": 30], true) }
        XCTAssertThrowsError(try db.state(scope: "test", write: true) { _ in throw AppFailure("INJECTED_FAILURE") })
        let value = try db.state(scope: "test", write: false) { state in (state, state["energy"] ?? 0) }
        XCTAssertEqual(value as? Int, 30)
    }
    /// 递增epoch后提交旧认证结果，确认旧结果不能恢复会话。
    func testSessionRejectsLateAuthentication() {
        let session = SessionStore(); let old = session.snapshot().epoch
        session.logout()
        XCTAssertFalse(session.commit(username: "synthetic", token: "demo", expectedEpoch: old))
        XCTAssertFalse(session.snapshot().authenticated)
        XCTAssertTrue(session.commit(username: "synthetic", token: "demo", expectedEpoch: session.snapshot().epoch))
        XCTAssertNotEqual(session.requestContext().scope, "guest")
    }
    /// 验证用户名裁剪、密码空格保留及重复注册拒绝。
    func testPasswordSpacesAndDuplicateRegistration() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let accounts = MockAccountStore(storage: Accounts(LocalDatabase(directory: root)))
        XCTAssertTrue(try accounts.register(username: " 教学账号 ", password: " 密码😀 "))
        XCTAssertFalse(try accounts.register(username: "教学账号", password: "replacement"))
        XCTAssertTrue(try accounts.verify(username: "教学账号", password: " 密码😀 "))
        XCTAssertFalse(try accounts.verify(username: "教学账号", password: "密码😀"))
    }
    /// 固定业务日验证七日日历、连续奖励与重复提交不重复加能量。
    func testCalendarRewardAndDuplicate() throws {
        var state: JSONObject = ["dates": ["2026-10-25", "2026-10-26"], "energy": 0]
        let ledger = CheckinLedger(today: "2026-10-27", template: [:])
        let first = try ledger.submit(&state, expectedDate: "2026-10-27")
        XCTAssertEqual(first["awardedEnergy"] as? Int, 30)
        XCTAssertEqual((first["calendar"] as? [JSONObject])?.count, 7)
        XCTAssertEqual(try ledger.submit(&state, expectedDate: "2026-10-27")["awardedEnergy"] as? Int, 0)
        XCTAssertEqual(state["energy"] as? Int, 30)
        XCTAssertThrowsError(try ledger.submit(&state, expectedDate: "2026-10-26"))
    }
    /// 验证登录注册输入隔离，切页取消后密码清除且无迟到提示。
    @MainActor func testFormIsolationAndCancel() async throws {
        let repository = WaitingAuth()
        let login = AuthViewModel(repository: repository, registration: false)
        let register = AuthViewModel(repository: repository, registration: true)
        login.username("synthetic"); login.password("example"); login.submit(); login.submit()
        await Task.yield(); login.leave()
        try await Task.sleep(nanoseconds: 20000000)
        XCTAssertEqual(register.state.username, "")
        XCTAssertEqual(register.state.password, "")
        XCTAssertEqual(login.state.password, "")
        XCTAssertNil(login.state.message)
        XCTAssertFalse(login.state.isLoading)
        XCTAssertLessThanOrEqual(repository.calls, 1)
    }
    /// 拒绝凭证键和数值伪布尔，再验证读写删除。
    func testTypedPreferencesRejectCredentialKeysAndNumericBoolean() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let preferences = TypedPreferences(directory: root)
        let scope = String(repeating: "a", count: 64)
        XCTAssertThrowsError(try preferences.perform("write", key: "token", scope: scope, value: true))
        XCTAssertThrowsError(try preferences.perform("write", key: "assetVisibility", scope: scope, value: 1))
        XCTAssertTrue(try preferences.perform("write", key: "assetVisibility", scope: scope, value: true))
        XCTAssertTrue(try preferences.perform("read", key: "assetVisibility", scope: scope, value: nil))
        XCTAssertTrue(try preferences.perform("remove", key: "assetVisibility", scope: scope, value: nil))
        XCTAssertFalse(try preferences.perform("read", key: "assetVisibility", scope: scope, value: nil))
    }
    /// 用固定字符和派生向量核对Android兼容行为。
    func testWhitespaceAndKDFCompatibilityVector() throws {
        XCTAssertEqual(InputText.trim("\u{001C} 账号\u{00A0}"), "账号")
        XCTAssertEqual(InputText.trim("\u{0085}账号\u{0085}"), "\u{0085}账号\u{0085}")
        let derived = try MockAccountStore.derive(password: " 密码😀 ", salt: Array(UInt8(0)...UInt8(15)))
        XCTAssertEqual(derived.map { String(format: "%02x", $0) }.joined(), "64009ed8f2c08e13386caf8efc4b4557a0024ce16bfce1fa773804d8d63e1a0a")
    }
    /// 阻塞提交队列后取消认证，确认释放队列后也不会提交会话。
    @MainActor func testCanceledQueuedAuthenticationCannotCommitSession() async throws {
        let commits = CommitExecutor(); let session = SessionStore()
        let queueEntered = expectation(description: "commit queue occupied")
        let responseReady = expectation(description: "network result returned")
        let release = DispatchSemaphore(value: 0)
        DispatchQueue.global().async { commits.sync { queueEntered.fulfill(); release.wait() } }
        await fulfillment(of: [queueEntered], timeout: 2)
        let repository = NetworkAuthRepository(network: ImmediateNetwork(responseReady), session: session, commits: commits)
        let request = Task { try await repository.authenticate(username: "synthetic", password: "synthetic", registration: false) }
        await fulfillment(of: [responseReady], timeout: 2)
        await Task.yield(); request.cancel(); release.signal()
        _ = try? await request.value
        XCTAssertFalse(session.snapshot().authenticated)
    }
    /// 构造排队取消，断言写入闭包从未执行。
    @MainActor func testCanceledQueuedCommitDoesNotStartMutation() async throws {
        let commits = CommitExecutor()
        let entered = expectation(description: "queue occupied")
        let release = DispatchSemaphore(value: 0)
        DispatchQueue.global().async { commits.sync { entered.fulfill(); release.wait() } }
        await fulfillment(of: [entered], timeout: 2)
        let mutation = Task { try await commits.perform { XCTFail("Canceled work must not mutate storage"); return true } }
        await Task.yield(); mutation.cancel(); release.signal()
        do { _ = try await mutation.value; XCTFail("Expected cancellation") }
        catch { XCTAssertTrue(error is CancellationError) }
    }
    /// 立即返回安全合成认证结果，构造提交队列取消竞态。
    private final class ImmediateNetwork: NetworkPort {
        /// 测试同步信号，用于精确构造提交前取消。
        let ready: XCTestExpectation
        /// 保存网络返回的同步信号，用于测试排队取消。
        init(_ ready: XCTestExpectation) { self.ready = ready }
        /// 立即发出同步信号并返回合成认证结果，用于复现排队取消。
        func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply {
            ready.fulfill()
            return HTTPReply(status: 200, data: try JSON.data(["code": 200, "data": ["id": 1, "username": "synthetic", "token": "demo-only"]]))
        }
        /// 此替身无在途网络任务；取消边界由测试中的提交器验证。
        func cancel(_ requestID: String) {}
    }
    /// 测试账号存储适配器，指向用例自己的临时数据库。
    private final class Accounts: MockAccountStorage {
        /// 测试专属数据库引用，不指向用户账号目录。
        let db: LocalDatabase
        /// 注入已有数据库门面，不在适配器中创建另一份账号库。
        init(_ db: LocalDatabase) { self.db = db }
        /// 按哈希账号键读取密码校验记录，未注册时返回空。
        func read(_ key: String) throws -> String? { try db.credential(key) }
        /// 唯一插入账号校验值，已有记录不覆盖。
        func create(key: String, verifier: String) throws -> Bool { try db.insertCredential(key: key, verifier: verifier) }
    }
    /// 延迟认证替身，用于验证取消与重复提交。
    private final class WaitingAuth: AuthRepository {
        /// 在途请求索引或测试调用次数，避免重复提交/漏清理。
        var calls = 0
        /// 经认证契约发起登录或注册；网络和会话提交由实现负责。
        func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser {
            calls += 1; try await Task.sleep(nanoseconds: 1000000000)
            return AuthenticatedUser(id: 1, username: username)
        }
    }
}
