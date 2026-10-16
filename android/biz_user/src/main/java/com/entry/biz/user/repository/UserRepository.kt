package com.entry.biz.user.repository

import com.entry.biz.user.model.User
import com.entry.biz.user.network.UserService
import com.entry.lib.base.mvvm.cache.CacheManager
import com.entry.lib.base.mvvm.model.BaseRepository

class UserRepository(private val userService: UserService) : BaseRepository() {

    suspend fun login(username: String, pass: String): User {
        val response = userService.login(username, pass)
        if (response.code == 200 && response.data != null) {
            val user = response.data
            // 登录成功后，利用 lib_base 的 CacheManager 缓存 Token 和用户名
            user.token?.let { CacheManager.encode("key_token", it) }
            CacheManager.encode("key_username", user.username)
            return user
        } else {
            throw Exception(response.message ?: "登录失败")
        }
    }

    suspend fun register(username: String, pass: String): User {
        val response = userService.register(username, pass)
        if (response.code == 200 && response.data != null) {
            val user = response.data
            user.token?.let { CacheManager.encode("key_token", it) }
            CacheManager.encode("key_username", user.username)
            return user
        } else {
            throw Exception(response.message ?: "注册失败")
        }
    }
}