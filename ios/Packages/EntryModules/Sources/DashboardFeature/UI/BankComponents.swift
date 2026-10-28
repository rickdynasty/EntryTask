#if canImport(UIKit)
import SwiftUI
import UIKit
import CoreKit

/// 颜色、间距和共用组件集中维护，基础展示组件不认识API或会话。
enum BankStyle {
    static let ink = Color(red: 0.18, green: 0.17, blue: 0.16)
    static let muted = Color(red: 0.55, green: 0.53, blue: 0.50)
    static let gold = Color(red: 0.68, green: 0.43, blue: 0.22)
    static let brand = Color(red: 0.89, green: 0.18, blue: 0.28)
    static let background = Color(red: 0.96, green: 0.96, blue: 0.96)
    static let cream = Color(red: 1, green: 0.97, blue: 0.94)
}
struct BankCard<Content: View>: View {
    var color = Color.white
    @ViewBuilder let content: () -> Content
    var body: some View { VStack(alignment: .leading, spacing: 14, content: content).padding(16).frame(maxWidth: .infinity, alignment: .leading).background(color).cornerRadius(10) }
}
struct SectionHeader: View {
    let title: String
    var subtitle = ""
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
    let identifier: String
    let refresh: () -> Void
    @ViewBuilder let content: () -> Content
    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) { content(); AppFooterLogo() }.padding(.horizontal, 12).padding(.top, 10)
        }.background(BankStyle.background).refreshable { refresh() }.accessibilityIdentifier(identifier)
    }
}
struct AppFooterLogo: View {
    var body: some View {
        VStack(spacing: 14) { BankBrandView().scaleEffect(0.75); Text("让  财  富  有  温  度").font(.system(size: 11)).foregroundColor(BankStyle.muted) }
            .padding(.vertical, 44).frame(maxWidth: .infinity).accessibilityIdentifier("dashboard.footer")
    }
}
/// 区域保留上次成功内容；重试动作由页面VM提供，不在View中读网络。
struct ResourceRegion<Value, Content: View>: View {
    @ObservedObject var resource: PageResource<Value>
    let retry: () -> Void
    @ViewBuilder let content: (Value?) -> Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            content(resource.state.value)
            if resource.state.loading { ProgressView().frame(maxWidth: .infinity).accessibilityLabel("正在加载") }
            if let error = resource.state.error {
                HStack { Text(error).font(.caption).foregroundColor(BankStyle.muted); Spacer(); Button("重试", action: retry) }.padding(8)
            }
        }
    }
}
struct BankIcon: View {
    let key: String
    var color = BankStyle.gold
    var size: CGFloat = 27
    private var symbol: String {
        let symbols = ["search":"magnifyingglass", "scan":"viewfinder", "message":"message", "settings":"gearshape", "deposit":"yensign.circle", "assets":"chart.pie", "wallet":"wallet.pass", "transfer":"arrow.left.arrow.right", "card":"creditcard", "fund":"chart.xyaxis.line", "shield":"shield", "exchange":"arrow.triangle.2.circlepath", "diamond":"diamond", "city":"building.2", "gold":"triangle", "family":"house", "more":"ellipsis.circle", "pig":"banknote", "hexagon":"hexagon", "globe":"globe.asia.australia", "ledger":"list.bullet.rectangle", "energy":"bolt", "gift":"gift", "crown":"crown", "user":"person"]
        return symbols[key] ?? "square.grid.2x2"
    }
    var body: some View { Image(systemName: symbol).font(.system(size: size, weight: .light)).foregroundColor(color).frame(width: size + 8, height: size + 8).accessibilityHidden(true) }
}
/// 图片只有共享素材白名单；文件读取在后台，View卸载时不交付过期结果。
struct BankArtwork: View {
    let key: String
    @State private var image: UIImage?
    private static let allowed: Set<String> = ["hero-ginkgo", "certification-gift", "seasonal-gifts", "custom-cards", "crown", "book", "profile-avatar", "advisor-avatar", "credit-gift", "quick-0", "quick-1", "quick-2", "quick-3"]
    var body: some View {
        Group { if let image { Image(uiImage: image).resizable().scaledToFit() } else { Color.clear } }
            .accessibilityHidden(true).task(id: key) {
                guard Self.allowed.contains(key), let url = Bundle.main.url(forResource: key, withExtension: "png", subdirectory: "dashboard-art") else { return }
                let data = await Task.detached(priority: .utility) { try? Data(contentsOf: url) }.value
                guard !Task.isCancelled, let data else { return }; image = UIImage(data: data)
            }
    }
}
struct StandardGridMenu: View {
    let entries: [MenuEntry]
    var columns = 5
    var quick = false
    var blue = false
    let open: (String) -> Void
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
struct CategoryTabRow: View {
    let entries: [Category]; let selected: String; let select: (String) -> Void
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
struct AssetPrivacyCard: View {
    let data: AssetOverview?; let visible: Bool; let toggle: () -> Void
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
struct MoneyText: View {
    let value: Money?; let visible: Bool
    var body: some View { Text(visible ? value?.text ?? "—" : "******").font(.system(size: 21, weight: .semibold)).foregroundColor(BankStyle.ink).minimumScaleFactor(0.7).lineLimit(1) }
}
struct FinancialProductCard: View {
    let product: Product; let open: (String) -> Void
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
