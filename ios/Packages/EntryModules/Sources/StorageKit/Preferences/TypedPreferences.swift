// 文件职责：按用户隔离的受控布尔偏好存储，不接收任意凭证键。
import Foundation
import CoreFoundation
import CoreKit

/// 受控偏好文件，调用方负责后台执行；原子替换成功后才确认写入。
public final class TypedPreferences {
    /// 受控存储目录，由组合根或隔离测试注入。
    private let directory: URL
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 注入受控偏好目录，不提前执行磁盘读写。
    public init(directory: URL) { self.directory = directory }
    /// 只允许assetVisibility布尔键及哈希scope，原子写入或读取默认隐藏值。
    public func perform(_ operation: String, key: String, scope: String, value: Any?) throws -> Bool {
        guard ["read", "write", "remove"].contains(operation), key == "assetVisibility", scope.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else { throw AppFailure("STORAGE_ERROR") }
        lock.lock(); defer { lock.unlock() }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent(scope + ".json")
        if operation == "read" {
            guard FileManager.default.fileExists(atPath: url.path) else { return false }
            // NSNumber必须确实是CFBoolean，数字1不能冒充布尔值。
            guard let number = try JSON.object(Data(contentsOf: url))[key] as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { throw AppFailure("STORAGE_ERROR") }
            return number.boolValue
        }
        if operation == "remove" {
            if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        } else {
            guard let number = value as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { throw AppFailure("STORAGE_ERROR") }
            // 原子替换成功后才返回；文件启用数据保护并排除系统备份。
            try JSON.data([key: number.boolValue]).write(to: url, options: [.atomic, .completeFileProtection])
            var excluded = url; var metadata = URLResourceValues(); metadata.isExcludedFromBackup = true; try excluded.setResourceValues(metadata)
        }
        return true
    }
}
