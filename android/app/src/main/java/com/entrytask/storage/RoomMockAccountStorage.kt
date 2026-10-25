package com.entrytask.storage

import com.entry.lib.log.MLog
import com.entry.lib.net.mock.MockAccountStorage
import com.entry.lib.net.mock.MockAccountStore.Companion.ACCOUNT_KEY_PREFIX as ACCOUNT_PREFIX
import com.entry.lib.net.mock.MockAccountStore.Companion.LEGACY_ACCOUNT_KEYS as LEGACY_KEYS
import com.entry.lib.stroage.room.CredentialStore

/** 组装层的数据适配：Room是账号权威；SP只保留可重试迁移源，不做双写或故障回退。 */
class RoomMockAccountStorage(
    private val credentials: CredentialStore,
    private val legacy: LegacyAccountSource,
) : MockAccountStorage {
    private var imported = false

    /** 跨介质不能原子提交：先整批提交Room，再删除SP；中断重试时已有Room主键优先。 */
    @Synchronized
    private fun importVerifiers() {
        if (imported) return
        val records = legacy.verifiers()
        if (records.isNotEmpty()) {
            MLog.i(TAG, "migration.started")
            credentials.insertIfAbsent(records)
            check(legacy.remove(records.keys)) { "AUTH_MIGRATION_CLEANUP_FAILED" }
            MLog.i(TAG, "migration.completed")
        }
        imported = true
    }

    override fun read(key: String): String? {
        importVerifiers()
        return when {
            key.startsWith(ACCOUNT_PREFIX) -> credentials.read(key)
            key in LEGACY_KEYS -> legacy.read(key)
            else -> error("AUTH_STORAGE_KEY_INVALID")
        }
    }

    override fun create(key: String, verifier: String): Boolean {
        importVerifiers()
        require(key.startsWith(ACCOUNT_PREFIX))
        return key in credentials.insertIfAbsent(mapOf(key to verifier))
    }

    /** 旧明文由MockAccountStore派生后交给本方法；Room成功后才删除完整旧键对。 */
    override fun commit(values: Map<String, String>, removals: Set<String>): Boolean {
        importVerifiers()
        require(
            values.keys.all { it.startsWith(ACCOUNT_PREFIX) } && removals.all { it in LEGACY_KEYS }
        )
        credentials.insertIfAbsent(values)
        val cleaned = legacy.remove(removals)
        if (removals.isNotEmpty()) {
            if (cleaned) MLog.i(TAG, "legacy.migration.completed")
            else MLog.w(TAG, "legacy.migration.cleanup_failed")
        }
        return cleaned
    }

    companion object {
        private const val TAG = "EntryTask.AuthStorage"
    }
}
