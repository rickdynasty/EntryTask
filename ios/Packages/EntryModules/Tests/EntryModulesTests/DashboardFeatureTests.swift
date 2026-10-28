import XCTest
import Foundation
import Combine
import CoreKit
import DashboardFeature

/// 真实契约样本用于映射测试；运行时页面不读fixture。竞态使用可控制的挂起点。
final class DashboardFeatureTests: XCTestCase {
    private func sample<T: Decodable>(_ path: String, _ type: T.Type = T.self) throws -> T {
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { root.deleteLastPathComponent() }
        let envelope = try JSON.object(Data(contentsOf: root.appendingPathComponent("mock/" + path)))
        return try JSONDecoder().decode(T.self, from: JSON.data(XCTUnwrap(envelope["data"])))
    }
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
    func testRepositoryRejectsGuestBeforeIO() async throws {
        let network = TestNetwork()
        let repo = NetworkDashboardRepository(network: network, context: { RequestContext(epoch: 0, scope: "guest") })
        do { _ = try await repo.home(); XCTFail("Guest must not execute private pages") }
        catch { XCTAssertEqual((error as? AppFailure)?.code, "AUTH_REQUIRED") }
        XCTAssertEqual(network.calls, 0)
    }
    private final class TestNetwork: NetworkPort {
        var calls = 0
        func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply { calls += 1; throw AppFailure("TEST_UNAVAILABLE") }
        func cancel(_ requestID: String) {}
    }
    private final class FakeRepositories: DashboardRepositorySet {
        var writes = 0
        var dates: [String] = []
        var checkinValue: CheckinStatus?
        var writeStarted: XCTestExpectation?
        var write: CheckedContinuation<CheckinStatus, Error>?
        func home() async throws -> HomeContent { throw AppFailure("TEST_UNAVAILABLE") }
        func wealth() async throws -> WealthContent { throw AppFailure("TEST_UNAVAILABLE") }
        func member() async throws -> MemberContent { throw AppFailure("TEST_UNAVAILABLE") }
        func unread() async throws -> Int { 0 }
        func products(category: String) async throws -> ProductPage { throw AppFailure("TEST_UNAVAILABLE") }
        func assets() async throws -> AssetOverview { throw AppFailure("TEST_UNAVAILABLE") }
        func identity(member: Bool) async throws -> Identity { throw AppFailure("TEST_UNAVAILABLE") }
        func financial() async throws -> FinancialOverview { throw AppFailure("TEST_UNAVAILABLE") }
        func checkin() async throws -> CheckinStatus {
            if let checkinValue { return checkinValue }; throw AppFailure("TEST_UNAVAILABLE")
        }
        func submit(date: String, key: String) async throws -> CheckinStatus {
            writes += 1; dates.append(date)
            return try await withCheckedThrowingContinuation { write = $0; writeStarted?.fulfill() }
        }
    }
}
