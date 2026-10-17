package com.entry.biz.user.manager

import android.content.Context
import android.content.Intent
import com.entry.biz.user.ui.AuthActivity
import com.entry.lib.log.MLog

/**
 * 用户认证状态管理单例
 * 负责在内存中维护冷启动的登录状态，退出应用或进程杀死后自动重置
 */
object UserAuthManager {
    private const val TAG = "Rick_tan:UserAuthManager"

    // 内存属性记录登录状态，默认未登录
    @Volatile private var isUserLoggedIn: Boolean = false
    @Volatile var sessionEpoch: Long = 0
        private set
    private var userScope: String? = null
    private var credential: String? = null

    @Synchronized fun establishSession(username: String, token: String) {
        require(username.isNotBlank() && token.isNotBlank())
        userScope = java.security.MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).joinToString("") { "%02x".format(it) }
        credential = token
        isUserLoggedIn = true
        sessionEpoch++
    }
    @Synchronized fun storageScope(): String? = if (isUserLoggedIn) userScope else null

    /**
     * 检查当前是否已登录
     */
    fun isLogin(): Boolean {
        MLog.d(TAG, "检查当前内存登录状态: $isUserLoggedIn")
        return isUserLoggedIn
    }

    /**
     * 登出，清空内存状态
     */
    @Synchronized fun logout() {
        credential = null
        userScope = null
        sessionEpoch++
        isUserLoggedIn = false
        MLog.d(TAG, "用户已登出，重置登录状态")
    }


    /**
     * 拉起登录/注册界面
     */
    fun startAuthActivity(context: Context) {
        val intent = Intent(context, AuthActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}