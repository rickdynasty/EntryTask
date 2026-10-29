// 文件职责：四域展示模型、十进制金额和Repository契约；不执行网络或持久化。
import Foundation

/// 四域只共享实际共用的展示值；金额保持十进制，不用浮点存储银行数值。
public struct Money: Decodable {
    /// 最小货币单位整数的字符串表示，避免大金额精度丢失。
    public let amountMinor: String
    /// 货币小数位数，转换为Decimal时作为十进制除数。
    public let minorUnit: Int
    /// 货币代码，金额语义与单位必须一起解释。
    public let currency: String
    /// 受控小数位范围内将最小单位转换为Decimal，非法值返回空。
    public var decimal: Decimal? {
        guard (0...8).contains(minorUnit), let raw = Decimal(string: amountMinor, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        return raw / pow(Decimal(10), minorUnit)
    }
    /// 格式化为两位小数金额，缺失或非法值显示占位符。
    public var text: String {
        guard let decimal else { return "—" }
        let f = NumberFormatter(); f.locale = Locale(identifier: "en_US"); f.numberStyle = .decimal
        f.minimumFractionDigits = 2; f.maximumFractionDigits = 2
        return f.string(from: decimal as NSDecimalNumber) ?? "—"
    }
}
/// 宫格入口模型，包含标识、标题、图标和可选角标。
public struct MenuEntry: Decodable, Identifiable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// title：组件或条目的显示标题，不作为网络路由权威。
    /// icon：业务图标键，由共用组件映射到系统符号。
    /// badge：可选角标文案，空值不绘制角标。
    public let id: String; public let title: String; public let icon: String; public let badge: String?
}
/// 产品分类的稳定标识和显示标题。
public struct Category: Decodable, Identifiable { public let id: String; public let title: String }
/// 首页轮播海报数据，素材键只映射本地白名单。
public struct Banner: Decodable, Identifiable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// title：组件或条目的显示标题，不作为网络路由权威。
    /// subtitle：区域辅助说明，空文本时不占用展示位置。
    /// assetKey：共享本地素材键，不允许指定任意磁盘路径。
    public let id: String; public let title: String; public let subtitle: String; public let assetKey: String
}
/// 运营区单项展示模型，不携带可执行组件代码。
public struct EditorialTile: Decodable, Identifiable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// title：组件或条目的显示标题，不作为网络路由权威。
    /// subtitle：区域辅助说明，空文本时不占用展示位置。
    public let id: String; public let title: String; public let subtitle: String
    /// icon：业务图标键，由共用组件映射到系统符号。
    /// assetKey：共享本地素材键，不允许指定任意磁盘路径。
    /// tone：运营卡片主题键，仅映射预定义颜色。
    public let icon: String; public let assetKey: String; public let tone: String
}
/// 运营区域配置，仅支持预定义的布局样式。
public struct EditorialSection: Decodable, Identifiable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// title：组件或条目的显示标题，不作为网络路由权威。
    /// subtitle：区域辅助说明，空文本时不占用展示位置。
    /// style：已知运营布局样式，未知值不解释为组件代码。
    public let id: String; public let title: String; public let subtitle: String; public let style: String
    /// 宫格或分类的只读条目集合。
    public let entries: [EditorialTile]
}
/// 首页快捷、海报、分类和运营区域的数据集合。
public struct HomeContent: Decodable {
    /// searchPlaceholder：服务端提供的搜索框提示文本。
    /// quickServices：首页四个主要快捷入口。
    /// services：服务宫格条目，复用统一宫格组件。
    public let searchPlaceholder: String; public let quickServices: [MenuEntry]; public let services: [MenuEntry]
    /// shortcuts：首页横向快捷条目。
    /// banners：只读海报集合，素材必须来自本地白名单。
    /// categories：产品分类入口，选择后重新请求对应分类。
    public let shortcuts: [MenuEntry]; public let banners: [Banner]; public let categories: [Category]
    /// 首页运营区域集合，按固定布局类型渲染。
    public let sections: [EditorialSection]
}
/// 市场解读轮播中的单条内容。
public struct MarketItem: Decodable, Identifiable { public let id: String; public let title: String }
/// 财富页服务入口、产品分类和行情内容。
public struct WealthContent: Decodable {
    /// services：服务宫格条目，复用统一宫格组件。
    /// categories：产品分类入口，选择后重新请求对应分类。
    /// marketItems：市场解读条目，轮播只展示当前项。
    public let services: [MenuEntry]; public let categories: [Category]; public let marketItems: [MarketItem]
}
/// 会员权益入口集合。
public struct MemberContent: Decodable { public let benefitEntries: [MenuEntry] }
/// 产品展示值，保留收益口径、期限和风险提示。
public struct Product: Decodable, Identifiable {
    /// id：稳定模型标识，供列表身份或业务结果引用。
    /// name：产品显示名称，不作为稳定身份。
    /// ratio：十进制收益比例字符串，显示百分数时才乘以100。
    /// indicatorLabel：收益指标口径，不能将示例收益表述为承诺。
    public let id: String; public let name: String; public let ratio: String; public let indicatorLabel: String
    /// term：产品期限或申赎方式说明。
    /// minimum：产品起购门槛的展示文案。
    /// riskLevel：风险等级展示值。
    public let term: String; public let minimum: String; public let riskLevel: String
    /// riskText：产品风险提示，完整产品卡必须保留。
    /// distribution：自营或代销说明，供产品卡展示。
    public let riskText: String; public let distribution: String
    /// 从十进制比例计算的百分数展示文本。
    public var percentage: String { Self.percent(ratio) }
    /// 用Decimal把比例转换为百分数文本，非法比例显示占位。
    static func percent(_ value: String) -> String {
        guard let number = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")) else { return "—" }
        return NSDecimalNumber(decimal: number * 100).stringValue + "%"
    }
}
/// 产品分页结果，页面仅展示有限数量摘要。
public struct ProductPage: Decodable { public let items: [Product]; public let total: Int }
/// 资产总额与最新收益，显隐由会话内UI状态决定。
public struct AssetOverview: Decodable { public let total: Money; public let income: Money }
/// 个人及会员资料，含统计值和升级进度。
public struct Identity: Decodable {
    /// 会员升级阈值与当前资产，比例仅用于展示。
    public struct Progress: Decodable {
        /// current：会员当前月日均资产值，与升级阈值比较。
        /// nextThreshold：下一级会员资产门槛。
        /// isTopTier：是否已达最高演示等级，避免继续计算无意义升级差额。
        /// asOf：会员进度统计月份或日期口径。
        public let current: Money; public let nextThreshold: Money; public let isTopTier: Bool; public let asOf: String
        /// 展示用进度比例，钳制到0至1，不用于账务计算。
        public var fraction: Double? {
            if isTopTier { return 1 }
            guard let x = current.decimal, let y = nextThreshold.decimal, y > 0 else { return nil }
            return min(1, max(0, NSDecimalNumber(decimal: x / y).doubleValue))
        }
    }
    /// nickname：个人资料显示昵称，不作为身份隔离键。
    /// level：会员等级编号。
    /// tierName：会员等级名称。
    /// carbonLabel：个人资料的低碳标记文案。
    public let nickname: String; public let level: Int; public let tierName: String; public let carbonLabel: String
    /// bankCards：银行卡数量统计。
    /// todos：待办数量统计。
    /// coupons：已领取卡券数量统计。
    /// points：积分数量统计。
    /// energy：签到或权益累积能量值。
    public let bankCards: Int; public let todos: Int; public let coupons: Int; public let points: Int; public let energy: Int
    /// 会员升级资产进度模型。
    public let tierProgress: Progress
    /// 按银行卡、待办、卡券、积分、能量值顺序投影头部统计。
    public var counts: [Int] { [bankCards, todos, coupons, points, energy] }
}
/// 签到服务端事实，含业务日期、连续天数和日历。
public struct CheckinStatus: Decodable {
    /// 一个业务日期及该日是否已签到。
    public struct Day: Decodable, Identifiable {
        /// date：上海业务日文本，不使用设备本地日期替代。
        /// checked：此日是否已签到，由服务端事实决定。
        /// id：稳定模型标识，供列表身份或业务结果引用。
        public let date: String; public let checked: Bool; public var id: String { date }
    }
    /// businessDate：当前模拟服务的业务日期，提交时用于防止跨日误写。
    /// checkedToday：当天是否已完成签到。
    /// consecutiveDays：当前七日周期内连续签到天数。
    public let businessDate: String; public let checkedToday: Bool; public let consecutiveDays: Int
    /// energy：签到或权益累积能量值。
    /// awardedEnergy：本次提交实际增加的能量值，重复提交不重复奖励。
    /// alreadyChecked：本次提交是否命中当天已签到事实。
    public let energy: Int; public let awardedEnergy: Int; public let alreadyChecked: Bool
    /// calendar：上海时区公历，用于业务日计算。
    /// rewardRule：签到奖励规则展示文案。
    public let calendar: [Day]; public let rewardRule: String
}
/// 月度收支、信用卡账单和借款营销展示模型。
public struct FinancialOverview: Decodable {
    /// 区分无卡与有卡账单，金额及到期日允许缺省。
    public struct Bill: Decodable { public let hasCard: Bool; public let unpaid: Money?; public let dueDate: String? }
    /// 仅供营销展示的额度与利率，不代表真实授信。
    public struct Loan: Decodable {
        /// marketingOnly：标明额度仅为营销展示，仓储会校验该语义。
        /// limit：营销展示上限，不是可用授信额度。
        /// annualRate：营销示例年利率的十进制比例字符串。
        public let marketingOnly: Bool; public let limit: Money; public let annualRate: String
        /// 用于界面的营销示例百分数，不代表用户实际授信利率。
        public var rateText: String { Product.percent(annualRate) }
    }
    /// month：月度收支的统计月份。
    /// monthlyIncome：当月收入金额模型。
    /// monthlyExpense：当月支出金额模型。
    public let month: String; public let monthlyIncome: Money; public let monthlyExpense: Money
    /// creditBill：信用卡有卡状态与账单摘要。
    /// loanOffer：尚未授信的借款营销展示值。
    public let creditBill: Bill; public let loanOffer: Loan
    /// 仅用于进度条的收入占比，收支合计为零时返回空。
    public var incomeShare: Double? {
        guard let a = monthlyIncome.decimal, let b = monthlyExpense.decimal, a + b > 0 else { return nil }
        return min(1, max(0, NSDecimalNumber(decimal: a / (a + b)).doubleValue))
    }
}
/// 首页运营、未读数和产品查询契约。
public protocol HomeRepository: AnyObject {
    /// 查询首页运营内容，不在页面中读取fixture。
    func home() async throws -> HomeContent
    /// 只提取未读数量，避免将消息完整响应传给首页。
    func unread() async throws -> Int
    /// 按分类请求第一页最多三条产品。
    func products(category: String) async throws -> ProductPage
}
/// 财富页运营、资产和产品查询契约。
public protocol WealthRepository: AnyObject {
    /// 查询财富服务及行情区域内容。
    func wealth() async throws -> WealthContent
    /// 查询当前用户资产概览，隐藏逻辑留在展示层。
    func assets() async throws -> AssetOverview
    /// 按分类请求第一页最多三条产品。
    func products(category: String) async throws -> ProductPage
}
/// 会员资料、签到查询与写入契约。
public protocol MemberRepository: AnyObject {
    /// 查询会员权益展示内容。
    func member() async throws -> MemberContent
    /// 按页面选择会员或个人摘要API。
    func identity(member: Bool) async throws -> Identity
    /// 读取服务端签到事实，用于展示及未知写结果确认。
    func checkin() async throws -> CheckinStatus
    /// 校验并防重提交；结果只允许写回当前活动生命周期。
    func submit(date: String, key: String) async throws -> CheckinStatus
}
/// 我的页面资料、资产和收支查询契约。
public protocol ProfileRepository: AnyObject {
    /// 按页面选择会员或个人摘要API。
    func identity(member: Bool) async throws -> Identity
    /// 查询当前用户资产概览，隐藏逻辑留在展示层。
    func assets() async throws -> AssetOverview
    /// 查询收支/账单/营销额度，并拒绝非营销借款语义。
    func financial() async throws -> FinancialOverview
}
/// 组合根注入四域VM的仓储契约集合。
public protocol DashboardRepositorySet: HomeRepository, WealthRepository, MemberRepository, ProfileRepository {}
