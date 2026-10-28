import Foundation
import CoreKit
import CommonCrypto
import Security

/// 用户名索引/校验值与Android格式对等；密码原文不落盘、不进入日志。
public final class MockAccountStore: Sendable {
    private let storage: MockAccountStorage
    private let lock = NSLock()
    public init(storage: MockAccountStorage) { self.storage = storage }
    public static func normalize(_ username: String) -> String {
        InputText.trim(username)
    }
    public static func hash(_ text: String) -> String {
        let data = Data(text.utf8)
        var result = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &result) }
        return hex(result)
    }
    private static func hex(_ bytes: [UInt8]) -> String { bytes.map { String(format: "%02x", $0) }.joined() }
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
    public func register(username: String, password: String) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        let key = "mock_auth_v1." + Self.hash(Self.normalize(username))
        if try storage.read(key) != nil { return false }
        var salt = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, salt.count, &salt) == errSecSuccess else { throw AppFailure("AUTH_STORAGE_ERROR") }
        let verifier = "v1:PBKDF2WithHmacSHA256:120000:" + Self.hex(salt) + ":" + Self.hex(try Self.derive(password: password, salt: salt))
        return try storage.create(key: key, verifier: verifier)
    }
    public func verify(username: String, password: String) throws -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard let record = try storage.read("mock_auth_v1." + Self.hash(Self.normalize(username))) else { return false }
        let parts = record.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 5, parts[0] == "v1", parts[2] == "120000", parts[3].count == 32, parts[4].count == 64 else { throw AppFailure("AUTH_RECORD_INVALID") }
        let expected = try Self.unhex(parts[4]); let actual = try Self.derive(password: password, salt: Self.unhex(parts[3]), algorithm: parts[1])
        return zip(expected, actual).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }
}
