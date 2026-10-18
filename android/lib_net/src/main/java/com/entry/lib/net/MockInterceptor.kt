package com.entry.lib.net

import android.content.Context
import android.os.SystemClock
import com.entry.lib.log.MLog
import com.entry.lib.stroage.StorageManager
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.util.UUID

/**
 * 本地 Mock 拦截器：专用于 App 客户端的本地路由匹配与响应模拟。
 * 所有请求均在本地终止，不经过真实的 Socket 网络。
 */
class MockInterceptor(context: Context) : Interceptor {
    companion object {
        private const val TAG = "Rick_tan:MockInterceptor"
        private const val KEY_REG_USERNAME = "mock_reg_username"
        private const val KEY_REG_PASSWORD = "mock_reg_password"
    }

    private val app = context.applicationContext

    // 读取 assets 目录下的 manifest.json 路由配置清单
    private val routes by lazy {
        JSONObject(asset("manifest.json")).getJSONArray("routes")
    }

    /** 读取 assets 下指定路径的文本内容 */
    private fun asset(path: String): String {
        return app.assets.open("mock/$path").bufferedReader().use { it.readText() }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // 1. 模拟网络延迟（1~2.5秒随机耗时，支持监听取消操作）
        val delayMs = (1000L..2500L).random()
        val deadline = SystemClock.elapsedRealtime() + delayMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (chain.call().isCanceled()) throw IOException("CANCELLED")
            try {
                Thread.sleep(minOf(25L, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("CANCELLED", e)
            }
        }
        if (chain.call().isCanceled()) throw IOException("CANCELLED")

        val trace = request.header("X-Request-Id") ?: UUID.randomUUID().toString()

        return try {
            // 2. 拦截并处理：注册请求
            if (path.contains("register")) {
                val params = extractParams(request)
                val inputUsername = params["username"]?.trim().orEmpty()
                val inputPassword = params["password"].orEmpty()

                if (inputUsername.isBlank() || inputPassword.isBlank()) {
                    val failJson =
                        "{\"code\":400, \"message\":\"注册账号或密码不能为空\", \"data\":null}"
                    return buildResponse(request, failJson, trace, 200)
                }

                // 写入本地 SharedPreferences (StorageManager)
                StorageManager.encode(KEY_REG_USERNAME, inputUsername)
                StorageManager.encode(KEY_REG_PASSWORD, inputPassword)
                MLog.d(TAG, "注册成功，账号密码已写入本地 SP: username = $inputUsername")

                val successJson =
                    "{\"code\":200, \"message\":\"注册成功\", \"data\":{\"username\":\"$inputUsername\", \"token\":\"mock_token_xyz\"}}"
                return buildResponse(request, successJson, trace, 200)
            }

            // 3. 拦截并处理：登录请求
            if (path.contains("login")) {
                val params = extractParams(request)
                val inputUsername = params["username"]?.trim().orEmpty()
                val inputPassword = params["password"].orEmpty()

                val savedUsername = StorageManager.decodeString(KEY_REG_USERNAME, "")
                val savedPassword = StorageManager.decodeString(KEY_REG_PASSWORD, "")

                MLog.d(TAG, "正在校验登录：输入账号=$inputUsername, 本地SP账号=$savedUsername")

                // 比对校验逻辑
                val isMatch = inputUsername.isNotBlank() &&
                        inputPassword.isNotBlank() &&
                        inputUsername == savedUsername &&
                        inputPassword == savedPassword

                val jsonResponse = if (isMatch) {
                    "{\"code\":200, \"message\":\"登录成功\", \"data\":{\"username\":\"$inputUsername\", \"token\":\"mock_token_xyz\"}}"
                } else {
                    "{\"code\":400, \"message\":\"账号或密码错误，请检查或先注册\", \"data\":null}"
                }
                return buildResponse(request, jsonResponse, trace, 200)
            }

            // 4. 其他业务请求：按原有 Asset 路由规则读取对应 JSON Mock 数据
            val route = (0 until routes.length())
                .map { routes.getJSONObject(it) }
                .firstOrNull {
                    it.getString("method") == request.method &&
                            it.getString("path") == path
                }

            if (route == null) {
                return buildResponse(request, asset("errors/MOCK_NOT_FOUND.json"), trace, 404)
            }

            MLog.i(
                TAG,
                "apiId=${route.getString("apiId")} requestId=$trace configuredDelayMs=$delayMs"
            )
            val template = asset(route.getString("response"))

            // 针对特定业务（如 products 分页）进行动态扩展
            val responseBodyStr = if (route.getString("handler") == "products") {
                handleProductsPagination(request, template)
            } else {
                template
            }

            buildResponse(request, responseBodyStr, trace, 200)

        } catch (e: Exception) {
            MLog.e(TAG, "MOCK_DATA_INVALID requestId=$trace, error=${e.message}", e)
            val errorJson = "{\"code\":500, \"message\":\"本地 Mock 数据解析异常\", \"data\":null}"
            buildResponse(request, errorJson, trace, 500)
        }
    }

    /** 提取请求参数（兼容表单 FormUrlEncoded 与 JSON Body） */
    private fun extractParams(request: Request): Map<String, String> {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        val body = buffer.readUtf8()

        if (body.trim().startsWith("{")) {
            val obj = JSONObject(body)
            return obj.keys().asSequence().associateWith { obj.optString(it) }
        }

        return body.split('&')
            .filter { it.contains('=') }
            .associate {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
    }

    /** 处理 products 分页逻辑 */
    private fun handleProductsPagination(request: Request, templateJson: String): String {
        val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val size = request.url.queryParameter("pageSize")?.toIntOrNull() ?: 10

        val templateObj = JSONObject(templateJson)
        val data = templateObj.getJSONObject("data")
        val all = data.getJSONArray("items")
        val category = request.url.queryParameter("category")

        val filtered = (0 until all.length())
            .map { all.getJSONObject(it) }
            .filter { category == null || it.getString("category") == category }

        val offset = (page.toLong() - 1) * size
        val items = JSONArray()
        if (offset < filtered.size) {
            filtered.drop(offset.toInt()).take(size).forEach { items.put(it) }
        }

        data.put("items", items)
            .put("page", page)
            .put("pageSize", size)
            .put("total", filtered.size)
            .put("hasMore", offset + size < filtered.size)

        return templateObj.toString()
    }

    /** 辅助方法：快速构建标准的 OkHttp Response 响应对象 */
    private fun buildResponse(
        request: Request,
        jsonBody: String,
        trace: String,
        status: Int
    ): Response {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        // 尝试往 JSON 里注入 traceId（若不是合法 JSONObject 则保持原样）
        val finalBody = try {
            JSONObject(jsonBody).put("traceId", trace).toString()
        } catch (e: Exception) {
            jsonBody
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message("Local Mock")
            .body(finalBody.toResponseBody(jsonMediaType))
            .build()
    }
}