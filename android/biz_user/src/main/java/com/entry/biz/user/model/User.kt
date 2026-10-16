package com.entry.biz.user.model

data class User(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatar: String?,
    val token: String?
)

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)