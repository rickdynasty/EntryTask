package com.entry.biz.user.repository

import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.lib.log.MLog

/**
 * 用户数据仓库
 * 负责协调网络请求、处理响应结果并建立Native内存会话
 */
class UserRepository(private val userService: UserService) {

    companion object {
        private const val TAG = "Rick_tan:UserRepository"
    }

    /**
     * 执行登录请求
     */
    suspend fun login(username: String, pass: String): User {
        MLog.d(TAG, "开始执行登录请求: api=user")
        val response = userService.login(username, pass)

        if (response.code == 200 && response.data != null) {
            val user = response.data

            UserAuthManager.establishSession(user.username, requireNotNull(user.token))
            return user
        } else {
            MLog.w(TAG, "request.rejected operation=login code=${response.code}")
            throw AuthRejectedException(if (response.code == 400 || response.code == 401)
                "账号或密码错误，请检查后重试" else "登录失败，请稍后重试")
        }
    }

    /**
     * 执行注册请求
     */
    suspend fun register(username: String, pass: String): User {
        MLog.d(TAG, "开始执行注册请求: api=user")
        val response = userService.register(username, pass)

        if (response.code == 200 && response.data != null) {
            MLog.d(TAG, "注册成功，建立完整内存会话")
            val user = response.data
            UserAuthManager.establishSession(user.username, requireNotNull(user.token))
            return user
        } else {
            MLog.w(TAG, "request.rejected operation=register code=${response.code}")
            throw AuthRejectedException(if (response.code == 409)
                "该用户名已注册，请更换用户名或直接登录" else "注册失败，请稍后重试")
        }
    }
}
/** 只有经过业务映射的提示可交给UI，不直接展示服务端载荷。 */
internal class AuthRejectedException(val userMessage: String) : Exception()
