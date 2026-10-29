// 文件职责：演示弹层的独立身份和标题，支持SwiftUI sheet绑定。
#if canImport(UIKit)
import SwiftUI

/// 演示弹层的独立身份和标题，支持SwiftUI sheet绑定。
private struct DemoAction: Identifiable { let id = UUID(); let title: String }
/// 唯一Native业务根；原生底栏仍由Shell驱动，四页保留滚动身份，不创建RN runtime。
public struct NativeDashboardView: View {
    /// Shell状态到四页VM的投影协调器。
    @ObservedObject private var coordinator: DashboardCoordinator
    /// 安全退出回调，实际会话清理由Native Shell完成。
    private let logout: () -> Void
    /// 当前演示弹层状态，切Tab或换会话时清空。
    @State private var demo: DemoAction?
    /// 接收已有协调器与宿主退出动作，不自行构建运行时或账号服务。
    public init(coordinator: DashboardCoordinator, logout: @escaping () -> Void) { self.coordinator = coordinator; self.logout = logout }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    public var body: some View {
        // 四页维持各自View身份；仅当前页可见可点，epoch变化由外层id重建滚动与局部状态。
        ZStack {
            HomeScreen(model: coordinator.home, active: coordinator.enabled && demo == nil && coordinator.selectedTab == 1, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 1))
            WealthScreen(model: coordinator.wealth, visible: coordinator.assetsVisible, active: coordinator.enabled && demo == nil && coordinator.selectedTab == 2, toggle: coordinator.togglePrivacy, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 2))
            MemberScreen(model: coordinator.member, visible: coordinator.assetsVisible, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 3))
            ProfileScreen(model: coordinator.profile, visible: coordinator.assetsVisible, toggle: coordinator.togglePrivacy, logout: logout, open: show).modifier(PageVisibility(selected: coordinator.selectedTab == 4))
        }.id(coordinator.epoch).foregroundColor(BankStyle.ink)
         .accessibilityIdentifier("dashboard.native")
         .sheet(item: $demo) { action in DemoActionSheet(title: action.title) { demo = nil } }
         // 换会话或切Tab关闭演示弹层，避免旧页面提示残留。
         .onChange(of: coordinator.epoch) { _ in demo = nil }
         .onChange(of: coordinator.selectedTab) { _ in demo = nil }
    }
    /// 只在业务内容可交互时展示演示说明，不触发真实交易。
    private func show(_ title: String) { guard coordinator.enabled else { return }; demo = DemoAction(title: title) }
}
/// 保留隐藏页身份，同时关闭其触摸与无障碍访问。
private struct PageVisibility: ViewModifier {
    /// 当前页或分类是否被选中，控制交互和展示。
    let selected: Bool
    /// 只根据传入状态组织视图并转发动作，不在渲染过程中发请求或写库。
    func body(content: Content) -> some View { content.opacity(selected ? 1 : 0).allowsHitTesting(selected).accessibilityHidden(!selected) }
}
/// 说明本地演示范围的共用弹层，不执行真实交易。
struct DemoActionSheet: View {
    /// title：组件或条目的显示标题，不作为网络路由权威。
    /// close：关闭演示弹层的回调。
    let title: String; let close: () -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        VStack(spacing: 22) { Text(title).font(.title2.bold()); Text("当前为本地业务演示入口，暂未开放真实交易。\n页面数据由 Native Mock 提供。").font(.body).foregroundColor(BankStyle.muted).multilineTextAlignment(.center); Button("关闭", action: close).padding(12).foregroundColor(.white).frame(maxWidth: .infinity).background(BankStyle.brand).cornerRadius(8) }.padding(32)
    }
}
#endif
