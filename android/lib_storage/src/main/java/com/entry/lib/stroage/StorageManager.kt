package com.entry.lib.stroage

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

object StorageManager {

    private const val PREF_NAME = "app_default_pref"
    private lateinit var prefs: SharedPreferences // 直接用私有属性，完全没问题！

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun encode(key: String, value: Any) {
        val editor = prefs.edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is ByteArray -> {
                val encoded = Base64.encodeToString(value, Base64.DEFAULT)
                editor.putString(key, encoded)
            }
        }
        editor.apply()
    }

    // 移除了 inline 和 reified，改成普通的安全读取
    fun decodeString(key: String, default: String? = null): String? {
        return prefs.getString(key, default)
    }

    fun decodeInt(key: String, default: Int = 0): Int {
        return prefs.getInt(key, default)
    }

    fun decodeBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(key, default)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}