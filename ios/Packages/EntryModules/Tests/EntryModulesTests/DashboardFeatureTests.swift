// 文件职责：验证模型解码、旧结果隔离、签到和会话生命周期。
import XCTest
import Foundation
import Combine
import CoreKit
import DashboardFeature

/// 真实契约样本用于映射测试；运行时页面不读fixture。竞态使用可控制的挂起点。
final class DashboardFeatureTests: XCTestCase {
    /// 从共享fixture提取data，验证模型与真实契约样本对齐。
    private func sample<T: Decodable>(_ path: String, _ type: T.Type = T.self) throws -> T {
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { root.deleteLastPathComponent() }
        let envelope = try JSON.object(Data(contentsOf: root.appendingPathComponent("mock/" + path)))
        return try JSONDecoder().decode(T.self, from: JSON.data(XCTUnwrap(envelope["data"])))
    }
    /// 用共享样本验证模型、空账单和大金额十进制精度。
    func testModelsDecodeSharedContractsAndMoneyIsDecimal() throws {
        let home: HomeContent = try sample("home/content/response.success.json")
        XCTAssertEqual(home.quickServices.count, 4)
        XCTAssertTrue(home.sections.contains { $0.id == "research" })
        let profile: FinancialOverview = try sample("profile/financialOverview/response.success.json")
        XCTAssertNil(profile.incomeShare); XCTAssertFalse(profile.creditBill.hasCard)
        XCTAssertTrue(profile.loanOffer.marketingOnly); XCTAssertEqual(profile.loanOffer.rateText, "3%")
        let money = try JSONDecoder().decode(Money.self, from: Data(#"{"amountMinor":"123456789012345678","minorUnit":2,"currency":"CNY"}"#.utf8))
        XCTAssertEqual(money.decimal, Decimal(string: "1234567890123456.78"))
    }
    /// 手动恢复旧请求，确认其不能覆盖已接受的新数据。
    @MainActor func testCanceledResourceCannotOverwriteNewValue() async throws {
        let resource = PageResource<Int>()
        let started = expectation(description: "first load started")
        let completed = expectation(description: "late operation finished")
        var continuation: CheckedContinuation<Int, Never>?
        resource.load {
            let value = await withCheckedContinuation { continuation = $0; started.fulfill() }
            completed.fulfill(); return value
        }
        await fulfillment(of: [started], timeout: 2)
        resource.clear(); resource.accept(2); continuation?.resume(returning: 1)
        await fulfillment(of: [completed], timeout: 2)
        XCTAssertEqual(resource.state.value, 2); XCTAssertNil(resource.state.error)
    }
    /// 验证只有当前Tab活跃，隐私跨页同步且换会话复位。
    @MainActor func testCoordinatorPausesInactiveTabsAndResetsSessionPrivacy() async throws {
        let repository = FakeRepositories()
        let coordinator = DashboardCoordinator(repositories: repository)
        coordinator.update(tab: 1, epoch: 1, authenticated: true, foreground: true)
        XCTAssertTrue(coordinator.home.active); XCTAssertFalse(coordinator.wealth.active)
        coordinator.togglePrivacy(); XCTAssertTrue(coordinator.assetsVisible)
        coordinator.update(tab: 2, epoch: 1, authenticated: true, foreground: true)
        XCTAssertFalse(coordinator.home.active); XCTAssertTrue(coordinator.wealth.active)
        XCTAssertTrue(coordinator.assetsVisible)
        coordinator.update(tab: 2, epoch: 2, authenticated: false, foreground: true)
        XCTAssertFalse(coordinator.assetsVisible); XCTAssertFalse(coordinator.wealth.active)
        XCTAssertNil(coordinator.home.content.state.value)
        coordinator.suspend()
    }
    /// 重复点击只产生一次写，离页后迟到失败不污染提示。
    @MainActor func testCheckinIsSingleFlightAndLateWriteCannotUpdateInactivePage() async throws {
        let repository = FakeRepositories()
        let model = MemberPageViewModel(repository: repository)
        model.activate(epoch: 1)
        let status: CheckinStatus = try sample("member/checkin/response.success.json")
        model.checkin.accept(status)
        let started = expectation(description: "write submitted")
        repository.writeStarted = started
        model.submit(); model.submit()
        await fulfillment(of: [started], timeout: 2)
        XCTAssertEqual(repository.writes, 1)
        model.deactivate()
        repository.write?.resume(throwing: AppFailure("TIMEOUT"))
        await Task.yield()
        XCTAssertFalse(model.submitting); XCTAssertEqual(model.message, "")
    }
    /// 未知写结果跨日重试，断言使用重新查询的业务日期。
    @MainActor func testUnknownCheckinUsesConfirmedBusinessDateAfterMidnight() async throws {
        let repository = FakeRepositories()
        let model = MemberPageViewModel(repository: repository)
        let old: CheckinStatus = try sample("member/checkin/response.success.json")
        let confirmed = try JSONDecoder().decode(CheckinStatus.self, from: Data(#"{"businessDate":"2030-01-02","checkedToday":false,"consecutiveDays":0,"energy":0,"awardedEnergy":0,"alreadyChecked":false,"calendar":[],"rewardRule":"test"}"#.utf8))
        model.activate(epoch: 1); model.checkin.accept(old)
        let first = expectation(description: "first write")
        repository.writeStarted = first; model.submit()
        await fulfillment(of: [first], timeout: 2)
        model.deactivate(); repository.write?.resume(throwing: AppFailure("TIMEOUT"))
        await Task.yield()
        repository.checkinValue = confirmed
        model.activate(epoch: 1); model.checkin.accept(old)
        let retry = expectation(description: "confirmed write")
        repository.writeStarted = retry; model.submit()
        await fulfillment(of: [retry], timeout: 2)
        XCTAssertEqual(repository.dates, [old.businessDate, confirmed.businessDate])
        repository.write?.resume(returning: confirmed)
        model.deactivate()
    }
    /// 未登录时仓储直接拒绝，网络调用次数必须为零。
    func testRepositoryRejectsGuestBeforeIO() async throws {
        let network = TestNetwork()
        let repo = NetworkDashboardRepository(network: network, context: { RequestContext(epoch: 0, scope: "guest") })
        do { _ = try await repo.home(); XCTFail("Guest must not execute private pages") }
        catch { XCTAssertEqual((error as? AppFailure)?.code, "AUTH_REQUIRED") }
        XCTAssertEqual(network.calls, 0)
    }
    /// 记录网络调用次数并返回受控失败的测试替身。
    private final class TestNetwork: NetworkPort {
        /// 在途请求索引或测试调用次数，避免重复提交/漏清理。
        var calls = 0
        /// 记录调用次数并抛出受控失败，验证guest门禁发生在IO之前。
        func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply { calls += 1; throw AppFailure("TEST_UNAVAILABLE") }
        /// 此替身不创建实际网络任务，无需执行传输取消。
        func cancel(_ requestID: String) {}
    }
    /// 可控制签到挂起点的四域仓储替身。
    private final class FakeRepositories: DashboardRepositorySet {
        /// 测试记录的签到写次数，用于断言单飞防重。
        var writes = 0
        /// 测试记录的提交业务日期，用于验证跨日确认。
        var dates: [String] = []
        /// 测试指定的签到查询事实。
        var checkinValue: CheckinStatus?
        /// 测试等待签到写任务开始的同步信号。
        var writeStarted: XCTestExpectation?
        /// 测试可控的签到挂起点，由用例明确恢复。
        var write: CheckedContinuation<CheckinStatus, Error>?
        /// 提供受控查询失败，让生命周期测试不依赖真实网络。
        func home() async throws -> HomeContent { throw AppFailure("TEST_UNAVAILABLE") }
        /// 财富查询替身返回受控失败，不访问Mock或磁盘。
        func wealth() async throws -> WealthContent { throw AppFailure("TEST_UNAVAILABLE") }
        /// 会员内容替身返回受控失败，仅让用例控制签到路径。
        func member() async throws -> MemberContent { throw AppFailure("TEST_UNAVAILABLE") }
        /// 返回固定零未读数，排除消息请求对生命周期断言的影响。
        func unread() async throws -> Int { 0 }
        /// 产品查询不参与当前竞态测试，返回受控失败。
        func products(category: String) async throws -> ProductPage { throw AppFailure("TEST_UNAVAILABLE") }
        /// 资产查询替身返回受控失败，不产生真实IO。
        func assets() async throws -> AssetOverview { throw AppFailure("TEST_UNAVAILABLE") }
        /// 资料查询不参与签到竞态，返回受控失败。
        func identity(member: Bool) async throws -> Identity { throw AppFailure("TEST_UNAVAILABLE") }
        /// 收支查询替身返回受控失败，不读取用户账本。
        func financial() async throws -> FinancialOverview { throw AppFailure("TEST_UNAVAILABLE") }
        /// 返回用例指定的签到事实；未配置时抛受控失败。
        func checkin() async throws -> CheckinStatus {
            if let checkinValue { return checkinValue }; throw AppFailure("TEST_UNAVAILABLE")
        }
        /// 记录写次数与业务日期，挂起到用例手动恢复以精确构造竞态。
        func submit(date: String, key: String) async throws -> CheckinStatus {
            writes += 1; dates.append(date)
            return try await withCheckedThrowingContinuation { write = $0; writeStarted?.fulfill() }
        }
    }
}
