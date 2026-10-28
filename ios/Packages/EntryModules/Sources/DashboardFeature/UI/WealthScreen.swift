#if canImport(UIKit)
import SwiftUI

/// 财富使用轻量产品摘要，保留与首页完整产品卡的视觉差异。
struct WealthScreen: View {
    @ObservedObject var model: WealthPageViewModel
    let visible: Bool; let active: Bool; let toggle: () -> Void; let open: (String) -> Void
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
