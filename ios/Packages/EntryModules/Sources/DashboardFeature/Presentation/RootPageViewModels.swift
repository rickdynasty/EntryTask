import Foundation
import Combine
import CoreKit

@MainActor public final class HomePageViewModel: RootPageViewModel {
    public let content = PageResource<HomeContent>(); public let unread = PageResource<Int>(); public let products = PageResource<ProductPage>()
    @Published public private(set) var category = "all"
    private let repository: HomeRepository
    public init(repository: HomeRepository) { self.repository = repository; super.init() }
    public func select(_ id: String) {
        guard active, category != id else { return }; category = id; products.clear(); loadProducts()
    }
    private func loadProducts() { let id = category; products.load { [repository] in try await repository.products(category: id) } }
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.home() }; unread.load { [repository] in try await repository.unread() }; loadProducts()
    }
    override func cancel() { content.cancel(); unread.cancel(); products.cancel() }
    override func reset() { category = "all"; content.clear(); unread.clear(); products.clear() }
}
@MainActor public final class WealthPageViewModel: RootPageViewModel {
    public let content = PageResource<WealthContent>(); public let assets = PageResource<AssetOverview>(); public let products = PageResource<ProductPage>()
    @Published public private(set) var category = "fund"
    private let repository: WealthRepository
    public init(repository: WealthRepository) { self.repository = repository; super.init() }
    public func select(_ id: String) { guard active, category != id else { return }; category = id; products.clear(); loadProducts() }
    private func loadProducts() { let id = category; products.load { [repository] in try await repository.products(category: id) } }
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.wealth() }; assets.load { [repository] in try await repository.assets() }; loadProducts()
    }
    override func cancel() { content.cancel(); assets.cancel(); products.cancel() }
    override func reset() { category = "fund"; content.clear(); assets.clear(); products.clear() }
}
/// 写结果不确定时先读取服务端事实，不自动重放；切Tab/退出期间禁止迟到更新和重复提交。
@MainActor public final class MemberPageViewModel: RootPageViewModel {
    public let content = PageResource<MemberContent>(); public let identity = PageResource<Identity>(); public let checkin = PageResource<CheckinStatus>()
    @Published public private(set) var submitting = false
    @Published public private(set) var message = ""
    private let repository: MemberRepository
    private var writing: Task<Void, Never>?
    private var writeVersion = 0
    private var needsConfirmation = false
    public init(repository: MemberRepository) { self.repository = repository; super.init() }
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.member() }; identity.load { [repository] in try await repository.identity(member: true) }
        if !submitting { checkin.load { [repository] in try await repository.checkin() } }
    }
    public func submit() {
        guard active, !submitting, !checkin.state.loading, let status = checkin.state.value, !status.checkedToday else { return }
        submitting = true; message = "正在签到"; writeVersion += 1
        let current = writeVersion; let session = epoch; let key = UUID().uuidString; let confirm = needsConfirmation
        AppLogger.log("checkin.submit.started", ["flowId": key])
        writing = Task { [weak self, repository] in
            do {
                var businessDate = status.businessDate
                if confirm {
                    let known = try await repository.checkin()
                    guard let self, self.active, self.epoch == session, self.writeVersion == current, !Task.isCancelled else { return }
                    self.checkin.accept(known); self.needsConfirmation = false
                    // 确认可能发生在跨日之后，后续写入必须使用刚查询到的业务日期。
                    businessDate = known.businessDate
                    if known.checkedToday { self.message = "今日已签到"; self.submitting = false; return }
                }
                let result = try await repository.submit(date: businessDate, key: key)
                guard let self, self.active, self.epoch == session, self.writeVersion == current, !Task.isCancelled else { return }
                self.checkin.accept(result); self.submitting = false; self.needsConfirmation = false
                self.message = result.alreadyChecked ? "今日已签到" : "签到成功，获得\(result.awardedEnergy)能量值"
                self.identity.load { try await repository.identity(member: true) }
                AppLogger.log("checkin.submit.completed", ["flowId": key])
            } catch {
                guard let self, self.active, self.epoch == session, self.writeVersion == current, !Task.isCancelled else { return }
                self.submitting = false; self.needsConfirmation = true
                if error is CancellationError { return }
                self.message = "签到结果待确认，请刷新后重试"
                AppLogger.log("checkin.submit.failed", ["flowId": key, "code": (error as? AppFailure)?.code ?? "NETWORK_UNAVAILABLE"], error: true)
            }
        }
    }
    override func cancel() {
        if submitting { needsConfirmation = true }; writeVersion += 1; writing?.cancel(); writing = nil; submitting = false; message = ""
        content.cancel(); identity.cancel(); checkin.cancel()
    }
    override func reset() { needsConfirmation = false; content.clear(); identity.clear(); checkin.clear() }
    deinit { writing?.cancel() }
}
@MainActor public final class ProfilePageViewModel: RootPageViewModel {
    public let identity = PageResource<Identity>(); public let assets = PageResource<AssetOverview>(); public let financial = PageResource<FinancialOverview>()
    private let repository: ProfileRepository
    public init(repository: ProfileRepository) { self.repository = repository; super.init() }
    public override func refresh() {
        guard active else { return }
        identity.load { [repository] in try await repository.identity(member: false) }; assets.load { [repository] in try await repository.assets() }; financial.load { [repository] in try await repository.financial() }
    }
    override func cancel() { identity.cancel(); assets.cancel(); financial.cancel() }
    override func reset() { identity.clear(); assets.clear(); financial.clear() }
}
/// Native模式只投影Shell状态，不创建另一套根Tab权威；隐私仅在当前会话内跨Tab共享。
@MainActor public final class DashboardCoordinator: ObservableObject {
    public let home: HomePageViewModel; public let wealth: WealthPageViewModel
    public let member: MemberPageViewModel; public let profile: ProfilePageViewModel
    @Published public private(set) var selectedTab = 1
    @Published public private(set) var epoch = -1
    @Published public private(set) var enabled = false
    @Published public private(set) var assetsVisible = false
    private var pages: [RootPageViewModel] { [home, wealth, member, profile] }
    public init(repositories: DashboardRepositorySet) {
        home = HomePageViewModel(repository: repositories); wealth = WealthPageViewModel(repository: repositories)
        member = MemberPageViewModel(repository: repositories); profile = ProfilePageViewModel(repository: repositories)
    }
    public func update(tab: Int, epoch: Int, authenticated: Bool, foreground: Bool) {
        guard (1...4).contains(tab) else { return }
        if self.selectedTab != tab || self.epoch != epoch || self.enabled != (authenticated && foreground) {
            AppLogger.log("dashboard.page.changed", ["pageId": ["home", "wealth", "member", "profile"][tab - 1], "epoch": epoch])
        }
        if self.epoch != epoch || !authenticated {
            pages.forEach { $0.invalidate() }; assetsVisible = false; self.epoch = epoch
        }
        for (index, page) in pages.enumerated() where index + 1 != tab || !authenticated || !foreground { page.deactivate() }
        selectedTab = tab; enabled = authenticated && foreground
        if enabled { pages[tab - 1].activate(epoch: epoch) }
    }
    public func suspend() { enabled = false; pages.forEach { $0.deactivate() } }
    public func togglePrivacy() { guard enabled else { return }; assetsVisible.toggle(); AppLogger.log("dashboard.privacy.toggled") }
}
