// 文件职责：账号读取与唯一创建契约，由App适配具体数据库。
import Foundation
import CoreKit

/// 模拟服务端契约，由App适配StorageKit；基础库不反向依赖DemoEngine。
public protocol MockAccountStorage: AnyObject, Sendable {
    /// 按哈希账号键读取密码校验记录，未注册时返回空。
    func read(_ key: String) throws -> String?
    /// 唯一插入账号校验值，已有记录不覆盖。
    func create(key: String, verifier: String) throws -> Bool
}
/// 用户演示状态的事务契约，不让Mock依赖数据库实现。
public protocol DemoStatePort: AnyObject, Sendable {
    /// 在串行提交边界检查会话，再原子转换用户隔离状态。
    func transaction(context: RequestContext, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any
}
