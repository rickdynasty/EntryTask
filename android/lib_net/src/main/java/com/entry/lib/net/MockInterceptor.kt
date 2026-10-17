package com.entry.lib.net

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import com.entry.lib.stroage.StorageManager
import com.entry.lib.log.MLog
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Dedicated app client only. Every route terminates locally; no socket fallback. */
class MockInterceptor(context: Context) : Interceptor {
    private val app = context.applicationContext
    private val routes by lazy { JSONObject(asset("manifest.json")).getJSONArray("routes") }
    private fun asset(path: String) = app.assets.open("mock/$path").bufferedReader().use { it.readText() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val delayMs = (1000L..3000L).random()
        val deadline = SystemClock.elapsedRealtime() + delayMs
        // OkHttp worker, not main/JS. Observe cancellation even during simulated delay.
        while (SystemClock.elapsedRealtime() < deadline) {
            if (chain.call().isCanceled()) throw IOException("CANCELLED")
            try { Thread.sleep(minOf(25L, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1L)) }
            catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("CANCELLED", e) }
        }
        if (chain.call().isCanceled()) throw IOException("CANCELLED")
        val trace = request.header("X-Request-Id") ?: UUID.randomUUID().toString()
        return try {
            val route = (0 until routes.length()).map { routes.getJSONObject(it) }
                .firstOrNull { it.getString("method") == request.method && it.getString("path") == request.url.encodedPath }
            if (route == null) return response(request, error("MOCK_NOT_FOUND"), trace, 404)
            MLog.i("EntryTask:Mock", "apiId=${route.getString("apiId")} requestId=$trace configuredDelayMs=$delayMs")
            val template = JSONObject(asset(route.getString("response")))
            when (route.getString("handler")) {
                "login", "register" -> response(request, auth(route.getString("handler"), request, template), trace)
                "products" -> {
                    val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
                    val size = request.url.queryParameter("pageSize")?.toIntOrNull() ?: 10
                    if (page < 1 || size !in 1..50) return response(request, error("VALIDATION_ERROR"), trace, 400)
                    val data = template.getJSONObject("data")
                    val all = data.getJSONArray("items")
                    val category = request.url.queryParameter("category")
                    val filtered = (0 until all.length()).map { all.getJSONObject(it) }.filter { category == null || it.getString("category") == category }
                    val offset = (page.toLong() - 1) * size
                    val items = JSONArray()
                    if (offset < filtered.size) filtered.drop(offset.toInt()).take(size).forEach { items.put(it) }
                    data.put("items", items).put("page", page).put("pageSize", size).put("total", filtered.size).put("hasMore", offset + size < filtered.size)
                    response(request, template, trace)
                }
                else -> response(request, template, trace)
            }
        } catch (e: Exception) {
            // Missing/bad packaged resources must fail, never escape to the Internet.
            MLog.e("EntryTask:Mock", "MOCK_DATA_INVALID requestId=$trace")
            response(request, JSONObject().put("code", "MOCK_DATA_INVALID").put("message", "本地 Mock 数据无效").put("data", JSONObject.NULL), trace, 500)
        }
    }

    private fun error(code: String) = JSONObject(asset("errors/$code.json"))
    private fun response(request: Request, body: JSONObject, trace: String, status: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status).message("Local Mock")
        .body(body.put("traceId", trace).toString().toResponseBody("application/json; charset=utf-8".toMediaType())).build()

    private fun params(request: Request): Map<String, String> {
        val buffer = Buffer(); request.body?.writeTo(buffer); val body = buffer.readUtf8()
        if (body.trim().startsWith("{")) {
            val obj = JSONObject(body)
            return obj.keys().asSequence().associateWith { obj.optString(it) }
        }
        return body.split('&').filter { it.contains('=') }.associate {
            val pair = it.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
    }

    @Synchronized private fun auth(operation: String, request: Request, template: JSONObject): JSONObject {
        val values = params(request)
        val username = values["username"]?.trim().orEmpty()
        val password = values["password"].orEmpty()
        fun failure(code: String): JSONObject = error(code).put("errorCode", code).put("code", 400)
        if (username.isBlank() || username.length > 32 || password.isBlank() || password.length > 64) return failure("VALIDATION_ERROR")
        val users = JSONArray(StorageManager.decodeString("mock_users_v1", null) ?: asset("seeds/users.json"))
        val found = (0 until users.length()).map { users.getJSONObject(it) }.firstOrNull { it.getString("username") == username }
        if (operation == "register") {
            if (found != null) return failure("ACCOUNT_EXISTS")
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            users.put(JSONObject().put("username", username).put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("verifier", Base64.encodeToString(derive(password, salt, 600000), Base64.NO_WRAP)).put("iterations", 600000))
            // Use confirmed persistence for registration; don't report success before disk write.
            val prefs = app.getSharedPreferences("app_default_pref", Context.MODE_PRIVATE)
            check(prefs.edit().putString("mock_users_v1", users.toString()).remove("mock_db_password").remove("mock_db_username").commit())
        } else {
            if (found == null) return failure("INVALID_CREDENTIALS")
            val actual = derive(password, Base64.decode(found.getString("salt"), Base64.NO_WRAP), found.getInt("iterations"))
            if (!MessageDigest.isEqual(actual, Base64.decode(found.getString("verifier"), Base64.NO_WRAP))) return failure("INVALID_CREDENTIALS")
        }
        template.getJSONObject("data").put("username", username).put("id", (username.hashCode().toLong() and 0x7fffffffL) + 1)
        return template
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        if (Build.VERSION.SDK_INT >= 26) {
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        }
        // SHA256 PBKDF2 provider is not guaranteed on API 24/25. Same RFC 8018 output.
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1)); val result = u.clone()
        repeat(iterations - 1) { u = mac.doFinal(u); for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte() }
        return result
    }
}
