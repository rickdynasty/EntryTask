package com.entry.lib.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo

@SuppressLint("StaticFieldLeak")
object AppGlobals {
    private lateinit var mAppContext: Context

    internal fun initialize(context: Context) {
        mAppContext = context
    }

    /**
     * 获取全局 Context
     */
    val context: Context
        get() {
            check(::mAppContext.isInitialized) {
                "AppGlobals is not initialized. Make sure AppContextInitializer is enabled."
            }
            return mAppContext
        }

    /**
     * 基础应用属性代理
     */
    val applicationId: String
        get() = context.packageName

    val isDebug: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}