import Foundation
import CoreFoundation
import CoreKit

/// 受控偏好文件，调用方负责后台执行；原子替换成功后才确认写入。
public final class TypedPreferences {
    private let directory: URL
    private let lock = NSLock()
    public init(directory: URL) { self.directory = directory }
    public func perform(_ operation: String, key: String, scope: String, value: Any?) throws -> Bool {
        guard ["read", "write", "remove"].contains(operation), key == "assetVisibility", scope.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil else { throw AppFailure("STORAGE_ERROR") }
        lock.lock(); defer { lock.unlock() }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent(scope + ".json")
        if operation == "read" {
            guard FileManager.default.fileExists(atPath: url.path) else { return false }
            guard let number = try JSON.object(Data(contentsOf: url))[key] as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { throw AppFailure("STORAGE_ERROR") }
            return number.boolValue
        }
        if operation == "remove" {
            if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        } else {
            guard let number = value as? NSNumber, CFGetTypeID(number) == CFBooleanGetTypeID() else { throw AppFailure("STORAGE_ERROR") }
            try JSON.data([key: number.boolValue]).write(to: url, options: [.atomic, .completeFileProtection])
            var excluded = url; var metadata = URLResourceValues(); metadata.isExcludedFromBackup = true; try excluded.setResourceValues(metadata)
        }
        return true
    }
}
