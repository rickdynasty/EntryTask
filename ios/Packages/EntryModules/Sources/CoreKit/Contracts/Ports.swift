// 文件职责：跨层安全错误码，避免向JS或界面传播原始异常载荷。
import Foundation

/// 安全错误码跨层传递，原始异常和敏感载荷不进入JS或Toast。
public struct AppFailure: Error, Equatable, Sendable {
    /// 跨层安全错误码，不能替换为原始异常或敏感正文。
    public let code: String
    /// 只保存对外安全错误码，不附带底层异常或敏感正文。
    public init(_ code: String) { self.code = code }
}
public typealias JSONObject = [String: Any]
/// JSON对象解析与序列化入口，负责类型和传输体积约束。
public enum JSON {
    /// 解析JSON对象；字符串入口额外限制UTF-8字节数。
    public static func object(_ data: Data) throws -> JSONObject {
        guard let result = try JSONSerialization.jsonObject(with: data) as? JSONObject else { throw AppFailure("INVALID_REQUEST") }
        return result
    }
    /// 解析JSON对象；字符串入口额外限制UTF-8字节数。
    public static func object(_ text: String, limit: Int = 65536) throws -> JSONObject {
        guard text.utf8.count <= limit else { throw AppFailure("PAYLOAD_TOO_LARGE") }
        return try object(Data(text.utf8))
    }
    /// 将受控值编码为确定键顺序的JSON字节。
    public static func data(_ value: Any) throws -> Data { try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .fragmentsAllowed]) }
    /// 从JSON字节生成UTF-8文本，不自行打印载荷。
    public static func text(_ value: Any) throws -> String { String(decoding: try data(value), as: UTF8.self) }
}
/// 上下文由Native生成，不接受JS传入的scope/epoch作为身份权威。
public struct RequestContext: Equatable, Sendable {
    /// 会话代次；退出和认证提交后变化，旧结果不得跨代次回写。
    public let epoch: Int
    /// Native生成的用户隔离范围，未认证时为guest。
    public let scope: String
    /// 保存本次认证标志/代次或scope快照，供调用方比较身份变化。
    public init(epoch: Int, scope: String) { self.epoch = epoch; self.scope = scope }
}
/// 受控API请求描述；凭证由Native调用链管理。
public struct APIRequest {
    /// 契约清单中的API标识，不允许调用任意URL。
    public let apiID: String
    /// 单次请求关联编号，同时用于独立取消。
    public let requestID: String
    /// 白名单查询参数，不承载Native会话身份权威。
    public let params: JSONObject
    /// 原始受控请求体，仅供传输或Mock读取，不写入日志。
    public let body: JSONObject
    /// 请求超时毫秒值，客户端会限制实际范围。
    public let timeoutMS: Int
    /// 允许Native认证入口访问仅原生开放的API。
    public let nativeOnly: Bool
    /// 构造API请求描述，缺省生成requestId并使用10秒超时。
    public init(apiID: String, requestID: String = UUID().uuidString, params: JSONObject = [:], body: JSONObject = [:], timeoutMS: Int = 10000, nativeOnly: Bool = false) {
        self.apiID = apiID; self.requestID = requestID; self.params = params; self.body = body; self.timeoutMS = timeoutMS; self.nativeOnly = nativeOnly
    }
}
/// HTTP状态和原始响应字节，业务解码由上层负责。
public struct HTTPReply: Sendable {
    /// HTTP状态码，业务码仍需由响应data解码。
    public let status: Int
    /// 原始响应字节，业务层负责解码且禁止整体写日志。
    public let data: Data
    /// 构造HTTP响应值，保留状态码与原始字节供上层处理。
    public init(status: Int, data: Data) { self.status = status; self.data = data }
}
/// 业务依赖的网络契约，支持请求和独立取消。
public protocol NetworkPort: AnyObject {
    /// 根据受控API描述和Native上下文执行请求，具体传输由实现提供。
    func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply
    /// 取消指定请求或当前任务；取消与业务失败分开处理。
    func cancel(_ requestID: String)
}
/// 安装包资源读取契约，调用方负责避免主线程磁盘读取。
public protocol ResourceProvider: AnyObject, Sendable { func read(_ path: String) throws -> Data }
/// 本地模拟服务契约，输入保留Native会话上下文。
public protocol MockHandler: AnyObject, Sendable { func handle(_ request: URLRequest, context: RequestContext) async throws -> HTTPReply }

/// 只读取受控相对路径，资源来自安装包而非开发机。
public final class DirectoryResources: ResourceProvider {
    /// 安装包资源根URL，所有读取都相对该目录。
    private let root: URL
    /// 保存安装包资源根目录，后续只读取其下相对路径。
    public init(root: URL) { self.root = root }
    /// 拒绝绝对路径与父目录跳转；缺资源抛错，不回退到开发机文件。
    public func read(_ path: String) throws -> Data {
        guard !path.hasPrefix("/"), !path.split(separator: "/").contains("..") else { throw AppFailure("HOST_RESOURCE_INVALID") }
        let url = root.appendingPathComponent(path)
        guard FileManager.default.fileExists(atPath: url.path) else { throw AppFailure("HOST_RESOURCE_MISSING") }
        return try Data(contentsOf: url)
    }
}
