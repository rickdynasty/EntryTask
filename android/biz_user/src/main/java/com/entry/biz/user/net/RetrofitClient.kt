package com.entry.biz.user.net

import android.content.Context
import com.entry.biz.user.network.MockInterceptor
import com.entry.biz.user.network.UserService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var retrofit: Retrofit? = null

    fun init(context: Context) {
        if (retrofit == null) {
            val okHttpClient = OkHttpClient.Builder()
                // 植入我们的本地 Mock 拦截器，拦截请求读取 assets 中的 json
                .addInterceptor(MockInterceptor())
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl("https://api.entry.com/") // 虚拟 BaseUrl，会被拦截器直接拦截
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    val userService: UserService by lazy {
        requireNotNull(retrofit) { "RetrofitClient must be initialized with Context in Application or Activity first" }
            .create(UserService::class.java)
    }
}