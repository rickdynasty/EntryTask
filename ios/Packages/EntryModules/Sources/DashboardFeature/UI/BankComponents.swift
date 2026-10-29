// 文件职责：统一颜色语义，供四页与共用组件复用。
#if canImport(UIKit)
import SwiftUI
import UIKit
import CoreKit

/// 颜色、间距和共用组件集中维护，基础展示组件不认识API或会话。
enum BankStyle {
    /// 主要文字颜色。
    static let ink = Color(red: 0.18, green: 0.17, blue: 0.16)
    /// 辅助说明和占位文字颜色。
    static let muted = Color(red: 0.55, green: 0.53, blue: 0.50)
    /// 金色品牌及重点展示颜色。
    static let gold = Color(red: 0.68, green: 0.43, blue: 0.22)
    /// 品牌强调色，用于交互和重点数值。
    static let brand = Color(red: 0.89, green: 0.18, blue: 0.28)
    /// 页面背景色，区分白色卡片区域。
    static let background = Color(red: 0.96, green: 0.96, blue: 0.96)
    /// 运营区域使用的浅暖色背景。
    static let cream = Color(red: 1, green: 0.97, blue: 0.94)
}
/// 统一卡片容器，布局内容由调用方提供。
struct BankCard<Content: View>: View {
    /// 组件主题颜色，由调用方覆盖默认值。
    var color = Color.white
    /// 调用方提供的内容渲染闭包，组件只负责统一布局。
    @ViewBuilder let content: () -> Content
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View { VStack(alignment: .leading, spacing: 14, content: content).padding(16).frame(maxWidth: .infinity, alignment: .leading).background(color).cornerRadius(10) }
}
/// 统一区域标题与辅助说明。
struct SectionHeader: View {
    /// 组件或条目的显示标题，不作为网络路由权威。
    let title: String
    /// 区域辅助说明，空文本时不占用展示位置。
    var subtitle = ""
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title).font(.system(size: 18, weight: .semibold)).foregroundColor(BankStyle.ink)
            if !subtitle.isEmpty { Text(subtitle).font(.system(size: 11)).foregroundColor(BankStyle.muted) }
            Spacer(minLength: 0)
        }
    }
}
/// 每页一个纵向惰性列表；不同Tab保留各自ScrollView身份，epoch改变由上层重建。
struct BankPage<Content: View>: View {
    /// 页面无障碍标识，供设备UI测试定位。
    let identifier: String
    /// 刷新动作回调，由VM管理实际请求。
    let refresh: () -> Void
    /// 调用方提供的内容渲染闭包，组件只负责统一布局。
    @ViewBuilder let content: () -> Content
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) { content(); AppFooterLogo() }.padding(.horizontal, 12).padding(.top, 10)
        }.background(BankStyle.background).refreshable { refresh() }.accessibilityIdentifier(identifier)
    }
}
/// 共用银行品牌和页脚标语。
struct AppFooterLogo: View {
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        VStack(spacing: 14) { BankBrandView().scaleEffect(0.75); Text("让  财  富  有  温  度").font(.system(size: 11)).foregroundColor(BankStyle.muted) }
            .padding(.vertical, 44).frame(maxWidth: .infinity).accessibilityIdentifier("dashboard.footer")
    }
}
/// 区域保留上次成功内容；重试动作由页面VM提供，不在View中读网络。
struct ResourceRegion<Value, Content: View>: View {
    /// 区域资源状态所有者，提供数据、加载和错误。
    @ObservedObject var resource: PageResource<Value>
    /// 重试动作回调，不在展示组件内解释网络协议。
    let retry: () -> Void
    /// 调用方提供的内容渲染闭包，组件只负责统一布局。
    @ViewBuilder let content: (Value?) -> Content
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content(resource.state.value)
            // 加载和错误按区域独立展示，已有内容不会因局部刷新整体消失。
            if resource.state.loading { ProgressView().frame(maxWidth: .infinity).accessibilityLabel("正在加载") }
            if let error = resource.state.error {
                HStack { Text(error).font(.caption).foregroundColor(BankStyle.muted); Spacer(); Button("重试", action: retry) }.padding(8)
            }
        }
    }
}
/// 业务图标键到系统符号的受控映射。
struct BankIcon: View {
    /// 图标或素材的业务键，只映射预定义资源。
    let key: String
    /// 组件主题颜色，由调用方覆盖默认值。
    var color = BankStyle.gold
    /// 图标字号和尺寸基准。
    var size: CGFloat = 27
    /// 业务图标键映射的系统符号，未知键使用默认图标。
    private var symbol: String {
        let symbols = ["search":"magnifyingglass", "scan":"viewfinder", "message":"message", "settings":"gearshape", "deposit":"yensign.circle", "assets":"chart.pie", "wallet":"wallet.pass", "transfer":"arrow.left.arrow.right", "card":"creditcard", "fund":"chart.xyaxis.line", "shield":"shield", "exchange":"arrow.triangle.2.circlepath", "diamond":"diamond", "city":"building.2", "gold":"triangle", "family":"house", "more":"ellipsis.circle", "pig":"banknote", "hexagon":"hexagon", "globe":"globe.asia.australia", "ledger":"list.bullet.rectangle", "energy":"bolt", "gift":"gift", "crown":"crown", "user":"person"]
        return symbols[key] ?? "square.grid.2x2"
    }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View { Image(systemName: symbol).font(.system(size: size, weight: .light)).foregroundColor(color).frame(width: size + 8, height: size + 8).accessibilityHidden(true) }
}
/// 图片只有共享素材白名单；文件读取在后台，View卸载时不交付过期结果。
struct BankArtwork: View {
    /// 图标或素材的业务键，只映射预定义资源。
    let key: String
    /// 当前已加载的本地图片，不持有网络请求。
    @State private var image: UIImage?
    /// 允许输出的安全日志字段白名单。
    private static let allowed: Set<String> = ["hero-ginkgo", "certification-gift", "seasonal-gifts", "custom-cards", "crown", "book", "profile-avatar", "advisor-avatar", "credit-gift", "quick-0", "quick-1", "quick-2", "quick-3"]
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        Group { if let image { Image(uiImage: image).resizable().scaledToFit() } else { Color.clear } }
            .accessibilityHidden(true).task(id: key) {
                guard Self.allowed.contains(key), let url = Bundle.main.url(forResource: key, withExtension: "png", subdirectory: "dashboard-art") else { return }
                // 只将文件读取派到后台；返回后检查视图任务是否取消，拒绝过期图片交付。
                let data = await Task.detached(priority: .utility) { try? Data(contentsOf: url) }.value
                guard !Task.isCancelled, let data else { return }; image = UIImage(data: data)
            }
    }
}
/// 共用宫格入口，支持列数、快捷图和会员蓝色样式。
struct StandardGridMenu: View {
    /// 宫格或分类的只读条目集合。
    let entries: [MenuEntry]
    /// 宫格列数，由页面区域指定。
    var columns = 5
    /// 是否使用首页四快捷的共享图片样式。
    var quick = false
    /// 是否启用会员权益蓝色图标样式。
    var blue = false
    /// 演示入口回调，只传展示标题，不在组件内执行交易。
    let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: columns), spacing: 14) {
            ForEach(Array(entries.enumerated()), id: \.element.id) { index, entry in
                Button { open(entry.title) } label: {
                    VStack(spacing: 8) {
                        ZStack(alignment: .topTrailing) {
                            if quick { BankArtwork(key: "quick-\(index)").frame(width: 46, height: 46) }
                            else { BankIcon(key: entry.icon, color: blue ? .cyan : BankStyle.gold).frame(height: 38) }
                            if let badge = entry.badge, !badge.isEmpty { Text(badge).font(.system(size: 8)).foregroundColor(.white).padding(3).background(BankStyle.brand).cornerRadius(4).offset(x: 12, y: -3) }
                        }
                        Text(entry.title).font(.system(size: quick ? 14 : 12)).foregroundColor(BankStyle.ink).lineLimit(2).multilineTextAlignment(.center)
                    }.frame(maxWidth: .infinity, minHeight: quick ? 84 : 70)
                }.buttonStyle(.plain).accessibilityLabel(entry.title)
            }
        }
    }
}
/// 横向分类选择器，只回传分类动作。
struct CategoryTabRow: View {
    /// entries：宫格或分类的只读条目集合。
    /// selected：当前页或分类是否被选中，控制交互和展示。
    /// select：分类选择动作，查询与取消交给所属VM。
    let entries: [Category]; let selected: String; let select: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { ForEach(entries) { item in
                Button { select(item.id) } label: {
                    Text(item.title).font(.system(size: 13, weight: selected == item.id ? .semibold : .regular))
                        .foregroundColor(selected == item.id ? Color.white : BankStyle.muted).padding(.horizontal, 12).padding(.vertical, 10)
                        .background(selected == item.id ? BankStyle.gold : BankStyle.background).cornerRadius(5)
                }.accessibilityAddTraits(selected == item.id ? .isSelected : [])
            } }
        }
    }
}
/// 财富与我的共用资产卡，所有金额遵循同一显隐状态。
struct AssetPrivacyCard: View {
    /// data：响应字节或只读展示模型，不在View中直接写库。
    /// visible：当前显示敏感值或密码的局部/会话内状态。
    /// toggle：会话内隐私切换动作，组件不另存持久偏好。
    let data: AssetOverview?; let visible: Bool; let toggle: () -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        BankCard {
            HStack { SectionHeader(title: "资产总览"); Button(action: toggle) { Image(systemName: visible ? "eye" : "eye.slash").frame(width: 44, height: 44) }.accessibilityLabel(visible ? "隐藏资产" : "显示资产").accessibilityIdentifier("dashboard.privacy") }
            HStack {
                VStack(alignment: .leading, spacing: 7) { MoneyText(value: data?.total, visible: visible); Text("总资产（元）").font(.caption).foregroundColor(BankStyle.muted) }
                Spacer()
                VStack(alignment: .trailing, spacing: 7) { MoneyText(value: data?.income, visible: visible); Text("最新收益").font(.caption).foregroundColor(BankStyle.muted) }
            }
        }
    }
}
/// 统一金额占位、遮罩和排版。
struct MoneyText: View {
    /// 最近成功数据；刷新中或失败时可保留展示；当前显示敏感值或密码的局部/会话内状态。
    let value: Money?; let visible: Bool
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View { Text(visible ? value?.text ?? "—" : "******").font(.system(size: 21, weight: .semibold)).foregroundColor(BankStyle.ink).minimumScaleFactor(0.7).lineLimit(1) }
}
/// 首页完整产品卡，展示收益口径与风险说明。
struct FinancialProductCard: View {
    /// product：完整产品卡只读数据，包含风险提示与收益口径。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let product: Product; let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        Button { open(product.name) } label: {
            BankCard {
                HStack { Text(product.name).font(.system(size: 14)).lineLimit(1); Spacer(); Text(product.distribution).font(.system(size: 10)).foregroundColor(BankStyle.gold) }
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 7) { Text(product.percentage).font(.system(size: 27, weight: .semibold)).foregroundColor(product.ratio.hasPrefix("-") ? .green : BankStyle.brand); Text(product.indicatorLabel).font(.system(size: 10)).foregroundColor(BankStyle.muted) }
                    Spacer()
                    VStack(alignment: .leading, spacing: 7) { Text(product.term).font(.system(size: 17)); Text(product.minimum + " · " + product.riskLevel).font(.system(size: 10)).foregroundColor(BankStyle.muted) }
                }
                Text(product.riskText).font(.system(size: 10)).foregroundColor(BankStyle.muted).padding(8).frame(maxWidth: .infinity, alignment: .leading).background(BankStyle.background)
            }.foregroundColor(BankStyle.ink)
        }.buttonStyle(.plain)
    }
}
#endif
