// 文件职责：把Mock账号存储契约适配为数据库调用。
import Foundation
import CoreKit
import UserFeature
import DemoEngine
import StorageKit

/// App接线：DemoEngine不依赖数据库实现；所有写入与会话变化共用提交队列。
final class AccountStorageAdapter: MockAccountStorage {
    /// 账号和演示账本的数据库门面，首次访问才打开库。
    private let database: LocalDatabase
    /// 注入已有数据库门面，不在适配器中创建另一份账号库。
    init(_ database: LocalDatabase) { self.database = database }
    /// 按哈希账号键读取密码校验记录，未注册时返回空。
    func read(_ key: String) throws -> String? { try database.credential(key) }
    /// 唯一插入账号校验值，已有记录不覆盖。
    func create(key: String, verifier: String) throws -> Bool { try database.insertCredential(key: key, verifier: verifier) }
}
/// 把用户演示状态事务与会话提交队列连接起来。
final class DemoStateAdapter: DemoStatePort {
    /// 账号和演示账本的数据库门面，首次访问才打开库。
    private let database: LocalDatabase
    /// Native会话权威的引用，身份不由页面自行指定。
    private let session: SessionPort
    /// 会话与写事务共用的串行提交协调器。
    private let commits: CommitExecutor
    /// 保存调用方传入的值或依赖，不在构造期间发起业务请求。
    init(database: LocalDatabase, session: SessionPort, commits: CommitExecutor) { self.database = database; self.session = session; self.commits = commits }
    /// 进入共同提交队列后重新核对epoch/scope，再调用数据库事务。
    func transaction(context: RequestContext, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any {
        try commits.sync {
            guard session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
            return try database.state(scope: context.scope, write: write, transform: transform)
        }
    }
}
