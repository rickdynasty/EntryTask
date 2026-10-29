// 文件职责：模拟服务端账号库，保存随机盐和密码派生校验值。
import Foundation
import CoreKit
import CommonCrypto
import Security

/// 用户名索引/校验值与Android格式对等；密码原文不落盘、不进入日志。
public final class MockAccountStore: Sendable {
    /// 模拟账号持久化契约，不直接依赖StorageKit。
    private let storage: MockAccountStorage
    /// 保护本类型短同步共享状态；持锁区内不跨await。
    private let lock = NSLock()
    /// 只依赖账号存储契约，密码派生与校验由本模拟服务负责。
    public init(storage: MockAccountStorage) { self.storage = storage }
    /// 仅裁剪用户名两端兼容空白，密码不做同样归一化。
    public static func normalize(_ username: String) -> String {
        InputText.trim(username)
    }
    /// 计算UTF-8文本的SHA-256，用于不含明文用户名的索引。
    public static func hash(_ text: String) -> String {
        let data = Data(text.utf8)
        var result = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &result) }
        return hex(result)
    }
    /// 把原始字节转为固定两位小写十六进制。
    private static func hex(_ bytes: [UInt8]) -> String { bytes.map { String(format: "%02x", $0) }.joined() }
    /// 校验偶数字符与十六进制格式，还原持久化盐和校验值。
    private static func unhex(_ value: String) throws -> [UInt8] {
        guard value.count % 2 == 0 else { throw AppFailure("AUTH_RECORD_INVALID") }
        var result: [UInt8] = []; var index = value.startIndex
        while index < value.endIndex {
            let end = value.index(index, offsetBy: 2)
            guard let byte = UInt8(value[index..<end], radix: 16) else { throw AppFailure("AUTH_RECORD_INVALID") }
            result.append(byte); index = end
        }
        return result
    }
    /// 使用固定120000轮PBKDF2派生32字节校验值，调用方在后台执行。
    public static func derive(password: String, salt: [UInt8], algorithm: String = "PBKDF2WithHmacSHA256") throws -> [UInt8] {
        guard ["PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1"].contains(algorithm) else { throw AppFailure("AUTH_RECORD_INVALID") }
        var output = [UInt8](repeating: 0, count: 32)
        let bytes = Array(password.utf8)
        let status = bytes.withUnsafeBytes { pass in salt.withUnsafeBytes { salt in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pass.baseAddress?.assumingMemoryBound(to: Int8.self), bytes.count,
                salt.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count,
                CCPseudoRandomAlgorithm(algorithm == "PBKDF2WithHmacSHA256" ? kCCPRFHmacAlgSHA256 : kCCPRFHmacAlgSHA1), 120000, &output, output.count)
        } }
        guard status == kCCSuccess else { throw AppFailure("AUTH_RECORD_INVALID") }
        return output
    }
    /// 加锁检查账号唯一性，生成随机盐并保存版本化密码校验记录。
    public func register(username: String, password: String) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        let key = "mock_auth_v1." + Self.hash(Self.normalize(username))
        // 读写过程受同一锁保护；数据库唯一键再兜底，重复注册不能覆盖原密码。
        if try storage.read(key) != nil { return false }
        var salt = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, salt.count, &salt) == errSecSuccess else { throw AppFailure("AUTH_STORAGE_ERROR") }
        // 保存版本、算法、迭代次数、随机盐及派生值，绝不保存密码原文。
        let verifier = "v1:PBKDF2WithHmacSHA256:120000:" + Self.hex(salt) + ":" + Self.hex(try Self.derive(password: password, salt: salt))
        return try storage.create(key: key, verifier: verifier)
    }
    /// 校验记录格式并重新派生，逐字节异或汇总后判断密码是否一致。
    public func verify(username: String, password: String) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard let record = try storage.read("mock_auth_v1." + Self.hash(Self.normalize(username))) else { return false }
        let parts = record.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 5, parts[0] == "v1", parts[2] == "120000", parts[3].count == 32, parts[4].count == 64 else { throw AppFailure("AUTH_RECORD_INVALID") }
        let expected = try Self.unhex(parts[4]); let actual = try Self.derive(password: password, salt: Self.unhex(parts[3]), algorithm: parts[1])
        // 对固定长度字节逐项汇总差异，不在首个不同字节提前返回。
        return zip(expected, actual).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }
}
