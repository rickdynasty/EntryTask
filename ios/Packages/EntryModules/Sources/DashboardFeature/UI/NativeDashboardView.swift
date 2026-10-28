#if canImport(UIKit)
import SwiftUI

private struct DemoAction: Identifiable { let id = UUID(); let title: String }
/// 唯一Native业务根；原生底栏仍由Shell驱动，四页保留滚动身份，不创建RN runtime。
public struct NativeDashboardView: View {
    @ObservedObject private var coordinator: DashboardCoordinator
    private let logout: () -> Void
    @State private var demo: DemoAction?
    public init(coordinator: DashboardCoordinator, logout: @escaping () -> Void) { self.coordinator = coordinator; self.logout = logout }
    public var body: some View {
        ZStack {
            HomeScreen(model: coordinator.home, active: coordinator.enabled && demo == nil && coordinator.selectedTab == 1, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 1))
            WealthScreen(model: coordinator.wealth, visible: coordinator.assetsVisible, active: coordinator.enabled && demo == nil && coordinator.selectedTab == 2, toggle: coordinator.togglePrivacy, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 2))
            MemberScreen(model: coordinator.member, visible: coordinator.assetsVisible, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 3))
            ProfileScreen(model: coordinator.profile, visible: coordinator.assetsVisible, toggle: coordinator.togglePrivacy, logout: logout, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 4))
        }.id(coordinator.epoch).foregroundColor(BankStyle.ink)
         .accessibilityIdentifier("dashboard.native")
         .sheet(item: $demo) { action in DemoActionSheet(title: action.title) { demo = nil } }
         .onChange(of: coordinator.epoch) { _ in demo = nil }
         .onChange(of: coordinator.selectedTab) { _ in demo = nil }
    }
    private func show(_ title: String) { guard coordinator.enabled else { return }; demo = DemoAction(title: title) }
}
private struct PageVisibility: ViewModifier {
    let selected: Bool
    func body(content: Content) -> some View { content.opacity(selected ? 1 : 0).allowsHitTesting(selected).accessibilityHidden(!selected) }
}
struct DemoActionSheet: View {
    let title: String; let close: () -> Void
    var body: some View {
        VStack(spacing: 22) { Text(title).font(.title2.bold()); Text("当前为本地业务演示入口，暂未开放真实交易。\n页面数据由 Native Mock 提供。").font(.body).foregroundColor(BankStyle.muted).multilineTextAlignment(.center); Button("关闭", action: close).padding(12).foregroundColor(.white).frame(maxWidth: .infinity).background(BankStyle.brand).cornerRadius(8) }.padding(32)
    }
}
#endif
