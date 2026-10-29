// 文件职责：真实URLSession到Mock再到SQLite的集成回归。
import XCTest
import Foundation
import CoreKit
import NetworkKit
import StorageKit
import DemoEngine

/// 使用真实URLSession→URLProtocol→Mock→SQLite，防止仅测Fake而漏掉上下文丢失。
final class NetworkIntegrationTests: XCTestCase {
    /// 从测试源码位置定位仓库资源，只用于主机集成测试。
    private func resources() -> DirectoryResources {
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { root.deleteLastPathComponent() }
        return DirectoryResources(root: root)
    }
    /// 真实网络拦截链验证表单特殊字符、重复注册、登录及JS权限拒绝。
    func testRegistrationLoginAndNativeOnlyBoundary() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let database = LocalDatabase(directory: root)
        let adapter = Adapter(database)
        let handler = DemoHandler(resources: resources(), accounts: MockAccountStore(storage: adapter), state: adapter, delayMS: { 0 })
        let client = NetworkClient(resources: resources(), handler: handler); defer { client.close() }
        let guest = RequestContext(epoch: 0, scope: "guest")
        let form: JSONObject = ["username": " 本地&账号+😀 ", "password": " 密码+&=😀 "]
        let registered = try await client.execute(APIRequest(apiID: "auth.register", body: form, nativeOnly: true), context: guest)
        XCTAssertEqual(try JSON.object(registered.data)["code"] as? Int, 200)
        let duplicate = try await client.execute(APIRequest(apiID: "auth.register", body: form, nativeOnly: true), context: guest)
        XCTAssertEqual(try JSON.object(duplicate.data)["code"] as? Int, 409)
        let login = try await client.execute(APIRequest(apiID: "auth.login", body: form, nativeOnly: true), context: guest)
        XCTAssertEqual(try JSON.object(login.data)["code"] as? Int, 200)
        do { _ = try await client.execute(APIRequest(apiID: "auth.login", body: form), context: guest); XCTFail("JS must not invoke Native authentication API") }
        catch { XCTAssertEqual((error as? AppFailure)?.code, "ENDPOINT_NOT_ALLOWED") }
    }
    /// 验证先取消后登记不会复活，签到写入可被后续查询读取。
    func testCancelBeforeRegistrationAndMockWritePersistence() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let adapter = Adapter(LocalDatabase(directory: root))
        let handler = DemoHandler(resources: resources(), accounts: MockAccountStore(storage: adapter), state: adapter, delayMS: { 0 })
        let client = NetworkClient(resources: resources(), handler: handler); defer { client.close() }
        let user = RequestContext(epoch: 1, scope: "test-user")
        let canceled = APIRequest(apiID: "home.content")
        client.cancel(canceled.requestID)
        do { _ = try await client.execute(canceled, context: user); XCTFail("Canceled request must not execute") }
        catch { XCTAssertEqual((error as? AppFailure)?.code, "CANCELLED") }
        let before = try await client.execute(APIRequest(apiID: "checkin.status"), context: user)
        let status = try XCTUnwrap(try JSON.object(before.data)["data"] as? JSONObject)
        let date = try XCTUnwrap(status["businessDate"] as? String)
        let write = APIRequest(apiID: "checkin.submit", body: ["expectedBusinessDate": date, "idempotencyKey": UUID().uuidString])
        let result = try await client.execute(write, context: user)
        XCTAssertEqual(try JSON.object(result.data)["code"] as? Int, 200)
        let again = try await client.execute(APIRequest(apiID: "checkin.status"), context: user)
        XCTAssertEqual((try JSON.object(again.data)["data"] as? JSONObject)?["checkedToday"] as? Bool, true)
    }
    /// 网络集成测试的数据库适配器，不接触正式账号库。
    private final class Adapter: MockAccountStorage, DemoStatePort {
        /// 测试专属数据库引用，不指向用户账号目录。
        let db: LocalDatabase
        /// 保存当前用例创建的隔离数据库，不构建生产应用图。
        init(_ db: LocalDatabase) { self.db = db }
        /// 读取本用例数据库内的合成账号校验值。
        func read(_ key: String) throws -> String? { try db.credential(key) }
        /// 在本用例数据库中验证唯一账号创建。
        func create(key: String, verifier: String) throws -> Bool { try db.insertCredential(key: key, verifier: verifier) }
        /// 测试用事务适配，直接操作隔离数据库；不承担生产会话校验。
        func transaction(context: RequestContext, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any { try db.state(scope: context.scope, write: write, transform: transform) }
    }
}
