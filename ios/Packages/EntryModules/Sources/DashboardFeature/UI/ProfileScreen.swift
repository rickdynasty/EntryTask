#if canImport(UIKit)
import SwiftUI

struct ProfileScreen: View {
    @ObservedObject var model: ProfilePageViewModel
    let visible: Bool; let toggle: () -> Void; let logout: () -> Void; let open: (String) -> Void
    var body: some View {
        BankPage(identifier: "dashboard.profile", refresh: model.refresh) {
            HStack { Button("安全退出", action: logout).font(.system(size: 12)).foregroundColor(BankStyle.ink).accessibilityIdentifier("dashboard.logout"); Spacer(); Button { open("设置") } label: { BankIcon(key: "settings", color: BankStyle.ink) }.accessibilityLabel("设置") }
            ResourceRegion(resource: model.identity, retry: model.refresh) { data in UserCardHeader(data: data, open: open) }
            ResourceRegion(resource: model.assets, retry: model.refresh) { data in AssetPrivacyCard(data: data, visible: visible, toggle: toggle) }
            ResourceRegion(resource: model.financial, retry: model.refresh) { data in
                MonthlyCashflowCard(data: data, visible: visible)
                CreditBillCard(data: data?.creditBill, visible: visible, open: open)
                LoanSummaryCard(data: data?.loanOffer, visible: visible, open: open)
            }
        }
    }
}
struct UserCardHeader: View {
    let data: Identity?; let open: (String) -> Void
    var body: some View {
        BankCard {
            HStack(spacing: 12) {
                BankArtwork(key: "profile-avatar").frame(width: 56, height: 56)
                VStack(alignment: .leading, spacing: 6) { Text(data?.nickname ?? "正在加载资料").font(.system(size: 21, weight: .semibold)); Text(data?.carbonLabel ?? "低碳").font(.system(size: 11)).foregroundColor(.green); Text("R\(data?.level.description ?? "—") \(data?.tierName ?? "")").font(.system(size: 12)).foregroundColor(BankStyle.gold) }
                Spacer()
            }
            HStack(spacing: 2) { ForEach(Array(["银行卡", "待办", "卡券", "积分", "能量值"].enumerated()), id: \.offset) { i, name in
                Button { open(name) } label: { VStack(spacing: 8) { Text(data.map { String($0.counts[i]) } ?? "—").font(.system(size: 21)); Text(name).font(.system(size: 11)) }.frame(maxWidth: .infinity, minHeight: 60) }.buttonStyle(.plain).foregroundColor(BankStyle.ink)
            } }
        }
    }
}
struct MonthlyCashflowCard: View {
    let data: FinancialOverview?; let visible: Bool
    var body: some View {
        BankCard {
            SectionHeader(title: "月度收支", subtitle: data?.month ?? "")
            HStack { VStack(alignment: .leading, spacing: 8) { MoneyText(value: data?.monthlyIncome, visible: visible); Text("收入").font(.caption) }; Spacer(); VStack(alignment: .trailing, spacing: 8) { MoneyText(value: data?.monthlyExpense, visible: visible); Text("支出").font(.caption) } }
            ProgressView(value: visible ? data?.incomeShare ?? 0 : 0).tint(BankStyle.brand).accessibilityLabel(visible ? "本地账户收支比例" : "收支比例已隐藏")
        }
    }
}
struct CreditBillCard: View {
    let data: FinancialOverview.Bill?; let visible: Bool; let open: (String) -> Void
    var body: some View {
        BankCard {
            SectionHeader(title: "信用卡账单")
            if let data {
                if data.hasCard { MoneyText(value: data.unpaid, visible: visible); Text("到期日 \(visible ? data.dueDate ?? "—" : "已隐藏")").font(.caption) }
                else { Text("尚未绑定信用卡，暂无账单").font(.system(size: 13)).foregroundColor(BankStyle.muted) }
            } else { Text("账单信息暂不可用").font(.caption) }
            Button("信用卡服务 ›") { open("信用卡服务") }.font(.caption).foregroundColor(BankStyle.gold)
        }
    }
}
struct LoanSummaryCard: View {
    let data: FinancialOverview.Loan?; let visible: Bool; let open: (String) -> Void
    var body: some View {
        BankCard {
            SectionHeader(title: "我的借钱")
            HStack { VStack(alignment: .leading, spacing: 8) { MoneyText(value: data?.limit, visible: visible); Text("营销演示上限 · 尚未授信").font(.system(size: 10)).foregroundColor(BankStyle.muted) }; Spacer(); VStack(spacing: 8) { Text(data?.rateText ?? "—").font(.system(size: 22)); Text("示例年利率").font(.system(size: 10)).foregroundColor(BankStyle.muted) } }
            Button("了解借款服务 ›") { open("借款服务") }.font(.caption).foregroundColor(BankStyle.gold)
        }
    }
}
#endif
