// 文件职责：Mock内部HTTP与业务错误描述，在响应边界统一转换。
import Foundation
import CoreKit

/// Mock内部HTTP与业务错误描述，在响应边界统一转换。
private struct BusinessFailure: Error { let code: String; let status: Int }

/// 完整manifest分派，只有Native网络能进入；fixture与状态事实各有权威。
public final class DemoHandler: MockHandler {
    /// 安装包资源提供者，运行时不访问开发机文件。
    private let resources: ResourceProvider
    /// 模拟服务端账号处理器，负责注册写入和密码校验。
    private let accounts: MockAccountStore
    /// 当前可观察UI状态或模拟账本，不允许跨会话迟到写回。
    private let state: DemoStatePort
    /// 可注入时钟，测试可以固定跨日边界。
    private let clock: @Sendable () -> Date
    /// 可注入延迟，普通演示请求默认随机1至3秒。
    private let delayMS: @Sendable () -> UInt64
    /// Mock后台并发队列，承载同步KDF和业务处理。
    private let worker = DispatchQueue(label: "EntryTask.Mock", qos: .utility, attributes: .concurrent)
    /// 注入资源、账号、状态、时钟和延迟，测试可控制时间而业务无需改动。
    public init(resources: ResourceProvider, accounts: MockAccountStore, state: DemoStatePort, clock: @escaping @Sendable () -> Date = { Date() }, delayMS: @escaping @Sendable () -> UInt64 = { UInt64.random(in: 1000...3000) }) {
        self.resources = resources; self.accounts = accounts; self.state = state; self.clock = clock; self.delayMS = delayMS
    }
    /// 从Bundle中的mock目录读取并解析指定JSON对象。
    private func file(_ path: String) throws -> JSONObject { try JSON.object(resources.read("mock/" + path)) }
    /// 读取manifest路由清单，结构非法时拒绝继续。
    private func routes() throws -> [JSONObject] {
        guard let items = try file("manifest.json")["routes"] as? [JSONObject] else { throw AppFailure("MOCK_DATA_INVALID") }; return items
    }
    /// 按API查找对应响应fixture，并提取其data模板。
    private func source(_ id: String) throws -> Any {
        guard let path = try routes().first(where: { $0["apiId"] as? String == id })?["response"] as? String, let data = try file(path)["data"] else { throw AppFailure("MOCK_DATA_INVALID") }; return data
    }
    /// 从集合型响应模板中读取items，供分页与详情复用。
    private func rows(_ id: String) throws -> [JSONObject] { (try source(id) as? JSONObject)?["items"] as? [JSONObject] ?? [] }
    /// 用注入时钟生成上海时区业务日期，不依赖设备当前时区。
    private func today() -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "Asia/Shanghai"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: clock())
    }
    /// 可取消等待模拟延迟，再把同步业务处理派发到后台队列。
    public func handle(_ request: URLRequest, context: RequestContext) async throws -> HTTPReply {
        let flag = CancellationState()
        return try await withTaskCancellationHandler(operation: {
            // 延迟可被任务取消，不阻塞主线程；同步KDF和事务随后交给后台worker。
            try await Task.sleep(nanoseconds: delayMS() * 1000000)
            try Task.checkCancellation()
            return try await withCheckedThrowingContinuation { continuation in
                worker.async {
                    do { try flag.check(); continuation.resume(returning: try self.process(request, context: context, flag: flag)) }
                    catch { continuation.resume(throwing: error) }
                }
            }
        }, onCancel: { flag.cancel() })
    }
    /// 校验方法/路径/暴露级别，执行认证或状态事务并统一封装错误。
    private func process(_ request: URLRequest, context: RequestContext, flag: CancellationState) throws -> HTTPReply {
        let trace = request.value(forHTTPHeaderField: "X-Request-Id") ?? UUID().uuidString
        var status = 200; var reply: JSONObject
        var auth = false
        do {
            let api = request.value(forHTTPHeaderField: "X-Demo-Api") ?? ""
            guard let route = try routes().first(where: { route in
                guard route["apiId"] as? String == api, route["method"] as? String == request.httpMethod, let pattern = route["path"] as? String else { return false }
                return request.url?.path.range(of: "^" + pattern.replacingOccurrences(of: "{id}", with: "[A-Za-z0-9_-]+") + "$", options: .regularExpression) != nil
            }) else { throw BusinessFailure(code: "MOCK_NOT_FOUND", status: 404) }
            let handler = route["handler"] as? String ?? ""
            auth = ["login", "register"].contains(handler)
            guard route["exposure"] as? String == "jsAllowed" || request.value(forHTTPHeaderField: "X-Demo-Native") == "true" else { throw BusinessFailure(code: "ENDPOINT_NOT_ALLOWED", status: 403) }
            if auth { reply = try authenticate(request, route: route, registration: handler == "register", flag: flag) }
            else {
                if route["requiresAuth"] as? Bool == true && context.scope == "guest" { throw BusinessFailure(code: "AUTH_REQUIRED", status: 401) }
                // 用户状态按scope在事务中读取与更新；只在POST时提交替换后的账本。
                let data = try state.transaction(context: context, write: request.httpMethod == "POST") { previous in
                    try flag.check()
                    var current = previous
                    let result = try self.dispatch(route, request: request, state: &current)
                    try flag.check(); return (current, result)
                }
                reply = ["code": 200, "message": "success", "data": data]
            }
            try flag.check()
            AppLogger.log("mock.completed", ["apiId": api, "requestId": trace])
        } catch is CancellationError { throw CancellationError() }
          catch let error as BusinessFailure { status = error.status; reply = ["code": error.code, "message": error.code, "data": NSNull()] }
          catch let error as AppFailure {
            if ["STALE_SESSION", "CANCELLED"].contains(error.code) { throw error }
            status = 500; reply = ["code": auth ? 500 : "MOCK_DATA_INVALID", "message": "MOCK_DATA_INVALID", "data": NSNull()]
            AppLogger.log("mock.failed", ["code": error.code, "requestId": trace], error: true)
        } catch { status = 500; reply = ["code": auth ? 500 : "MOCK_DATA_INVALID", "message": "MOCK_DATA_INVALID", "data": NSNull()] }
        // 认证沿用当前演示契约：HTTP为200，登录/注册失败在数值业务码中表达。
        if auth { status = 200 }
        reply["traceId"] = trace
        return HTTPReply(status: status, data: try JSON.data(reply))
    }
    /// 经认证契约发起登录或注册；网络和会话提交由实现负责。
    private func authenticate(_ request: URLRequest, route: JSONObject, registration: Bool, flag: CancellationState) throws -> JSONObject {
        let body = request.httpBody ?? Data()
        let input: JSONObject
        if request.value(forHTTPHeaderField: "Content-Type")?.hasPrefix("application/x-www-form-urlencoded") == true {
            let query = String(decoding: body, as: UTF8.self).replacingOccurrences(of: "+", with: "%20")
            input = Dictionary((URLComponents(string: "https://local/?" + query)?.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, last in last })
        } else { input = try JSON.object(body) }
        let username = MockAccountStore.normalize(input["username"] as? String ?? "")
        let password = input["password"] as? String ?? ""
        let path: String; let code: Int?
        if username.isEmpty || InputText.trim(password).isEmpty || username.utf16.count > 256 || password.utf16.count > 1024 {
            path = "errors/VALIDATION_ERROR.json"; code = 400
        // 注册落库属于Mock服务端行为，未来接真实后台时无需UI自行写账号库。
        } else if registration {
            try flag.check()
            let created = try accounts.register(username: username, password: password)
            path = created ? route["response"] as! String : "errors/ACCOUNT_EXISTS.json"; code = created ? nil : 409
        } else {
            let valid = try accounts.verify(username: username, password: password)
            path = valid ? route["response"] as! String : "errors/INVALID_CREDENTIALS.json"; code = valid ? nil : 401
        }
        var reply = try file(path)
        if let code { reply["code"] = code }
        else { var user = reply["data"] as? JSONObject ?? [:]; user["username"] = username; reply["data"] = user }
        return reply
    }
    /// 按已知handler处理分页、签到、权益、消息与搜索，不执行任意脚本。
    private func dispatch(_ route: JSONObject, request: URLRequest, state: inout JSONObject) throws -> Any {
        let handler = route["handler"] as? String ?? ""
        let api = route["apiId"] as? String ?? ""
        let raw = try source(api)
        var data = raw as? JSONObject ?? [:]
        let query = Dictionary((URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems ?? []).map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, last in last })
        let id = request.url!.pathComponents.last(where: { !["claims", "read"].contains($0) }) ?? ""
        /// 在当前集合查找目标id，缺失时返回资源不存在。
        func find(_ items: [JSONObject]) throws -> JSONObject {
            guard let item = items.first(where: { $0["id"] as? String == id }) else { throw BusinessFailure(code: "RESOURCE_NOT_FOUND", status: 404) }; return item
        }
        /// 校验页码与每页上限，安全截取列表并计算是否还有下一页。
        func page(_ items: [JSONObject]) throws -> JSONObject {
            let p = Int(query["page"] ?? "1") ?? 1; let size = Int(query["pageSize"] ?? "10") ?? 10
            guard p > 0, (1...50).contains(size), p <= Int.max / size else { throw BusinessFailure(code: "VALIDATION_ERROR", status: 400) }
            let offset = (p - 1) * size
            return ["items": Array(items.dropFirst(min(offset, items.count)).prefix(size)), "page": p, "pageSize": size, "total": items.count, "hasMore": offset + size < items.count]
        }
        /// 把已领取事实投影到权益模板，不修改共享fixture。
        func benefits() throws -> [JSONObject] {
            let ids = Set((state["claims"] as? [JSONObject] ?? []).compactMap { $0["benefitId"] as? String })
            return try rows("benefit.list").map { item in var item = item; if ids.contains(item["id"] as? String ?? "") { item["status"] = "claimed" }; return item }
        }
        // 演示账户从空资产事实开始，不能把运营fixture里的示例资产归给新账号。
        if api == "account.list" { return try page([]) }
        switch handler {
        case "fixture": return raw
        case "collection": return try page(data["items"] as? [JSONObject] ?? [])
        case "products":
            let category = query["category"]
            return try page(rows("product.list").filter { category == nil || category == "all" || (category == "hot" && $0["category"] as? String == "fund") || $0["category"] as? String == category })
        case "productDetail": return try find(rows("product.list"))
        case "articleDetail": return try find(rows("article.list"))
        case "activityDetail": return try find(rows("activity.list"))
        case "assets":
            for key in ["total", "liabilities", "net", "income"] { var value = data[key] as? JSONObject ?? [:]; value["amountMinor"] = "0"; data[key] = value }
            data["categories"] = (data["categories"] as? [JSONObject] ?? []).map { item in var item = item; item["amountMinor"] = "0"; return item }; return data
        case "summary":
            data["bankCards"] = 0; data["todos"] = 0; data["points"] = 0; data["coupons"] = (state["claims"] as? [JSONObject] ?? []).count; data["energy"] = state["energy"] as? Int ?? 0
            if var tier = data["tierProgress"] as? JSONObject { tier["asOf"] = String(today().prefix(7)); data["tierProgress"] = tier }; return data
        case "financialOverview": data["month"] = String(today().prefix(7)); return data
        case "accountDetail", "transactionDetail", "itemDetail": throw BusinessFailure(code: "RESOURCE_NOT_FOUND", status: 404)
        case "transactions", "items": return try page([])
        case "checkin": return try CheckinLedger(today: today(), template: data).snapshot(state)
        // 写入前校验业务日期及幂等键格式；实际重复签到由日期账本保证不重复奖励。
        case "checkinSubmit":
            let body = try JSON.object(request.httpBody ?? Data()); let expected = body["expectedBusinessDate"] as? String ?? ""
            guard expected == today() else { throw BusinessFailure(code: "BUSINESS_DATE_CHANGED", status: 409) }
            guard let key = body["idempotencyKey"] as? String, key.range(of: "^[A-Za-z0-9-]{1,100}$", options: .regularExpression) != nil else { throw BusinessFailure(code: "VALIDATION_ERROR", status: 400) }
            return try CheckinLedger(today: today(), template: source("checkin.status") as? JSONObject ?? [:]).submit(&state, expectedDate: expected)
        case "benefits": return try page(benefits())
        case "benefitDetail": return try find(benefits())
        // 先查询已有领取事实，重复领取返回同一记录，避免重复发券。
        case "claim":
            let benefit = try find(benefits()); var claims = state["claims"] as? [JSONObject] ?? []
            if let found = claims.first(where: { $0["benefitId"] as? String == id }) { return found }
            guard benefit["status"] as? String == "available" else { throw BusinessFailure(code: (benefit["stock"] as? Int ?? 0) == 0 ? "BENEFIT_SOLD_OUT" : "BENEFIT_NOT_ELIGIBLE", status: 403) }
            let claim: JSONObject = ["id": "claim-" + id, "benefitId": id, "title": benefit["title"] ?? "", "date": today(), "status": "已领取", "paragraphs": benefit["paragraphs"] ?? []]
            claims.append(claim); state["claims"] = claims; return claim
        case "claims": return try page(state["claims"] as? [JSONObject] ?? [])
        case "claimDetail": return try find(state["claims"] as? [JSONObject] ?? [])
        case "energy": var result = try page(state["energyRecords"] as? [JSONObject] ?? []); result["balance"] = state["energy"] as? Int ?? 0; return result
        // 已读集合来自当前用户账本；共享消息模板始终保持只读。
        case "messages", "messageDetail", "messageRead", "unread":
            var read = Set(state["readIds"] as? [String] ?? []); var messages = try rows("message.list")
            if handler == "messageRead" { _ = try find(messages); read.insert(id); state["readIds"] = read.sorted() }
            messages = messages.map { item in var item = item; item["isRead"] = read.contains(item["id"] as? String ?? ""); return item }
            if handler == "messages" { return try page(messages) }
            if handler == "messageDetail" { return try find(messages) }
            return ["isRead": true, "unreadCount": messages.filter { $0["isRead"] as? Bool != true }.count]
        case "search":
            let word = (query["keyword"] ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let scope = query["scope"] ?? "services"; var items: [JSONObject]
            switch scope {
            case "products": items = try rows("product.list").map { ["id": $0["id"] ?? "", "title": $0["name"] ?? "", "summary": $0["riskText"] ?? "", "target": ["kind": "product", "title": $0["name"] ?? "", "id": $0["id"] ?? "", "requiresAuth": false]] }
            case "articles", "activities": items = try rows(scope == "articles" ? "article.list" : "activity.list").map { item in var item = item; item["target"] = ["kind": scope == "articles" ? "article" : "activity", "title": item["title"] ?? "", "id": item["id"] ?? "", "requiresAuth": false]; return item }
            default: items = try rows("service.catalog")
            }
            return try page(items.filter { !word.isEmpty && ($0["title"] as? String ?? "").range(of: word, options: .caseInsensitive) != nil })
        default: throw AppFailure("MOCK_DATA_INVALID")
        }
    }
}
