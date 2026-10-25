package com.entry.biz.user.manager

import com.entry.lib.log.MLog

/**
 * 用户认证状态管理单例
 * 负责在内存中维护冷启动的登录状态，退出应用或进程杀死后自动重置
 */
object UserAuthManager {
    private const val TAG = "EntryTask.Auth"

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
    /** 只接收仍属于发起时会话的认证结果，退出任务后的迟到响应不能重新登录。 */
    @Synchronized fun commitAuthentication(username: String, token: String, expectedEpoch: Long): Boolean {
        if (sessionEpoch != expectedEpoch) return false
        establishSession(username, token)
        return true
    }
    @Synchronized fun storageScope(): String? = if (isUserLoggedIn) userScope else null

    /** 原子读取登录标记和会话版本，供 Native Bridge 生成一致快照。 */
    @Synchronized fun sessionSnapshot(): Pair<Boolean, Long> = isUserLoggedIn to sessionEpoch

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

}
