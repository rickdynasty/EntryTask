import UIKit
import SwiftUI
import Combine
import UserFeature
import CoreKit
import DashboardFeature

/// 稳定Shell全程存活；构建期选择RN或Native业务内容，根Tab和认证统一管理。
final class ShellViewController: UIViewController, UITabBarDelegate {
    private let graph: AppContainer
    private let tabBar = UITabBar()
    private let content = UIView()
    private var childHost: UIViewController?
    private var barHeight: NSLayoutConstraint!
    private var contentBottom: NSLayoutConstraint!
    private var cancellables = Set<AnyCancellable>()
    private var authController: UIViewController?
    private var presentingFlow: UUID?
    private var didStart = false
    private var logoutInProgress = false
    init(graph: AppContainer) { self.graph = graph; super.init(nibName: nil, bundle: nil) }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }
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
        graph.shell.$snapshot.sink { [weak self] _ in DispatchQueue.main.async { self?.render() } }.store(in: &cancellables)
        graph.shell.$authFlow.sink { [weak self] _ in DispatchQueue.main.async { self?.presentAuthenticationIfNeeded() } }.store(in: &cancellables)
        NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification).sink { [weak self] _ in
            guard let self, BuildConfiguration.pageMode == .native else { return }; self.graph.dashboard.suspend()
        }.store(in: &cancellables)
        NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification).sink { [weak self] _ in self?.resume() }.store(in: &cancellables)
    }
    override func viewSafeAreaInsetsDidChange() { super.viewSafeAreaInsetsDidChange(); barHeight?.constant = 49 + view.safeAreaInsets.bottom }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); resume() }
    private func resume() {
        graph.shell.refresh()
        if !didStart { didStart = true; if !graph.session.snapshot().authenticated { graph.shell.requestAuthentication() } }
        presentAuthenticationIfNeeded(); render()
    }
    func requestAuthentication() throws {
        guard view.window != nil, UIApplication.shared.applicationState == .active else { throw AppFailure("HOST_NOT_READY") }
        graph.shell.requestAuthentication(); presentAuthenticationIfNeeded()
    }
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
    private func finishAuthentication(_ flow: UUID) {
        guard flow == presentingFlow else { return }
        authController?.dismiss(animated: true) { [weak self] in
            guard let self, self.presentingFlow == flow else { return }
            self.authController = nil; self.presentingFlow = nil
            self.graph.shell.authenticationReturned(flow, success: self.graph.session.snapshot().authenticated)
            self.render()
        }
    }
    private func render() {
        let state = graph.shell.snapshot
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
        if BuildConfiguration.pageMode == .native, childHost != nil {
            graph.dashboard.update(tab: state.tabId, epoch: state.sessionEpoch, authenticated: state.authenticated,
                                   foreground: UIApplication.shared.applicationState == .active && authController == nil)
        }
        tabBar.selectedItem = tabBar.items?.first { $0.tag == state.tabId }
        let hidden = !state.authenticated || state.depth > 0
        tabBar.isHidden = hidden
        contentBottom.isActive = false
        contentBottom = hidden ? content.bottomAnchor.constraint(equalTo: view.bottomAnchor) : content.bottomAnchor.constraint(equalTo: tabBar.topAnchor)
        contentBottom.isActive = true
        if graph.shell.authFlow != nil { presentAuthenticationIfNeeded() }
    }
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
    func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) { graph.shell.selectTab(item.tag) }
    override var childForStatusBarStyle: UIViewController? { childHost }
}
