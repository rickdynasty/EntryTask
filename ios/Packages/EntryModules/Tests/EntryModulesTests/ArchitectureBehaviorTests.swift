import XCTest
import Foundation
@testable import CoreKit
@testable import UserFeature
@testable import StorageKit
@testable import NetworkKit
@testable import DemoEngine

/// Fake Port和隔离数据库验证失败、事务与取消，不访问正式账号。
final class ArchitectureBehaviorTests: XCTestCase {
    private func temporary() -> URL { FileManager.default.temporaryDirectory.appendingPathComponent("entrytask-test-" + UUID().uuidString) }
    func testUniqueInsertAndReopen() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let key = "mock_auth_v1." + String(repeating: "a", count: 64)
        let db = LocalDatabase(directory: root)
        XCTAssertTrue(try db.insertCredential(key: key, verifier: "original"))
        XCTAssertFalse(try db.insertCredential(key: key, verifier: "replacement"))
        XCTAssertEqual(try LocalDatabase(directory: root).credential(key), "original")
    }
    func testTransactionRollsBack() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let db = LocalDatabase(directory: root)
        _ = try db.state(scope: "test", write: true) { _ in (["energy": 30], true) }
        XCTAssertThrowsError(try db.state(scope: "test", write: true) { _ in throw AppFailure("INJECTED_FAILURE") })
        let value = try db.state(scope: "test", write: false) { state in (state, state["energy"] ?? 0) }
        XCTAssertEqual(value as? Int, 30)
    }
    func testSessionRejectsLateAuthentication() {
        let session = SessionStore(); let old = session.snapshot().epoch
        session.logout()
        XCTAssertFalse(session.commit(username: "synthetic", token: "demo", expectedEpoch: old))
        XCTAssertFalse(session.snapshot().authenticated)
        XCTAssertTrue(session.commit(username: "synthetic", token: "demo", expectedEpoch: session.snapshot().epoch))
        XCTAssertNotEqual(session.requestContext().scope, "guest")
    }
    func testPasswordSpacesAndDuplicateRegistration() throws {
        let root = temporary(); defer { try? FileManager.default.removeItem(at: root) }
        let accounts = MockAccountStore(storage: Accounts(LocalDatabase(directory: root)))
        XCTAssertTrue(try accounts.register(username: " 教学账号 ", password: " 密码😀 "))
        XCTAssertFalse(try accounts.register(username: "教学账号", password: "replacement"))
        XCTAssertTrue(try accounts.verify(username: "教学账号", password: " 密码😀 "))
        XCTAssertFalse(try accounts.verify(username: "教学账号", password: "密码😀"))
    }
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
    func testWhitespaceAndKDFCompatibilityVector() throws {
        XCTAssertEqual(InputText.trim("\u{001C} 账号\u{00A0}"), "账号")
        XCTAssertEqual(InputText.trim("\u{0085}账号\u{0085}"), "\u{0085}账号\u{0085}")
        let derived = try MockAccountStore.derive(password: " 密码😀 ", salt: Array(UInt8(0)...UInt8(15)))
        XCTAssertEqual(derived.map { String(format: "%02x", $0) }.joined(), "64009ed8f2c08e13386caf8efc4b4557a0024ce16bfce1fa773804d8d63e1a0a")
    }
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
    private final class ImmediateNetwork: NetworkPort {
        let ready: XCTestExpectation
        init(_ ready: XCTestExpectation) { self.ready = ready }
        func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply {
            ready.fulfill()
            return HTTPReply(status: 200, data: try JSON.data(["code": 200, "data": ["id": 1, "username": "synthetic", "token": "demo-only"]]))
        }
        func cancel(_ requestID: String) {}
    }
    private final class Accounts: MockAccountStorage {
        let db: LocalDatabase
        init(_ db: LocalDatabase) { self.db = db }
        func read(_ key: String) throws -> String? { try db.credential(key) }
        func create(key: String, verifier: String) throws -> Bool { try db.insertCredential(key: key, verifier: verifier) }
    }
    private final class WaitingAuth: AuthRepository {
        var calls = 0
        func authenticate(username: String, password: String, registration: Bool) async throws -> AuthenticatedUser {
            calls += 1; try await Task.sleep(nanoseconds: 1000000000)
            return AuthenticatedUser(id: 1, username: username)
        }
    }
}
