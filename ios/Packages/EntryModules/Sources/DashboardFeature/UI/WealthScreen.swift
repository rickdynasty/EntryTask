// 文件职责：财富页资产、行情、服务宫格和轻量产品摘要组装。
#if canImport(UIKit)
import SwiftUI

/// 财富使用轻量产品摘要，保留与首页完整产品卡的视觉差异。
struct WealthScreen: View {
    /// 页面VM，View只观察其状态并转发动作。
    @ObservedObject var model: WealthPageViewModel
    /// visible：当前显示敏感值或密码的局部/会话内状态。
    /// active：页面或轮播是否允许工作，由当前Tab与前台状态决定。
    /// toggle：会话内隐私切换动作，组件不另存持久偏好。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let visible: Bool; let active: Bool; let toggle: () -> Void; let open: (String) -> Void
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
    var body: some View {
        BankPage(identifier: "dashboard.wealth", refresh: model.refresh) {
            SectionHeader(title: "财富", subtitle: "资讯")
            ResourceRegion(resource: model.assets, retry: model.refresh) { data in AssetPrivacyCard(data: data, visible: visible, toggle: toggle) }
            ResourceRegion(resource: model.content, retry: model.refresh) { data in
                MarketTickerCarousel(items: data?.marketItems ?? [], active: active, open: open)
                if let data { StandardGridMenu(entries: data.services, open: open) }
                BankCard {
                    CategoryTabRow(entries: data?.categories ?? [], selected: model.category, select: model.select)
                    SectionHeader(title: "安稳纯债专区", subtitle: "专注债券投资 追求稳健风格")
                    ResourceRegion(resource: model.products, retry: model.refresh) { page in
                        if let page {
                            Text("当前分类共\(page.total)项 · 展示最多3项").font(.system(size: 11)).foregroundColor(BankStyle.muted)
                            if page.items.isEmpty { Text("该分类暂无产品").font(.caption) }
                            ForEach(Array(page.items.prefix(3))) { product in
                                Button { open(product.name) } label: { HStack { VStack(alignment: .leading, spacing: 6) { Text(product.name).font(.system(size: 14)); Text(product.term + " · " + product.riskLevel).font(.system(size: 11)).foregroundColor(BankStyle.muted) }; Spacer(); Text(product.percentage).foregroundColor(BankStyle.brand) }.padding(.vertical, 8) }.buttonStyle(.plain).foregroundColor(BankStyle.ink)
                            }
                        }
                    }
                }
            }
        }
    }
}
#endif
