import Foundation
import CoreKit

/// 模拟服务端契约，由App适配StorageKit；基础库不反向依赖DemoEngine。
public protocol MockAccountStorage: AnyObject, Sendable {
    func read(_ key: String) throws -> String?
    func create(key: String, verifier: String) throws -> Bool
}
public protocol DemoStatePort: AnyObject, Sendable {
    func transaction(context: RequestContext, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any
}
