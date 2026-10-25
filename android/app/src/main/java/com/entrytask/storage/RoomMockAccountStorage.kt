package com.entrytask.storage

import com.entry.lib.net.mock.MockAccountStorage
import com.entry.lib.net.mock.MockAccountStore.Companion.ACCOUNT_KEY_PREFIX
import com.entry.lib.stroage.room.CredentialStore

/** 认证只读写Room；不回退或迁移旧SP，存储失败必须传播给请求方。 */
class RoomMockAccountStorage(private val credentials: CredentialStore) : MockAccountStorage {
    private fun validate(key: String) {
        require(key.matches(Regex(Regex.escape(ACCOUNT_KEY_PREFIX) + "[0-9a-f]{64}"))) {
            "AUTH_STORAGE_KEY_INVALID"
        }
    }

    override fun read(key: String): String? {
        validate(key)
        return credentials.read(key)
    }

    /** Room唯一主键保证并发注册不覆盖已有校验值。 */
    override fun create(key: String, verifier: String): Boolean {
        validate(key)
        return key in credentials.insertIfAbsent(mapOf(key to verifier))
    }
}
