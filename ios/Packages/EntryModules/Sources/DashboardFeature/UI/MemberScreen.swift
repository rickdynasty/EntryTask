#if canImport(UIKit)
import SwiftUI

struct MemberScreen: View {
    @ObservedObject var model: MemberPageViewModel
    let visible: Bool; let open: (String) -> Void
    var body: some View {
        BankPage(identifier: "dashboard.member", refresh: model.refresh) {
            SectionHeader(title: "会员", subtitle: "活动　已领权益")
            ResourceRegion(resource: model.identity, retry: model.refresh) { data in
                BankCard(color: Color(red: 0.06, green: 0.58, blue: 0.75)) {
                    VipProfileCard(data: data, visible: visible, open: open)
                    ResourceRegion(resource: model.content, retry: model.refresh) { data in
                        BankCard { StandardGridMenu(entries: data?.benefitEntries ?? [], columns: 4, blue: true, open: open) }
                    }
                    Button { open("会员升级攻略") } label: { HStack { BankIcon(key: "crown"); Text("会员升级攻略").font(.system(size: 15)); Spacer(); Text("去升级 ›").foregroundColor(BankStyle.brand) }.padding(12).background(Color.white).cornerRadius(8) }.buttonStyle(.plain).foregroundColor(BankStyle.ink)
                }
            }
            SectionHeader(title: "会员专属活动")
            ResourceRegion(resource: model.checkin, retry: model.refresh) { data in
                InteractiveTaskCard(data: data, submitting: model.submitting, message: model.message, submit: model.submit)
            }
        }
    }
}
struct VipProfileCard: View {
    let data: Identity?; let visible: Bool; let open: (String) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack { BankIcon(key: "crown", color: Color(red: 1, green: 0.91, blue: 0.65), size: 38); Text("R\(data?.level.description ?? "—") \(data?.tierName ?? "中信红会员")").font(.system(size: 22, weight: .semibold)); Spacer(); Button("全部权益 ›") { open("全部权益") }.font(.system(size: 12)) }
            ProgressView(value: visible ? data?.tierProgress.fraction ?? 0 : 0).tint(Color.yellow).accessibilityLabel(visible ? "会员升级进度" : "会员升级进度已隐藏")
            HStack { Text("当前月日均金融资产").font(.system(size: 12)); Spacer(); Text(visible ? data?.tierProgress.current.text ?? "—" : "******").font(.system(size: 14)) }
            if data?.tierProgress.isTopTier == true { Text("已达最高演示等级").font(.caption) }
        }.foregroundColor(Color(red: 1, green: 0.94, blue: 0.75))
    }
}
struct InteractiveTaskCard: View {
    let data: CheckinStatus?; let submitting: Bool; let message: String; let submit: () -> Void
    var body: some View {
        BankCard(color: BankStyle.cream) {
            HStack {
                Text("连续签到赢好礼，已连签\(data?.consecutiveDays.description ?? "—")天").font(.system(size: 13, weight: .semibold))
                Spacer(minLength: 4)
                Button(submitting ? "提交中" : data?.checkedToday == true ? "今日已签到" : "立即签到", action: submit)
                    .font(.system(size: 12)).padding(10).foregroundColor(.white).background(BankStyle.brand).cornerRadius(3)
                    .disabled(data == nil || submitting || data?.checkedToday == true).accessibilityIdentifier("dashboard.checkin")
            }
            Text(data?.rewardRule ?? "正在加载签到规则").font(.system(size: 11)).foregroundColor(BankStyle.muted)
            HStack(spacing: 4) { ForEach(data?.calendar ?? []) { day in
                VStack(spacing: 10) { Text(day.checked ? "已签" : "空").font(.system(size: 12)); Text(String(day.date.suffix(5))).font(.system(size: 9)) }.frame(maxWidth: .infinity).padding(.vertical, 12).background(Color.white).foregroundColor(BankStyle.gold).accessibilityElement(children: .ignore).accessibilityLabel("\(day.date)，\(day.checked ? "已签到" : "未签到")")
            } }
            if !message.isEmpty { Text(message).font(.caption).foregroundColor(BankStyle.muted).accessibilityIdentifier("dashboard.checkin.message") }
        }
    }
}
#endif
