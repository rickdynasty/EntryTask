package com.entry.biz.user.repository

import com.entry.biz.user.domain.AuthRepository
import com.entry.biz.user.domain.AuthenticatedUser
import com.entry.biz.user.domain.AuthRejectedException
import com.entry.biz.user.manager.UserAuthManager
import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.lib.log.MLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 用户数据仓库
 * 负责协调网络请求、处理响应结果并建立Native内存会话
 */
class UserRepository(private val userService: UserService) : AuthRepository {

    companion object {
        private const val TAG = "Rick_tan:UserRepository"
    }

    /**
     * 执行登录请求
     */
    override suspend fun login(username: String, password: String): AuthenticatedUser {
        MLog.d(TAG, "开始执行登录请求: api=user")
        val epoch = UserAuthManager.sessionSnapshot().second
        val response = userService.login(username, password)

        if (response.code == 200 && response.data != null) {
            val user = response.data

            commitSession(user, epoch)
            return AuthenticatedUser(user.id, user.username)
        } else {
            MLog.w(TAG, "request.rejected operation=login code=${response.code}")
            throw AuthRejectedException(if (response.code == 400 || response.code == 401)
                "账号或密码错误，请检查后重试" else "登录失败，请稍后重试")
        }
    }

    /**
     * 执行注册请求
     */
    override suspend fun register(username: String, password: String): AuthenticatedUser {
        MLog.d(TAG, "开始执行注册请求: api=user")
        val epoch = UserAuthManager.sessionSnapshot().second
        val response = userService.register(username, password)

        if (response.code == 200 && response.data != null) {
            MLog.d(TAG, "注册成功，建立完整内存会话")
            val user = response.data
            commitSession(user, epoch)
            return AuthenticatedUser(user.id, user.username)
        } else {
            MLog.w(TAG, "request.rejected operation=register code=${response.code}")
            throw AuthRejectedException(if (response.code == 409)
                "该用户名已注册，请更换用户名或直接登录" else "注册失败，请稍后重试")
        }
    }

    /** 提交前检查取消和会话版本；退出后的旧认证响应不产生会话/成功导航。 */
    private suspend fun commitSession(user: User, expectedEpoch: Long) {
        currentCoroutineContext().ensureActive()
        val token = user.token?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("AUTH_RESPONSE_INVALID")
        check(user.username.isNotBlank()) { "AUTH_RESPONSE_INVALID" }
        if (!UserAuthManager.commitAuthentication(user.username, token, expectedEpoch)) {
            throw CancellationException("AUTH_REQUEST_SUPERSEDED")
        }
    }

}
