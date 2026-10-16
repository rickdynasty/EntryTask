package com.entry.biz.user.network

import com.entry.biz.user.model.ApiResponse
import com.entry.biz.user.model.User
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