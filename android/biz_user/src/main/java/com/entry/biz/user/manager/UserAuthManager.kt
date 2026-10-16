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
    private var isUserLoggedIn: Boolean = false

    /**
     * 检查当前是否已登录
     */
    fun isLogin(): Boolean {
        MLog.d(TAG, "检查当前内存登录状态: $isUserLoggedIn")
        return isUserLoggedIn
    }

    /**
     * 更新登录成功状态
     */
    fun setLoginSuccess(isLoggedIn: Boolean) {
        isUserLoggedIn = isLoggedIn
        MLog.d(TAG, "更新内存登录状态成功: $isUserLoggedIn")
    }

    /**
     * 登出，清空内存状态
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