package com.entry.biz.user.manager

import android.content.Context
import android.content.Intent
import com.entry.biz.user.ui.AuthActivity
import com.entry.lib.base.mvvm.cache.CacheManager
import com.entry.lib.log.MLog

object UserAuthManager {
    private const val TAG = "UserAuthManager"

    // 使用内存属性记录登录状态（应用重启后自动重置为 false）
    private var isUserLoggedIn: Boolean = false

    /**
     * 判断当前是否已登录
     */
    fun isLogin(): Boolean {
        MLog.d(TAG, "检查当前登录状态: $isUserLoggedIn")
        return isUserLoggedIn
    }

    /**
     * 登录成功后调用，更新内存状态
     */
    fun setLoginSuccess(isLoggedIn: Boolean) {
        isUserLoggedIn = isLoggedIn
        MLog.d(TAG, "更新登录状态: $isUserLoggedIn")
    }

    /**
     * 退出登录或清空状态
     */
    fun logout() {
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

private fun String?.isNullOrEmpty(): Boolean = this == null || this.trim().isEmpty()