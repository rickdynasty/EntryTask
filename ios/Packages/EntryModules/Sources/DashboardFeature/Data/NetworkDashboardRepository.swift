import Foundation
import CoreKit

/// Native四页复用NetworkPort与同一Mock服务；不直接读取fixture或创建第二套账号/账本。
public final class NetworkDashboardRepository: DashboardRepositorySet {
    private let network: NetworkPort
    private let context: @Sendable () -> RequestContext
    public init(network: NetworkPort, context: @escaping @Sendable () -> RequestContext) { self.network = network; self.context = context }
    private func load<T: Decodable>(_ api: String, params: JSONObject = [:], body: JSONObject = [:], requestID: String = UUID().uuidString) async throws -> T {
        let captured = context()
        guard captured.scope != "guest" else { throw AppFailure("AUTH_REQUIRED") }
        let reply = try await network.execute(APIRequest(apiID: api, requestID: requestID, params: params, body: body), context: captured)
        try Task.checkCancellation()
        guard context() == captured else { throw AppFailure("STALE_SESSION") }
        let envelope = try JSON.object(reply.data)
        guard reply.status == 200, let code = envelope["code"] as? Int, code == 200, let data = envelope["data"], !(data is NSNull) else { throw AppFailure("API_REJECTED") }
        do { return try JSONDecoder().decode(T.self, from: JSON.data(data)) }
        catch { throw AppFailure("RESPONSE_INVALID") }
    }
    public func home() async throws -> HomeContent { try await load("home.content") }
    public func wealth() async throws -> WealthContent { try await load("wealth.content") }
    public func member() async throws -> MemberContent { try await load("member.content") }
    public func identity(member: Bool) async throws -> Identity { try await load(member ? "member.summary" : "profile.summary") }
    public func assets() async throws -> AssetOverview { try await load("asset.summary") }
    public func financial() async throws -> FinancialOverview {
        let value: FinancialOverview = try await load("profile.financialOverview")
        guard value.loanOffer.marketingOnly else { throw AppFailure("RESPONSE_INVALID") }; return value
    }
    public func unread() async throws -> Int {
        struct Unread: Decodable { let unreadCount: Int }
        let value: Unread = try await load("message.unread"); return value.unreadCount
    }
    public func products(category: String) async throws -> ProductPage { try await load("product.list", params: ["category": category, "page": "1", "pageSize": "3"]) }
    public func checkin() async throws -> CheckinStatus { try await load("checkin.status") }
    public func submit(date: String, key: String) async throws -> CheckinStatus {
        try await load("checkin.submit", body: ["expectedBusinessDate": date, "idempotencyKey": key], requestID: key)
    }
}
