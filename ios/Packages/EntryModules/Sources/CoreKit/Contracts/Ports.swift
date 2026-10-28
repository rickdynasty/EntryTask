import Foundation

/// 安全错误码跨层传递，原始异常和敏感载荷不进入JS或Toast。
public struct AppFailure: Error, Equatable, Sendable {
    public let code: String
    public init(_ code: String) { self.code = code }
}
public typealias JSONObject = [String: Any]
public enum JSON {
    public static func object(_ data: Data) throws -> JSONObject {
        guard let result = try JSONSerialization.jsonObject(with: data) as? JSONObject else { throw AppFailure("INVALID_REQUEST") }
        return result
    }
    public static func object(_ text: String, limit: Int = 65536) throws -> JSONObject {
        guard text.utf8.count <= limit else { throw AppFailure("PAYLOAD_TOO_LARGE") }
        return try object(Data(text.utf8))
    }
    public static func data(_ value: Any) throws -> Data { try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys, .fragmentsAllowed]) }
    public static func text(_ value: Any) throws -> String { String(decoding: try data(value), as: UTF8.self) }
}
/// 上下文由Native生成，不接受JS传入的scope/epoch作为身份权威。
public struct RequestContext: Equatable, Sendable {
    public let epoch: Int
    public let scope: String
    public init(epoch: Int, scope: String) { self.epoch = epoch; self.scope = scope }
}
public struct APIRequest {
    public let apiID: String
    public let requestID: String
    public let params: JSONObject
    public let body: JSONObject
    public let timeoutMS: Int
    public let nativeOnly: Bool
    public init(apiID: String, requestID: String = UUID().uuidString, params: JSONObject = [:], body: JSONObject = [:], timeoutMS: Int = 10000, nativeOnly: Bool = false) {
        self.apiID = apiID; self.requestID = requestID; self.params = params; self.body = body; self.timeoutMS = timeoutMS; self.nativeOnly = nativeOnly
    }
}
public struct HTTPReply: Sendable {
    public let status: Int
    public let data: Data
    public init(status: Int, data: Data) { self.status = status; self.data = data }
}
public protocol NetworkPort: AnyObject {
    func execute(_ request: APIRequest, context: RequestContext) async throws -> HTTPReply
    func cancel(_ requestID: String)
}
public protocol ResourceProvider: AnyObject, Sendable { func read(_ path: String) throws -> Data }
public protocol MockHandler: AnyObject, Sendable { func handle(_ request: URLRequest, context: RequestContext) async throws -> HTTPReply }

/// 只读取受控相对路径，资源来自安装包而非开发机。
public final class DirectoryResources: ResourceProvider {
    private let root: URL
    public init(root: URL) { self.root = root }
    public func read(_ path: String) throws -> Data {
        guard !path.hasPrefix("/"), !path.split(separator: "/").contains("..") else { throw AppFailure("HOST_RESOURCE_INVALID") }
        let url = root.appendingPathComponent(path)
        guard FileManager.default.fileExists(atPath: url.path) else { throw AppFailure("HOST_RESOURCE_MISSING") }
        return try Data(contentsOf: url)
    }
}
