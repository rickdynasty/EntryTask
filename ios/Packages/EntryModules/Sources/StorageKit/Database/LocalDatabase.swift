// 文件职责：GRDB存储门面，按需打开账号库和用户演示状态库。
import Foundation
import CoreKit
import GRDB

/// 技术存储Facade：账号和演示状态分库，首次后台访问才打开，不暴露SQL给业务。
public final class LocalDatabase: @unchecked Sendable {
    /// 受控存储目录，由组合根或隔离测试注入。
    private let directory: URL
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSRecursiveLock()
    /// 账号校验值数据库连接，懒打开并在锁内访问。
    private var auth: DatabaseQueue?
    /// 用户演示状态数据库连接，与账号数据库分开。
    private var demo: DatabaseQueue?
    /// 仅保存存储目录，连接与迁移在首次后台访问时创建。
    public init(directory: URL) { self.directory = directory }
    /// 懒打开目标数据库，执行结构迁移及文件保护后缓存连接。
    private func open(auth isAuth: Bool) throws -> DatabaseQueue {
        if let existing = isAuth ? auth : demo { return existing }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var resource = directory
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try resource.setResourceValues(values)
        let path = directory.appendingPathComponent(isAuth ? "entrytask_auth.db" : "entrytask_demo.db").path
        var config = Configuration()
        #if os(iOS)
        config.prepareDatabase { db in try db.execute(sql: "PRAGMA secure_delete = ON") }
        #endif
        let queue = try DatabaseQueue(path: path, configuration: config)
        var migrator = DatabaseMigrator()
        // 首次打开按库类型创建唯一键；账号与演示状态不混用表和文件。
        migrator.registerMigration("v1") { db in
            if isAuth {
                try db.create(table: "auth_credentials") { t in
                    t.column("account_key", .text).primaryKey()
                    t.column("password_verifier", .text).notNull()
                }
            } else {
                try db.create(table: "demo_user_state") { t in
                    t.column("scope", .text).primaryKey()
                    t.column("state_json", .blob).notNull()
                }
            }
        }
        // 结构迁移成功后才缓存连接；失败直接抛给调用方，不能伪装空数据。
        try migrator.migrate(queue)
        #if os(iOS)
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: directory.path)
        // SQLite附属文件与主文件同保护，不能仅设置主db。
        for suffix in ["", "-wal", "-shm"] where FileManager.default.fileExists(atPath: path + suffix) {
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: path + suffix)
        }
        #endif
        if isAuth { auth = queue } else { demo = queue }
        return queue
    }
    /// 校验哈希账号键后读取密码校验记录，不返回明文密码。
    public func credential(_ key: String) throws -> String? {
        guard key.range(of: "^mock_auth_v1\\.[0-9a-f]{64}$", options: .regularExpression) != nil else { throw AppFailure("AUTH_STORAGE_KEY_INVALID") }
        lock.lock(); defer { lock.unlock() }
        return try open(auth: true).read { db in try String.fetchOne(db, sql: "SELECT password_verifier FROM auth_credentials WHERE account_key = ?", arguments: [key]) }
    }
    /// 以数据库唯一键防重插入，返回本次是否真正创建。
    public func insertCredential(key: String, verifier: String) throws -> Bool {
        guard key.range(of: "^mock_auth_v1\\.[0-9a-f]{64}$", options: .regularExpression) != nil, !verifier.isEmpty else { throw AppFailure("AUTH_STORAGE_KEY_INVALID") }
        lock.lock(); defer { lock.unlock() }
        return try open(auth: true).write { db in
            try db.execute(sql: "INSERT OR IGNORE INTO auth_credentials(account_key,password_verifier) VALUES (?,?)", arguments: [key, verifier])
            return db.changesCount == 1
        }
    }
    /// 事务内读取并替换整份scope状态，转换抛错时自动回滚。
    public func state(scope: String, write: Bool, transform: (JSONObject) throws -> (JSONObject, Any)) throws -> Any {
        guard scope != "guest" || !write else { throw AppFailure("AUTH_REQUIRED") }
        lock.lock(); defer { lock.unlock() }
        return try open(auth: false).write { db in
            let data = try Data.fetchOne(db, sql: "SELECT state_json FROM demo_user_state WHERE scope = ?", arguments: [scope])
            let previous = try data.map { try JSON.object($0) } ?? [:]
            // 转换发生在数据库事务内；任何抛错都会回滚，结果与持久状态一起确定。
            let (next, result) = try transform(previous)
            if write {
                let encoded = try JSON.data(next)
                guard encoded.count <= 1048576 else { throw AppFailure("PAYLOAD_TOO_LARGE") }
                try db.execute(sql: "INSERT INTO demo_user_state(scope,state_json) VALUES (?,?) ON CONFLICT(scope) DO UPDATE SET state_json=excluded.state_json", arguments: [scope, encoded])
            }
            return result
        }
    }
}
