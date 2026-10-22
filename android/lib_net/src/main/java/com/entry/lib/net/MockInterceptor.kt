package com.entry.lib.net

import android.content.Context
import android.os.SystemClock
import com.entry.lib.log.MLog
import com.entry.lib.net.mock.MockAccountStore
import com.entry.lib.net.mock.SharedPreferencesAccountStorage
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
class MockInterceptor(
    context: Context,
    private val accounts: MockAccountStore = MockAccountStore(SharedPreferencesAccountStorage(
        context.applicationContext.getSharedPreferences("app_default_pref", Context.MODE_PRIVATE))),
) : Interceptor {
    companion object {
        private const val TAG = "Rick_tan:MockInterceptor"
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

        // 1. 模拟网络延迟（1~3秒随机耗时，支持监听取消操作）
        val delayMs = (1000L..3000L).random()
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

        var nativeAuthentication = false
        return try {
            // 精确按manifest的方法/路径查找，不能让包含login/register的其他路径误命中认证。
            val route = (0 until routes.length()).map { routes.getJSONObject(it) }.firstOrNull {
                it.getString("method") == request.method && it.getString("path") == path
            }

            if (route == null) {
                return buildResponse(request, asset("errors/MOCK_NOT_FOUND.json"), trace, 404)
            }

            if (route.getString("handler") in setOf("login", "register")) {
                nativeAuthentication = true
                return handleAuthentication(request, route, trace)
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
            MLog.e(TAG, "MOCK_DATA_INVALID requestId=$trace exception=${e.javaClass.simpleName}")
            // Native认证响应沿用数字业务码；RN共享错误fixture保持原有字符串语义。
            val error = JSONObject(asset("errors/MOCK_DATA_INVALID.json"))
            if (nativeAuthentication) error.put("code", 500)
            buildResponse(request, error.toString(), trace, if (nativeAuthentication) 200 else 500)
        }
    }

    /** 认证也从分API JSON读取完整响应，仅注入本次规范化用户名，不手拼敏感JSON。 */
    private fun handleAuthentication(request: Request, route: JSONObject, trace: String): Response {
        val params = extractParams(request)
        val username = params["username"]?.trim().orEmpty()
        val password = params["password"].orEmpty() // 密码不trim，注册与登录逐字一致。
        val handler = route.getString("handler")
        val result = if (username.isBlank() || password.isBlank() || username.length > 256 || password.length > 1024) {
            JSONObject(asset("errors/VALIDATION_ERROR.json")).put("code", 400)
        } else if (handler == "register" && accounts.register(username, password) == MockAccountStore.Registration.EXISTS) {
            JSONObject(asset("errors/ACCOUNT_EXISTS.json")).put("code", 409)
        } else if (handler == "login" && !accounts.verify(username, password)) {
            JSONObject(asset("errors/INVALID_CREDENTIALS.json")).put("code", 401)
        } else {
            JSONObject(asset(route.getString("response"))).apply { getJSONObject("data").put("username", username) }
        }
        MLog.i(TAG, "auth.completed apiId=${route.getString("apiId")} requestId=$trace code=${result.getInt("code")}")
        return buildResponse(request, result.toString(), trace, 200)
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