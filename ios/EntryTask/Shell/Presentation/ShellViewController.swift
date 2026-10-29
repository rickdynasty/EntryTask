// 文件职责：稳定Native宿主，负责UIKit底栏、认证呈现和唯一业务内容容器。
import UIKit
import SwiftUI
import Combine
import UserFeature
import CoreKit
import DashboardFeature

/// 稳定Shell全程存活；构建期选择RN或Native业务内容，根Tab和认证统一管理。
final class ShellViewController: UIViewController, UITabBarDelegate {
    /// 组合根引用，向宿主提供组装好的业务依赖。
    private let graph: AppContainer
    /// 两种页面模式共用的UIKit根底栏。
    private let tabBar = UITabBar()
    /// 唯一业务内容UIView容器，承载当前构建模式的子控制器。
    private let content = UIView()
    /// 已挂载的唯一业务子控制器，认证往返期间继续保留。
    private var childHost: UIViewController?
    /// 底栏高度约束，包含底部安全区。
    private var barHeight: NSLayoutConstraint!
    /// 内容底边约束，按底栏显隐重新绑定。
    private var contentBottom: NSLayoutConstraint!
    /// Combine订阅集合，随宿主释放注销观察。
    private var cancellables = Set<AnyCancellable>()
    /// 当前全屏认证容器，用于防止重复呈现。
    private var authController: UIViewController?
    /// 正在展示的认证流程编号，过滤旧流程回调。
    private var presentingFlow: UUID?
    /// 启动认证门禁的一次性标记，避免每次出现都新建流程。
    private var didStart = false
    /// 退出操作防重标记。
    private var logoutInProgress = false
    /// 注入既有应用图，根宿主不创建第二套会话与网络。
    init(graph: AppContainer) { self.graph = graph; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
    /// 组装唯一内容区和Native底栏，订阅Shell与前后台变化。
    override func viewDidLoad() {
        super.viewDidLoad(); graph.host = self; view.backgroundColor = .systemBackground
        content.translatesAutoresizingMaskIntoConstraints = false; tabBar.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content); view.addSubview(tabBar)
        let titles = ["首页", "财富", "会员", "我的"]
        let icons = ["building.columns", "diamond", "crown", "person"]
        tabBar.items = zip(titles, icons).enumerated().map { UITabBarItem(title: $0.element.0, image: UIImage(systemName: $0.element.1), tag: $0.offset + 1) }
        tabBar.delegate = self; tabBar.tintColor = UIColor(red: 0.73, green: 0.46, blue: 0.24, alpha: 1)
        let appearance = UITabBarAppearance(); appearance.configureWithOpaqueBackground(); appearance.backgroundColor = .systemBackground
        tabBar.standardAppearance = appearance; tabBar.scrollEdgeAppearance = appearance
        barHeight = tabBar.heightAnchor.constraint(equalToConstant: 49)
        contentBottom = content.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        NSLayoutConstraint.activate([
            content.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor), content.leadingAnchor.constraint(equalTo: view.leadingAnchor), content.trailingAnchor.constraint(equalTo: view.trailingAnchor), contentBottom,
            tabBar.leadingAnchor.constraint(equalTo: view.leadingAnchor), tabBar.trailingAnchor.constraint(equalTo: view.trailingAnchor), tabBar.bottomAnchor.constraint(equalTo: view.bottomAnchor), barHeight
        ])
        // Shell仍是唯一状态源；观察回调延后到主队列，避免发布过程重入渲染。
        graph.shell.$snapshot.sink { [weak self] _ in DispatchQueue.main.async { self?.render() } }.store(in: &cancellables)
        graph.shell.$authFlow.sink { [weak self] _ in DispatchQueue.main.async { self?.presentAuthenticationIfNeeded() } }.store(in: &cancellables)
        NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification).sink { [weak self] _ in
            guard let self, BuildConfiguration.pageMode == .native else { return }; self.graph.dashboard.suspend()
        }.store(in: &cancellables)
        NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification).sink { [weak self] _ in self?.resume() }.store(in: &cancellables)
    }
    /// 安全区变化时更新底栏高度，避免内容被底部指示条遮挡。
    override func viewSafeAreaInsetsDidChange() { super.viewSafeAreaInsetsDidChange(); barHeight?.constant = 49 + view.safeAreaInsets.bottom }
    /// 宿主可见后恢复状态与认证协调。
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); resume() }
    /// 刷新Native会话；仅首次执行启动认证门禁，再同步当前界面。
    private func resume() {
        graph.shell.refresh()
        if !didStart { didStart = true; if !graph.session.snapshot().authenticated { graph.shell.requestAuthentication() } }
        presentAuthenticationIfNeeded(); render()
    }
    /// 只允许前台可见宿主呈现认证；Shell负责合并已有流程。
    func requestAuthentication() throws {
        guard view.window != nil, UIApplication.shared.applicationState == .active else { throw AppFailure("HOST_NOT_READY") }
        graph.shell.requestAuthentication(); presentAuthenticationIfNeeded()
    }
    /// 检查flow、前台及已有弹层，展示不可下拉关闭的全屏认证页。
    private func presentAuthenticationIfNeeded() {
        guard let flow = graph.shell.authFlow, authController == nil, presentedViewController == nil,
              view.window != nil, UIApplication.shared.applicationState == .active else { return }
        presentingFlow = flow
        let screen = AuthScreen(repository: graph.auth) { [weak self] in self?.finishAuthentication(flow) }
        let host = UIHostingController(rootView: screen)
        host.modalPresentationStyle = .fullScreen; host.isModalInPresentation = true
        authController = host; present(host, animated: false)
        AppLogger.log("auth.presentation.started", ["flowId": flow.uuidString])
    }
    /// 核对流程编号，关闭认证后重新读取Native会话并刷新业务内容。
    private func finishAuthentication(_ flow: UUID) {
        guard flow == presentingFlow else { return }
        // 关闭动画结束后再次核对flow，旧认证回调不得结束新流程。
        authController?.dismiss(animated: true) { [weak self] in
            guard let self, self.presentingFlow == flow else { return }
            self.authController = nil; self.presentingFlow = nil
            self.graph.shell.authenticationReturned(flow, success: self.graph.session.snapshot().authenticated)
            self.render()
        }
    }
    /// 按构建模式首次挂载唯一内容；持续投影认证、Tab及底栏显隐。
    private func render() {
        let state = graph.shell.snapshot
        // 内容只首次挂载；认证返回和切Tab仅更新状态，保持根宿主及单Surface存活。
        if (state.authenticated || graph.shell.mayMountGate) && childHost == nil {
            let child: UIViewController
            if BuildConfiguration.pageMode == .rn {
                child = RNContainerViewController(root: graph.runtime.rootView())
            } else {
                child = UIHostingController(rootView: NativeDashboardView(coordinator: graph.dashboard) { [weak self] in self?.logout() })
            }
            child.view.accessibilityIdentifier = BuildConfiguration.pageMode == .rn ? "dashboard.rn" : "dashboard.native"
            AppLogger.log("dashboard.mounted", ["module": BuildConfiguration.pageMode.rawValue])
            addChild(child); content.addSubview(child.view); child.view.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([child.view.leadingAnchor.constraint(equalTo: content.leadingAnchor), child.view.trailingAnchor.constraint(equalTo: content.trailingAnchor), child.view.topAnchor.constraint(equalTo: content.topAnchor), child.view.bottomAnchor.constraint(equalTo: content.bottomAnchor)])
            child.didMove(toParent: self); childHost = child
        }
        // Native内容只在已认证、前台且认证容器关闭后激活对应VM。
        if BuildConfiguration.pageMode == .native, childHost != nil {
            graph.dashboard.update(tab: state.tabId, epoch: state.sessionEpoch, authenticated: state.authenticated,
                                   foreground: UIApplication.shared.applicationState == .active && authController == nil)
        }
        tabBar.selectedItem = tabBar.items?.first { $0.tag == state.tabId }
        // 未认证或进入业务子页时隐藏根底栏，同时释放其占用的内容空间。
        let hidden = !state.authenticated || state.depth > 0
        tabBar.isHidden = hidden
        contentBottom.isActive = false
        contentBottom = hidden ? content.bottomAnchor.constraint(equalTo: view.bottomAnchor) : content.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        contentBottom.isActive = true
        if graph.shell.authFlow != nil { presentAuthenticationIfNeeded() }
    }
    /// 串行清除Native会话并通知UI，旧请求不能跨epoch回写。
    private func logout() {
        guard !logoutInProgress else { return }; logoutInProgress = true
        Task { [weak self] in
            guard let self else { return }
            defer { self.logoutInProgress = false }
            do {
                try await self.graph.shell.logout(); self.graph.shell.requestAuthentication(); self.presentAuthenticationIfNeeded(); self.render()
            } catch { AppLogger.log("auth.logout.failed", ["code": "NATIVE_ERROR"], error: true) }
        }
    }
    /// 把UIKit底栏选择交给Shell，View不另存根导航状态。
    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) { graph.shell.selectTab(item.tag) }
    /// 将状态栏样式查询交给当前业务子控制器。
    override var childForStatusBarStyle: UIViewController? { childHost }
}
