// 文件职责：首页海报轮播，停用、触摸或减少动态效果时暂停。
#if canImport(UIKit)
import SwiftUI

/// 页面可见、前台且非减少动态效果时运行；手指触碰暂停，销毁/停用自动取消Task。
struct BannerCarousel: View {
    /// banners：只读海报集合，素材必须来自本地白名单。
    /// active：页面或轮播是否允许工作，由当前Tab与前台状态决定。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let banners: [Banner]; let active: Bool; let open: (String) -> Void
    /// 当前轮播下标，按有界内容数量循环。
    @State private var index = 0
    /// 手势期间暂停自动轮播，避免与用户操作竞争。
    @GestureState private var touching = false
    /// 系统减少动态效果偏好，开启时暂停自动切换。
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 由活跃、触摸、无障碍设置及数量共同决定的轮播条件。
    private var running: Bool { active && !reduceMotion && !touching && banners.count > 1 }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
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
         // running变化会取消旧Task；减少动态效果、手势或切页都能停止计时。
         .task(id: running) {
            while running && !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                // sleep结束再次校验取消，按当前有限条目数循环下标。
                guard !Task.isCancelled, !banners.isEmpty else { return }; index = (index + 1) % banners.count
            }
         }
    }
}
/// 财富市场解读向上轮播，生命周期与活跃页绑定。
struct MarketTickerCarousel: View {
    /// items：当前展示列表，页面按产品需求限制展示数量。
    /// active：页面或轮播是否允许工作，由当前Tab与前台状态决定。
    /// open：演示入口回调，只传展示标题，不在组件内执行交易。
    let items: [MarketItem]; let active: Bool; let open: (String) -> Void
    /// 当前轮播下标，按有界内容数量循环。
    @State private var index = 0
    /// 手势期间暂停自动轮播，避免与用户操作竞争。
    @GestureState private var touching = false
    /// 系统减少动态效果偏好，开启时暂停自动切换。
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 由活跃、触摸、无障碍设置及数量共同决定的轮播条件。
    private var running: Bool { active && !reduceMotion && !touching && items.count > 1 }
    /// 由只读状态组合界面，交互通过回调交给VM或宿主。
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
         // running变化会取消旧Task；减少动态效果、手势或切页都能停止计时。
         .task(id: running) {
            while running && !Task.isCancelled {
                do { try await Task.sleep(nanoseconds: 5_000_000_000) } catch { return }
                // sleep结束再次校验取消，按当前有限条目数循环下标。
                guard !Task.isCancelled, !items.isEmpty else { return }; index = (index + 1) % items.count
            }
         }
    }
}
#endif
