import Foundation

/// 四域只共享实际共用的展示值；金额保持十进制，不用浮点存储银行数值。
public struct Money: Decodable {
    public let amountMinor: String
    public let minorUnit: Int
    public let currency: String
    public var decimal: Decimal? {
        guard (0...8).contains(minorUnit), let raw = Decimal(string: amountMinor, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        return raw / pow(Decimal(10), minorUnit)
    }
    public var text: String {
        guard let decimal else { return "—" }
        let f = NumberFormatter(); f.locale = Locale(identifier: "en_US"); f.numberStyle = .decimal
        f.minimumFractionDigits = 2; f.maximumFractionDigits = 2
        return f.string(from: decimal as NSDecimalNumber) ?? "—"
    }
}
public struct MenuEntry: Decodable, Identifiable {
    public let id: String; public let title: String; public let icon: String; public let badge: String?
}
public struct Category: Decodable, Identifiable { public let id: String; public let title: String }
public struct Banner: Decodable, Identifiable {
    public let id: String; public let title: String; public let subtitle: String; public let assetKey: String
}
public struct EditorialTile: Decodable, Identifiable {
    public let id: String; public let title: String; public let subtitle: String
    public let icon: String; public let assetKey: String; public let tone: String
}
public struct EditorialSection: Decodable, Identifiable {
    public let id: String; public let title: String; public let subtitle: String; public let style: String
    public let entries: [EditorialTile]
}
public struct HomeContent: Decodable {
    public let searchPlaceholder: String; public let quickServices: [MenuEntry]; public let services: [MenuEntry]
    public let shortcuts: [MenuEntry]; public let banners: [Banner]; public let categories: [Category]
    public let sections: [EditorialSection]
}
public struct MarketItem: Decodable, Identifiable { public let id: String; public let title: String }
public struct WealthContent: Decodable {
    public let services: [MenuEntry]; public let categories: [Category]; public let marketItems: [MarketItem]
}
public struct MemberContent: Decodable { public let benefitEntries: [MenuEntry] }
public struct Product: Decodable, Identifiable {
    public let id: String; public let name: String; public let ratio: String; public let indicatorLabel: String
    public let term: String; public let minimum: String; public let riskLevel: String
    public let riskText: String; public let distribution: String
    public var percentage: String { Self.percent(ratio) }
    static func percent(_ value: String) -> String {
        guard let number = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) else { return "—" }
        return NSDecimalNumber(decimal: number * 100).stringValue + "%"
    }
}
public struct ProductPage: Decodable { public let items: [Product]; public let total: Int }
public struct AssetOverview: Decodable { public let total: Money; public let income: Money }
public struct Identity: Decodable {
    public struct Progress: Decodable {
        public let current: Money; public let nextThreshold: Money; public let isTopTier: Bool; public let asOf: String
        public var fraction: Double? {
            if isTopTier { return 1 }
            guard let x = current.decimal, let y = nextThreshold.decimal, y > 0 else { return nil }
            return min(1, max(0, NSDecimalNumber(decimal: x / y).doubleValue))
        }
    }
    public let nickname: String; public let level: Int; public let tierName: String; public let carbonLabel: String
    public let bankCards: Int; public let todos: Int; public let coupons: Int; public let points: Int; public let energy: Int
    public let tierProgress: Progress
    public var counts: [Int] { [bankCards, todos, coupons, points, energy] }
}
public struct CheckinStatus: Decodable {
    public struct Day: Decodable, Identifiable {
        public let date: String; public let checked: Bool; public var id: String { date }
    }
    public let businessDate: String; public let checkedToday: Bool; public let consecutiveDays: Int
    public let energy: Int; public let awardedEnergy: Int; public let alreadyChecked: Bool
    public let calendar: [Day]; public let rewardRule: String
}
public struct FinancialOverview: Decodable {
    public struct Bill: Decodable { public let hasCard: Bool; public let unpaid: Money?; public let dueDate: String? }
    public struct Loan: Decodable {
        public let marketingOnly: Bool; public let limit: Money; public let annualRate: String
        public var rateText: String { Product.percent(annualRate) }
    }
    public let month: String; public let monthlyIncome: Money; public let monthlyExpense: Money
    public let creditBill: Bill; public let loanOffer: Loan
    public var incomeShare: Double? {
        guard let a = monthlyIncome.decimal, let b = monthlyExpense.decimal, a + b > 0 else { return nil }
        return min(1, max(0, NSDecimalNumber(decimal: a / (a + b)).doubleValue))
    }
}
public protocol HomeRepository: AnyObject {
    func home() async throws -> HomeContent
    func unread() async throws -> Int
    func products(category: String) async throws -> ProductPage
}
public protocol WealthRepository: AnyObject {
    func wealth() async throws -> WealthContent
    func assets() async throws -> AssetOverview
    func products(category: String) async throws -> ProductPage
}
public protocol MemberRepository: AnyObject {
    func member() async throws -> MemberContent
    func identity(member: Bool) async throws -> Identity
    func checkin() async throws -> CheckinStatus
    func submit(date: String, key: String) async throws -> CheckinStatus
}
public protocol ProfileRepository: AnyObject {
    func identity(member: Bool) async throws -> Identity
    func assets() async throws -> AssetOverview
    func financial() async throws -> FinancialOverview
}
public protocol DashboardRepositorySet: HomeRepository, WealthRepository, MemberRepository, ProfileRepository {}
