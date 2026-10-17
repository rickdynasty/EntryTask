package com.entry.biz.user.repository

import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.lib.base.mvvm.model.BaseRepository
import com.entry.lib.log.MLog

/**
 * 用户数据仓库
 * 负责协调网络请求、处理响应结果并建立Native内存会话
 */
class UserRepository(private val userService: UserService) : BaseRepository() {

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
            val errorMsg = response.message
            MLog.w(TAG, "登录请求业务校验失败: $errorMsg")
            throw Exception(errorMsg)
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
            val errorMsg = response.message
            MLog.w(TAG, "注册请求业务校验失败: $errorMsg")
            throw Exception(errorMsg)
        }
    }
}