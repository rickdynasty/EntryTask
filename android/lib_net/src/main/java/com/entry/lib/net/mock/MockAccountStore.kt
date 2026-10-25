package com.entry.lib.net.mock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 账号持久化端口；create必须原子插入，失败抛异常，重复账号返回false。 */
interface MockAccountStorage {
    fun read(key: String): String?
    fun create(key: String, verifier: String): Boolean
}

/** 演示认证账本：用户名规范化后索引，每个账号独立保存加盐校验值，不保存新明文密码。 */
class MockAccountStore(private val storage: MockAccountStorage) {
    enum class Registration { CREATED, EXISTS }

    fun register(username: String, password: String): Registration = synchronized(lock) {
        val key = accountKey(username)
        if (storage.read(key) != null) return@synchronized Registration.EXISTS
        if (storage.create(key, verifier(password))) Registration.CREATED else Registration.EXISTS
    }

    fun verify(username: String, password: String): Boolean = synchronized(lock) {
        val record = storage.read(accountKey(username)) ?: return@synchronized false
        val parts = record.split(':')
        check(parts.size == 5 && parts[0] == "v1" && parts[1] in ALGORITHMS && parts[2].toIntOrNull() == ITERATIONS) {
            "AUTH_RECORD_INVALID"
        }
        check(parts[3].length == 32 && parts[4].length == 64) { "AUTH_RECORD_INVALID" }
        val salt = unhex(parts[3])
        val expected = unhex(parts[4])
        check(salt.size == 16 && expected.size == 32) { "AUTH_RECORD_INVALID" }
        MessageDigest.isEqual(expected, derive(password, salt, parts[1]))
    }

    private fun verifier(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val algorithm = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").algorithm
        } catch (_: java.security.NoSuchAlgorithmException) {
            // API24/25的系统Provider可能只有SHA1版；记录算法，后续不依赖设备默认值校验。
            "PBKDF2WithHmacSHA1"
        }
        return "v1:$algorithm:$ITERATIONS:${hex(salt)}:${hex(derive(password, salt, algorithm))}"
    }

    private fun derive(password: String, salt: ByteArray, algorithm: String): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally { spec.clearPassword(); chars.fill('\u0000') }
    }

    companion object {
        const val ACCOUNT_KEY_PREFIX = "mock_auth_v1."
        private const val ITERATIONS = 120_000
        private val ALGORITHMS = setOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")
        private val random = SecureRandom()
        // 多个拦截器/Store实例也不能绕过注册防重的原子边界。
        private val lock = Any()
        private fun accountKey(username: String) = ACCOUNT_KEY_PREFIX + hex(
            MessageDigest.getInstance("SHA-256").digest(username.trim().toByteArray(Charsets.UTF_8)))
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        private fun unhex(value: String): ByteArray {
            check(value.length % 2 == 0) { "AUTH_RECORD_INVALID" }
            return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
