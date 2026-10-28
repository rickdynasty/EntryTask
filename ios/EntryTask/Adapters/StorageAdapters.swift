import Foundation
import CoreKit
import UserFeature
import DemoEngine
import StorageKit

/// App接线：DemoEngine不依赖数据库实现；所有写入与会话变化共用提交队列。
final class AccountStorageAdapter: MockAccountStorage {
    private let database: LocalDatabase
    init(_ database: LocalDatabase) { self.database = database }
    func read(_ key: String) throws -> String? { try database.credential(key) }
    func create(key: String, verifier: String) throws -> Bool { try database.insertCredential(key: key, verifier: verifier) }
}
final class DemoStateAdapter: DemoStatePort {
    private let database: LocalDatabase
    private let session: SessionPort
    private let commits: CommitExecutor
    init(database: LocalDatabase, session: SessionPort, commits: CommitExecutor) { self.database = database; self.session = session; self.commits = commits }
    func transaction(context: RequestContext, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any {
        try commits.sync {
            guard session.requestContext() == context else { throw AppFailure("STALE_SESSION") }
            return try database.state(scope: context.scope, write: write, transform: transform)
        }
    }
}
