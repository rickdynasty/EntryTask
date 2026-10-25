package com.entrytask.storage

import android.content.SharedPreferences
import com.entry.lib.net.mock.MockAccountStore.Companion.ACCOUNT_KEY_PREFIX

/** 只用于升级读取/删除旧账号，禁止新增SP账号写入入口。 */
interface LegacyAccountSource {
    fun verifiers(): Map<String, String>

    fun read(key: String): String?

    fun remove(keys: Set<String>): Boolean
}

class SharedPreferencesLegacyAccounts(private val preferences: SharedPreferences) :
    LegacyAccountSource {
    override fun verifiers(): Map<String, String> =
        preferences.all
            .filterKeys { it.startsWith(ACCOUNT_KEY_PREFIX) }
            .mapValues { (key, value) ->
                check(
                    key.matches(Regex(Regex.escape(ACCOUNT_KEY_PREFIX) + "[0-9a-f]{64}")) &&
                        value is String
                ) {
                    "AUTH_LEGACY_INVALID"
                }
                value as String
            }

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun remove(keys: Set<String>): Boolean {
        if (keys.isEmpty()) return true
        val editor = preferences.edit()
        keys.forEach(editor::remove)
        return editor.commit()
    }
}
