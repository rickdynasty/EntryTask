// 文件职责：四域网络仓储，复用NetworkPort并校验响应所属会话。
import Foundation
import CoreKit

/// Native四页复用NetworkPort与同一Mock服务；不直接读取fixture或创建第二套账号/账本。
public final class NetworkDashboardRepository: DashboardRepositorySet {
    /// 通过契约注入的网络能力，不让View直接访问传输层。
    private let network: NetworkPort
    /// 实时读取Native上下文的闭包，请求前后各取一次校验会话。
    private let context: @Sendable () -> RequestContext
    /// 注入共享网络与实时上下文读取闭包，请求前后均校验身份。
    public init(network: NetworkPort, context: @escaping @Sendable () -> RequestContext) { self.network = network; self.context = context }
    /// 发起前拒绝guest，返回后校验上下文和取消，再解码业务data。
    private func load<T: Decodable>(_ api: String, params: JSONObject = [:], body: JSONObject = [:], requestID: String = UUID().uuidString) async throws -> T {
        let captured = context()
        guard captured.scope != "guest" else { throw AppFailure("AUTH_REQUIRED") }
        let reply = try await network.execute(APIRequest(apiID: api, requestID: requestID, params: params, body: body), context: captured)
        // 请求完成也不直接交付：先检查取消与捕获的身份，再处理业务响应。
        try Task.checkCancellation()
        guard context() == captured else { throw AppFailure("STALE_SESSION") }
        let envelope = try JSON.object(reply.data)
        guard reply.status == 200, let code = envelope["code"] as? Int, code == 200, let data = envelope["data"], !(data is NSNull) else { throw AppFailure("API_REJECTED") }
        do { return try JSONDecoder().decode(T.self, from: JSON.data(data)) }
        catch { throw AppFailure("RESPONSE_INVALID") }
    }
    /// 查询首页运营内容，不在页面中读取fixture。
    public func home() async throws -> HomeContent { try await load("home.content") }
    /// 查询财富服务及行情区域内容。
    public func wealth() async throws -> WealthContent { try await load("wealth.content") }
    /// 查询会员权益展示内容。
    public func member() async throws -> MemberContent { try await load("member.content") }
    /// 按页面选择会员或个人摘要API。
    public func identity(member: Bool) async throws -> Identity { try await load(member ? "member.summary" : "profile.summary") }
    /// 查询当前用户资产概览，隐藏逻辑留在展示层。
    public func assets() async throws -> AssetOverview { try await load("asset.summary") }
    /// 查询收支/账单/营销额度，并拒绝非营销借款语义。
    public func financial() async throws -> FinancialOverview {
        let value: FinancialOverview = try await load("profile.financialOverview")
        guard value.loanOffer.marketingOnly else { throw AppFailure("RESPONSE_INVALID") }; return value
    }
    /// 只提取未读数量，避免将消息完整响应传给首页。
    public func unread() async throws -> Int {
        /// 只解码当前页面需要的未读消息数量。
        struct Unread: Decodable { let unreadCount: Int }
        let value: Unread = try await load("message.unread"); return value.unreadCount
    }
    /// 按分类请求第一页最多三条产品。
    public func products(category: String) async throws -> ProductPage { try await load("product.list", params: ["category": category, "page": "1", "pageSize": "3"]) }
    /// 读取服务端签到事实，用于展示及未知写结果确认。
    public func checkin() async throws -> CheckinStatus { try await load("checkin.status") }
    /// 提交预期业务日期和幂等键，并复用该键关联Native请求日志。
    public func submit(date: String, key: String) async throws -> CheckinStatus {
        try await load("checkin.submit", body: ["expectedBusinessDate": date, "idempotencyKey": key], requestID: key)
    }
}
