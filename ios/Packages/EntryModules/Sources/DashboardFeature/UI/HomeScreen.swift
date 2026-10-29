// 文件职责：首页区域组装，成功内容和局部加载状态分开展示。
#if canImport(UIKit)
import SwiftUI

/// 首页按原型区域排列；运营布局只支持已知四种样式，不解释远端脚本。
struct HomeScreen: View {
    /// 页面VM，View只观察其状态并转发动作。
    @ObservedObject var model: HomePageViewModel
    /// active：页面或轮播是否允许工作，由当前Tab与前台状态决定。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let active: Bool; let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        BankPage(identifier: "dashboard.home", refresh: model.refresh) {
            HomeTopBar(content: model.content, unread: model.unread, open: open)
            ResourceRegion(resource: model.content, retry: model.refresh) { data in
                if let data {
                    StandardGridMenu(entries: data.quickServices, columns: 4, quick: true, open: open)
                    BannerCarousel(banners: data.banners, active: active, open: open)
                    BankCard { StandardGridMenu(entries: data.services, open: open) }
                    HomeShortcutRow(entries: data.shortcuts, open: open)
                    ForEach(data.sections.filter { $0.id == "space" }) { HomeEditorialSection(section: $0, open: open) }
                }
            }
            BankCard(color: BankStyle.cream) {
                SectionHeader(title: "财富精选", subtitle: "把握市场动向 优中选优")
                ResourceRegion(resource: model.content, retry: model.refresh) { data in
                    CategoryTabRow(entries: data?.categories ?? [], selected: model.category, select: model.select)
                }
                ResourceRegion(resource: model.products, retry: model.refresh) { data in
                    if let data {
                        if data.items.isEmpty { Text("该分类暂无产品").font(.caption).foregroundColor(BankStyle.muted) }
                        // 根页最多展示三张产品卡，避免把分页列表全部塞进首页。
                        ForEach(Array(data.items.prefix(3))) { FinancialProductCard(product: $0, open: open) }
                        Button("查看更多产品 ›") { open("更多产品") }.font(.caption).foregroundColor(BankStyle.muted).frame(maxWidth: .infinity)
                    }
                }
            }
            ResourceRegion(resource: model.content, retry: model.refresh) { data in
                ForEach(data?.sections.filter { $0.id != "space" } ?? []) { HomeEditorialSection(section: $0, open: open) }
            }
        }
    }
}
/// 首页扫码、搜索、消息及顾问入口。
private struct HomeTopBar: View {
    /// 首页运营内容区域资源，为搜索提示及入口提供数据。
    @ObservedObject var content: PageResource<HomeContent>
    /// 未读数独立区域，不阻塞首页其他内容。
    @ObservedObject var unread: PageResource<Int>
    /// 演示入口回调，只传展示标题，不在组件内执行交易。
    let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        HStack(spacing: 12) {
            Button { open("扫一扫") } label: { BankIcon(key: "scan", color: BankStyle.ink) }.accessibilityLabel("扫一扫")
            Button { open("搜索") } label: { HStack { BankIcon(key: "search", color: BankStyle.muted, size: 17); Text(content.state.value?.searchPlaceholder ?? "搜索服务").font(.system(size: 13)).foregroundColor(BankStyle.muted); Spacer() }.padding(8).background(Color.white.opacity(0.8)).cornerRadius(7) }.accessibilityLabel("搜索")
            Button { open("消息通知") } label: {
                BankIcon(key: "message", color: BankStyle.ink).overlay(alignment: .topTrailing) {
                    if let count = unread.state.value, count > 0 { Text("\(min(count, 99))").font(.system(size: 9)).foregroundColor(.white).padding(4).background(BankStyle.brand).clipShape(Circle()).offset(x: 6, y: -4) }
                }
            }.accessibilityLabel("消息通知")
            Button { open("专属顾问") } label: { BankArtwork(key: "advisor-avatar").frame(width: 32, height: 38) }.accessibilityLabel("专属顾问")
        }.padding(10).background(BankStyle.cream)
    }
}
/// 首页横向快捷入口，不维护业务导航权威。
private struct HomeShortcutRow: View {
    /// entries：宫格或分类的只读条目集合。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let entries: [MenuEntry]; let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(entries) { item in
            Button(item.title) { open(item.title) }.font(.system(size: 12)).foregroundColor(BankStyle.muted).padding(10).background(Color.white).cornerRadius(4)
        } } }
    }
}
/// 按已知四种布局渲染运营区，拒绝解释远端代码。
private struct HomeEditorialSection: View {
    /// section：运营区域配置，只接受预定义布局样式。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let section: EditorialSection; let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        BankCard(color: BankStyle.cream) {
            if !section.title.isEmpty { SectionHeader(title: section.title, subtitle: section.subtitle) }
            // 只支持menu/banner/cards/news四种本地布局；不会将配置当代码执行。
            if section.style == "menu" {
                LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: section.entries.count == 4 ? 4 : 3)) {
                    ForEach(section.entries) { tile in Button { open(tile.title) } label: { VStack(spacing: 9) { BankIcon(key: tile.icon); Text(tile.title).font(.system(size: 12)).foregroundColor(BankStyle.ink) }.frame(maxWidth: .infinity, minHeight: 70) }.buttonStyle(.plain) }
                }
            } else if section.style == "banner" {
                ForEach(section.entries) { tile in
                    Button { open(tile.title) } label: {
                        HStack { VStack(alignment: .leading, spacing: 9) { Text(tile.title).font(.system(size: 24, weight: .semibold)); Text(tile.subtitle).font(.system(size: 11)) }; Spacer(); BankArtwork(key: tile.assetKey).frame(width: 95, height: 90) }.foregroundColor(BankStyle.gold)
                    }.buttonStyle(.plain)
                }
            } else if section.style == "cards" {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], alignment: .leading, spacing: 10) {
                    ForEach(section.entries) { tile in
                        Button { open(tile.title) } label: { BankCard(color: tile.tone == "blue" ? Color(red: 0.93, green: 0.95, blue: 1) : .white) { BankIcon(key: tile.icon); Text(tile.title).font(.system(size: 16, weight: .medium)).foregroundColor(BankStyle.gold); Text(tile.subtitle).font(.system(size: 12)).foregroundColor(BankStyle.muted).frame(maxWidth: .infinity, alignment: .leading) } }.buttonStyle(.plain)
                    }
                }
            } else if section.style == "news" {
                ForEach(section.entries) { tile in
                    Button { open(tile.title) } label: { HStack { VStack(alignment: .leading, spacing: 8) { Text(tile.title).font(.system(size: 14)).foregroundColor(BankStyle.ink); Text(tile.subtitle).font(.caption).foregroundColor(BankStyle.muted) }; Spacer(); BankIcon(key: tile.icon, size: 35) }.padding(.vertical, 6) }.buttonStyle(.plain)
                    Divider()
                }
            }
        }
    }
}
#endif
