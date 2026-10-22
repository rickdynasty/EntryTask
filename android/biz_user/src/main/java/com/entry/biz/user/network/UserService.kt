package com.entry.biz.user.network

import com.entry.biz.user.model.ApiResponse
import com.entry.biz.user.model.User
import com.entry.lib.net.NetManager
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/** Native登录/注册HTTP契约；Retrofit按方法注解生成表单请求。 */
interface UserService {
    @FormUrlEncoded
    @POST("api/user/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): ApiResponse<User>

    @FormUrlEncoded
    @POST("api/user/register")
    suspend fun register(
        @Field("username") username: String,
        @Field("password") password: String
    ): ApiResponse<User>
}

/** 由NetManager懒加载创建的服务代理；调用接口才发请求，不在UI/Repository读写Mock账号SP。 */
val userService: UserService by lazy {
    NetManager.create(UserService::class.java)
}