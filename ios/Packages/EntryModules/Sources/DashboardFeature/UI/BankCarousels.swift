#if canImport(UIKit)
import SwiftUI

/// 页面可见、前台且非减少动态效果时运行；手指触碰暂停，销毁/停用自动取消Task。
struct BannerCarousel: View {
    let banners: [Banner]; let active: Bool; let open: (String) -> Void
    @State private var index = 0
    @GestureState private var touching = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var running: Bool { active && !reduceMotion && !touching && banners.count > 1 }
    var body: some View {
        VStack(spacing: 8) {
            if !banners.isEmpty {
                let item = banners[min(index, banners.count - 1)]
                Button { open(item.title) } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 10) { Text(item.title).font(.system(size: 26, weight: .semibold)); Text(item.subtitle).font(.system(size: 12)) }
                        Spacer(minLength: 0); BankArtwork(key: item.assetKey).frame(width: 142, height: 140)
                    }.foregroundColor(BankStyle.gold).padding(16).frame(maxWidth: .infinity, minHeight: 166).background(Color(red: 1, green: 0.91, blue: 0.83)).cornerRadius(10)
                }.buttonStyle(.plain)
                HStack(spacing: 6) { ForEach(banners.indices, id: \.self) { n in
                    Button { index = n } label: { Capsule().fill(n == index ? BankStyle.gold : BankStyle.gold.opacity(0.2)).frame(width: n == index ? 18 : 6, height: 5).padding(.vertical, 8) }.accessibilityLabel("第\(n + 1)张海报")
                } }
            }
        }.simultaneousGesture(DragGesture(minimumDistance: 0).updating($touching) { _, state, _ in state = true })
         .task(id: running) {
            while running && !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                guard !Task.isCancelled, !banners.isEmpty else { return }; index = (index + 1) % banners.count
            }
         }
    }
}
struct MarketTickerCarousel: View {
    let items: [MarketItem]; let active: Bool; let open: (String) -> Void
    @State private var index = 0
    @GestureState private var touching = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private var running: Bool { active && !reduceMotion && !touching && items.count > 1 }
    var body: some View {
        HStack(spacing: 12) {
            Text("市场\n解读").font(.system(size: 12, weight: .bold)).foregroundColor(BankStyle.brand)
            if !items.isEmpty {
                let item = items[min(index, items.count - 1)]
                Button { open(item.title) } label: { Text(item.title).id(item.id).font(.system(size: 12)).foregroundColor(BankStyle.ink).frame(maxWidth: .infinity, alignment: .leading).lineLimit(2).transition(.asymmetric(insertion: .move(edge: .bottom), removal: .move(edge: .top))) }.buttonStyle(.plain)
            } else { Text("正在加载市场解读").font(.caption).foregroundColor(BankStyle.muted); Spacer() }
            Image(systemName: "chevron.right").foregroundColor(BankStyle.brand).font(.caption)
        }.padding(12).frame(height: 58).background(Color.white).cornerRadius(8).clipped()
         .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: index)
         .simultaneousGesture(DragGesture(minimumDistance: 0).updating($touching) { _, state, _ in state = true })
         .task(id: running) {
            while running && !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                guard !Task.isCancelled, !items.isEmpty else { return }; index = (index + 1) % items.count
            }
         }
    }
}
#endif
