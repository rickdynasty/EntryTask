package com.entry.biz.user.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // 根据路径匹配对应的 Mock 文件
        val assetFileName = when {
            path.contains("login") -> "mock_login.json"
            path.contains("register") -> "mock_register.json"
            else -> return chain.proceed(request) // 非mock请求正常放行
        }

        // 读取 assets 中的 json 数据
        val jsonString = try {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "{\"code\":404, \"message\":\"Mock file not found\", \"data\":null}"
        }

        // 构造一个合法的 OkHttp Response 返回
        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(request)
            .body(jsonString.toResponseBody("application/json;charset=utf-8".toMediaTypeOrNull()))
            .build()
    }
}