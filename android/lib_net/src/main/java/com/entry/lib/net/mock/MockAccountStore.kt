package com.entry.lib.net.mock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Mock账户的持久化端口；注册提交成功后才允许返回成功，测试可隔离真实SP。 */
interface MockAccountStorage {
    fun read(key: String): String?
    fun commit(values: Map<String, String>, removals: Set<String>): Boolean
}

/** 演示认证账本：用户名规范化后索引，每个账号独立保存加盐校验值，不保存新明文密码。 */
class MockAccountStore(private val storage: MockAccountStorage) {
    enum class Registration { CREATED, EXISTS }

    fun register(username: String, password: String): Registration = synchronized(lock) {
        migrateLegacy()
        val key = accountKey(username)
        if (storage.read(key) != null) return@synchronized Registration.EXISTS
        persist(mapOf(key to verifier(password)))
        Registration.CREATED
    }

    fun verify(username: String, password: String): Boolean = synchronized(lock) {
        migrateLegacy()
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

    /** 保留两版既有单账户；同名时现用mock_reg优先，提交成功后原子移除旧明文键。 */
    private fun migrateLegacy() {
        val values = linkedMapOf<String, String>()
        val removals = linkedSetOf<String>()
        for (prefix in listOf("mock_reg", "mock_db")) {
            val username = storage.read("${prefix}_username") ?: continue
            val password = storage.read("${prefix}_password") ?: continue
            if (username.isBlank() || password.isBlank()) continue
            val key = accountKey(username)
            if (storage.read(key) == null && key !in values) values[key] = verifier(password)
            removals += "${prefix}_username"
            removals += "${prefix}_password"
        }
        if (removals.isNotEmpty()) persist(values, removals)
    }

    private fun persist(values: Map<String, String>, removals: Set<String> = emptySet()) {
        check(storage.commit(values, removals)) { "AUTH_STORAGE_WRITE_FAILED" }
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
        private const val ITERATIONS = 120_000
        private val ALGORITHMS = setOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")
        private val random = SecureRandom()
        // 多个拦截器/Store实例也不能绕过注册防重和迁移的原子边界。
        private val lock = Any()
        private fun accountKey(username: String) = "mock_auth_v1." + hex(
            MessageDigest.getInstance("SHA-256").digest(username.trim().toByteArray(Charsets.UTF_8)))
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        private fun unhex(value: String): ByteArray {
            check(value.length % 2 == 0) { "AUTH_RECORD_INVALID" }
            return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }
}
