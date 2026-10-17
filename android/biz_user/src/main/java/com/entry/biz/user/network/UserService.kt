package com.entry.biz.user.network

import com.entry.biz.user.model.ApiResponse
import com.entry.biz.user.model.User
import com.entry.lib.net.NetManager
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface UserService {
    @FormUrlEncoded
    @POST("api/user/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") pass: String
    ): ApiResponse<User>

    @FormUrlEncoded
    @POST("api/user/register")
    suspend fun register(
        @Field("username") username: String,
        @Field("password") pass: String
    ): ApiResponse<User>
}

/**
 * 在 biz_user 模块中对外暴露的 userService 实例
 * 通过调用 lib_net 的 RetrofitClient 动态创建
 */
val userService: UserService by lazy {
    NetManager.create(UserService::class.java)
}