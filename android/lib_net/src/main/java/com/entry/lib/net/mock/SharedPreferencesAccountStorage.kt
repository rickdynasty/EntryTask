package com.entry.lib.net.mock

import android.content.SharedPreferences

/** 仅在OkHttp后台线程使用；一次commit原子落盘账号和迁移删除，失败不返回注册成功。 */
class SharedPreferencesAccountStorage(private val preferences: SharedPreferences) : MockAccountStorage {
    override fun read(key: String): String? = preferences.getString(key, null)
    override fun commit(values: Map<String, String>, removals: Set<String>): Boolean {
        val editor = preferences.edit()
        values.forEach { (key, value) -> editor.putString(key, value) }
        removals.forEach(editor::remove)
        return editor.commit()
    }
}
