// 文件职责：四域VM与Shell状态投影；协调区域查询、签到防重、生命周期和会话内隐私。
import Foundation
import Combine
import CoreKit

/// 首页区域查询与分类切换状态，IO交给仓储。
@MainActor public final class HomePageViewModel: RootPageViewModel {
    /// content：运营内容的独立区域资源。
    /// unread：未读数独立区域，不阻塞首页其他内容。
    /// products：当前分类的独立产品查询资源。
    public let content = PageResource<HomeContent>(); public let unread = PageResource<Int>(); public let products = PageResource<ProductPage>()
    /// 选中的产品分类，改变时取消旧分类查询。
    @Published public private(set) var category = "all"
    /// 通过契约注入的数据来源，VM不直接操作网络或数据库。
    private let repository: HomeRepository
    /// 注入当前域仓储，查询在页面激活后才开始。
    public init(repository: HomeRepository) { self.repository = repository; super.init() }
    /// 切换分类前清理旧资源，避免旧分类响应覆盖当前选择。
    public func select(_ id: String) {
        guard active, category != id else { return }; category = id; products.clear(); loadProducts()
    }
    /// 捕获当前分类后交给资源请求管理器。
    private func loadProducts() { let id = category; products.load { [repository] in try await repository.products(category: id) } }
    /// 仅在当前页活跃时并行刷新各区域，已有成功数据保持展示。
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.home() }; unread.load { [repository] in try await repository.unread() }; loadProducts()
    }
    /// 取消本页各区域在途请求，已成功内容保留到当前会话失效。
    override func cancel() { content.cancel(); unread.cancel(); products.cancel() }
    /// 清空当前会话数据和分类；不同用户不复用旧展示值。
    override func reset() { category = "all"; content.clear(); unread.clear(); products.clear() }
}
/// 财富区域查询与产品分类状态。
@MainActor public final class WealthPageViewModel: RootPageViewModel {
    /// content：运营内容的独立区域资源。
    /// assets：资产区域资源，金额显隐由共享状态决定。
    /// products：当前分类的独立产品查询资源。
    public let content = PageResource<WealthContent>(); public let assets = PageResource<AssetOverview>(); public let products = PageResource<ProductPage>()
    /// 选中的产品分类，改变时取消旧分类查询。
    @Published public private(set) var category = "fund"
    /// 通过契约注入的数据来源，VM不直接操作网络或数据库。
    private let repository: WealthRepository
    /// 注入当前域仓储，查询在页面激活后才开始。
    public init(repository: WealthRepository) { self.repository = repository; super.init() }
    /// 切换分类前清理旧资源，避免旧分类响应覆盖当前选择。
    public func select(_ id: String) { guard active, category != id else { return }; category = id; products.clear(); loadProducts() }
    /// 捕获当前分类后交给资源请求管理器。
    private func loadProducts() { let id = category; products.load { [repository] in try await repository.products(category: id) } }
    /// 仅在当前页活跃时并行刷新各区域，已有成功数据保持展示。
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.wealth() }; assets.load { [repository] in try await repository.assets() }; loadProducts()
    }
    /// 取消本页各区域在途请求，已成功内容保留到当前会话失效。
    override func cancel() { content.cancel(); assets.cancel(); products.cancel() }
    /// 清空当前会话数据和分类；不同用户不复用旧展示值。
    override func reset() { category = "fund"; content.clear(); assets.clear(); products.clear() }
}
/// 写结果不确定时先读取服务端事实，不自动重放；切Tab/退出期间禁止迟到更新和重复提交。
@MainActor public final class MemberPageViewModel: RootPageViewModel {
    /// 会员运营、身份和签到事实分别维护区域资源，局部失败互不清空。
    public let content = PageResource<MemberContent>(); public let identity = PageResource<Identity>(); public let checkin = PageResource<CheckinStatus>()
    /// 签到写入防重状态，提交期间禁用按钮。
    @Published public private(set) var submitting = false
    /// 待消费的用户提示，不携带底层敏感异常。
    @Published public private(set) var message = ""
    /// 通过契约注入的数据来源，VM不直接操作网络或数据库。
    private let repository: MemberRepository
    /// 签到写任务，切页或失效时取消。
    private var writing: Task<Void, Never>?
    /// 写操作代次，阻止旧写响应回写新页面。
    private var writeVersion = 0
    /// 上次写结果未知，下一次写前必须先查询事实。
    private var needsConfirmation = false
    /// 注入当前域仓储，查询在页面激活后才开始。
    public init(repository: MemberRepository) { self.repository = repository; super.init() }
    /// 仅在当前页活跃时并行刷新各区域，已有成功数据保持展示。
    public override func refresh() {
        guard active else { return }
        content.load { [repository] in try await repository.member() }; identity.load { [repository] in try await repository.identity(member: true) }
        // 写入期间不并发刷新签到事实，避免旧查询盖掉刚提交的结果。
        if !submitting { checkin.load { [repository] in try await repository.checkin() } }
    }
    /// 校验并防重提交；结果只允许写回当前活动生命周期。
    public func submit() {
        guard active, !submitting, !checkin.state.loading, let status = checkin.state.value, !status.checkedToday else { return }
        submitting = true; message = "正在签到"; writeVersion += 1
        let current = writeVersion; let session = epoch; let key = UUID().uuidString; let confirm = needsConfirmation
        AppLogger.log("checkin.submit.started", ["flowId": key])
        writing = Task { [weak self, repository] in
            do {
                var businessDate = status.businessDate
                // 前次写结果未知时先查事实；仅确认未签到后才继续一次用户明确发起的提交。
                if confirm {
                    let known = try await repository.checkin()
                    guard let self, self.active, self.epoch == session, self.writeVersion == current, !Task.isCancelled else { return }
                    self.checkin.accept(known); self.needsConfirmation = false
                    // 确认可能发生在跨日之后，后续写入必须使用刚查询到的业务日期。
                    businessDate = known.businessDate
                    if known.checkedToday { self.message = "今日已签到"; self.submitting = false; return }
                }
                // 写结果返回后仍检查活跃页、epoch和写代次，避免切页/退出后迟到更新。
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
    /// 取消所属区域请求；会员页另外取消写任务并保留结果待确认标记。
    override func cancel() {
        // 取消不会撤销已提交事实；标记待确认，回到页面后先读再决定是否可写。
        if submitting { needsConfirmation = true }; writeVersion += 1; writing?.cancel(); writing = nil; submitting = false; message = ""
        content.cancel(); identity.cancel(); checkin.cancel()
    }
    /// 清空当前会话数据和分类；不同用户不复用旧展示值。
    override func reset() { needsConfirmation = false; content.clear(); identity.clear(); checkin.clear() }
    /// 释放所有者时取消仍在运行的任务，避免继续交付页面结果。
    deinit { writing?.cancel() }
}
/// 个人资料、资产和月度收支的独立区域状态。
@MainActor public final class ProfilePageViewModel: RootPageViewModel {
    /// identity：用户/会员资料的独立查询资源。
    /// assets：资产区域资源，金额显隐由共享状态决定。
    /// financial：月度收支、账单与营销额度的独立资源。
    public let identity = PageResource<Identity>(); public let assets = PageResource<AssetOverview>(); public let financial = PageResource<FinancialOverview>()
    /// 通过契约注入的数据来源，VM不直接操作网络或数据库。
    private let repository: ProfileRepository
    /// 注入当前域仓储，查询在页面激活后才开始。
    public init(repository: ProfileRepository) { self.repository = repository; super.init() }
    /// 仅在当前页活跃时并行刷新各区域，已有成功数据保持展示。
    public override func refresh() {
        guard active else { return }
        identity.load { [repository] in try await repository.identity(member: false) }; assets.load { [repository] in try await repository.assets() }; financial.load { [repository] in try await repository.financial() }
    }
    /// 取消本页各区域在途请求，已成功内容保留到当前会话失效。
    override func cancel() { identity.cancel(); assets.cancel(); financial.cancel() }
    /// 清空当前会话数据和分类；不同用户不复用旧展示值。
    override func reset() { identity.clear(); assets.clear(); financial.clear() }
}
/// Native模式只投影Shell状态，不创建另一套根Tab权威；隐私仅在当前会话内跨Tab共享。
@MainActor public final class DashboardCoordinator: ObservableObject {
    /// home：首页VM，只有被Shell激活时才发请求。
    /// wealth：财富页VM，切离后暂停查询。
    public let home: HomePageViewModel; public let wealth: WealthPageViewModel
    /// member：会员页VM，负责签到查询与提交。
    /// profile：我的页VM，资料、资产与收支分别加载。
    public let member: MemberPageViewModel; public let profile: ProfilePageViewModel
    /// Shell根Tab的只读投影，不建立第二套导航权威。
    @Published public private(set) var selectedTab = 1
    /// 会话代次；退出和认证提交后变化，旧结果不得跨代次回写。
    @Published public private(set) var epoch = -1
    /// 当前已认证且前台的可交互状态。
    @Published public private(set) var enabled = false
    /// 当前会话内跨Tab共享的隐私状态，退出时复位隐藏。
    @Published public private(set) var assetsVisible = false
    /// 四页生命周期统一访问顺序，与根Tab编号一致。
    private var pages: [RootPageViewModel] { [home, wealth, member, profile] }
    /// 注入所属域仓储；协调器仅创建四页VM，不立即请求所有Tab。
    public init(repositories: DashboardRepositorySet) {
        home = HomePageViewModel(repository: repositories); wealth = WealthPageViewModel(repository: repositories)
        member = MemberPageViewModel(repository: repositories); profile = ProfilePageViewModel(repository: repositories)
    }
    /// 投影Shell的Tab/epoch/认证/前台状态，只激活当前合法页面。
    public func update(tab: Int, epoch: Int, authenticated: Bool, foreground: Bool) {
        guard (1...4).contains(tab) else { return }
        if self.selectedTab != tab || self.epoch != epoch || self.enabled != (authenticated && foreground) {
            AppLogger.log("dashboard.page.changed", ["pageId": ["home", "wealth", "member", "profile"][tab - 1], "epoch": epoch])
        }
        // 会话变化或未认证时统一失效四页数据，并把资产显隐恢复为隐藏。
        if self.epoch != epoch || !authenticated {
            pages.forEach { $0.invalidate() }; assetsVisible = false; self.epoch = epoch
        }
        // 先停用非当前页，再激活合法当前页；创建四个VM不等于同时请求四个Tab。
        for (index, page) in pages.enumerated() where index + 1 != tab || !authenticated || !foreground { page.deactivate() }
        selectedTab = tab; enabled = authenticated && foreground
        if enabled { pages[tab - 1].activate(epoch: epoch) }
    }
    /// 应用非活跃时暂停四页请求与交互，不退出Native会话。
    public func suspend() { enabled = false; pages.forEach { $0.deactivate() } }
    /// 在有效会话内切换四页共用显隐状态，不持久化资产数据。
    public func togglePrivacy() { guard enabled else { return }; assetsVisible.toggle(); AppLogger.log("dashboard.privacy.toggled") }
}
