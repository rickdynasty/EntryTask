package com.entry.lib.net

import com.entry.lib.stroage.StorageManager
import com.entry.lib.log.MLog
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder

/**
 * 本地 Mock 网络拦截器
 * 1. 拦截注册：将前端传过来的账号密码存入 SP
 * 2. 拦截登录：读取 SP 中的账号密码与前端传入的进行对比校验
 * 3. 模拟 1~3 秒随机网络延迟，触发 UI 的 Loading 动画
 */
class MockInterceptor : Interceptor {

    companion object {
        private const val TAG = "Rick_tan:MockInterceptor"
        private const val KEY_REG_USERNAME = "mock_db_username"
        private const val KEY_REG_PASSWORD = "mock_db_password"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // 1. 模拟随机 1 ~ 3 秒网络延迟（此时 UI 侧通过 isLoading 展示 Loading 弹框）
        val delayTime = (1000..3000).random().toLong()
        MLog.d(TAG, "Mock 拦截到请求: $path，模拟网络延迟: ${delayTime}ms")
        try {
            Thread.sleep(delayTime)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        // 获取请求体中的参数（兼容 表单格式 与 JSON 格式）
        val requestBodyString = getRequestBodyString(request)
        val paramMap = parseParams(requestBodyString)
        val inputUsername = paramMap["username"] ?: ""
        val inputPassword = paramMap["password"] ?: ""


        // 2. 拦截注册请求：将账号密码写入 SP
        if (path.contains("register")) {
            if (inputUsername.isBlank() || inputPassword.isBlank()) {
                val failJson =
                    "{\"code\":400, \"message\":\"注册账号或密码不能为空\", \"data\":null}"
                return buildResponse(request, failJson)
            }

            // 写入本地 SharedPreferences (CacheManager)
            StorageManager.encode(KEY_REG_USERNAME, inputUsername)
            StorageManager.encode(KEY_REG_PASSWORD, inputPassword)
            MLog.d(TAG, "注册成功，账号密码已写入本地 SP: username = $inputUsername")

            val successJson =
                "{\"code\":200, \"message\":\"注册成功\", \"data\":{\"username\":\"$inputUsername\", \"token\":\"mock_token_xyz\"}}"
            return buildResponse(request, successJson)
        }

        // 3. 拦截登录请求：读取本地 SP 内容进行校验
        if (path.contains("login")) {
            val savedUsername = StorageManager.decodeString(KEY_REG_USERNAME, "")
            val savedPassword = StorageManager.decodeString(KEY_REG_PASSWORD, "")

            MLog.d(TAG, "正在校验登录：输入账号=$inputUsername, 本地SP账号=$savedUsername")

            // 比对校验逻辑
            val isMatch = inputUsername == savedUsername && inputPassword == savedPassword

            val jsonResponse = if (isMatch) {
                "{\"code\":200, \"message\":\"登录成功\", \"data\":{\"username\":\"$inputUsername\", \"token\":\"mock_token_xyz\"}}"
            } else {
                "{\"code\":400, \"message\":\"账号或密码错误，请检查或先注册\", \"data\":null}"
            }
            return buildResponse(request, jsonResponse)
        }

        // 其他请求直接放行
        return chain.proceed(request)
    }

    /**
     * 辅助方法：读取 OkHttp RequestBody 的字符串内容
     */
    private fun getRequestBodyString(request: Request): String {
        return try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            buffer.readUtf8()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 兼容解析：支持表单形式 (username=xxx&password=xxx) 与 JSON 形式
     */
    private fun parseParams(bodyString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (bodyString.isBlank()) return map

        // 1. 尝试当做 JSON 解析
        if (bodyString.trim().startsWith("{")) {
            try {
                val jsonObj = JSONObject(bodyString)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObj.optString(key, "")
                }
                return map
            } catch (_: Exception) {
            }
        }

        // 2. 当做 Form-UrlEncoded 解析 (username=user&password=123456)
        val pairs = bodyString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                map[key] = value
            }
        }
        return map
    }

    /**
     * 构建 OkHttp Mock 响应体
     */
    private fun buildResponse(request: Request, json: String): Response {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toResponseBody(mediaType)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build()
    }
}