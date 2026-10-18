package com.entrytask.bridge

import android.os.SystemClock
import com.entrytask.MainApplication
import com.entry.biz.user.manager.UserAuthManager
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.entrytask.specs.NativeNetworkSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkModule(context: ReactApplicationContext) : NativeNetworkSpec(context) {
    private val calls = ConcurrentHashMap<String, Call>()
    private val cancelled = ConcurrentHashMap<String, Long>()
    private val routes by lazy { JSONObject(reactApplicationContext.assets.open("mock/manifest.json").bufferedReader().use { it.readText() }).getJSONArray("routes") }
    @Volatile private var valid = true

    override fun execute(requestJson: String, promise: Promise) {
        if (requestJson.toByteArray().size > 65536 || !valid) { promise.resolve(BridgeReply.error("INVALID_REQUEST")); return }
        try {
            val input = JSONObject(requestJson)
            val id = input.getString("requestId")
            if (!id.matches(Regex("[A-Za-z0-9-]{1,100}"))) { promise.resolve(BridgeReply.error("INVALID_REQUEST_ID")); return }
            val apiId = input.getString("apiId")
            val route = (0 until routes.length()).map { routes.getJSONObject(it) }.firstOrNull { it.getString("apiId") == apiId }
            if (route == null || route.optString("exposure") != "jsAllowed" || route.getString("method") !in listOf("GET", "POST")) {
                promise.resolve(BridgeReply.error("ENDPOINT_NOT_ALLOWED")); return
            }
            val epoch = UserAuthManager.sessionEpoch
            if (route.optBoolean("requiresAuth") && !UserAuthManager.isLogin()) { promise.resolve(BridgeReply.error("AUTH_REQUIRED")); return }
            val params = input.optJSONObject("params") ?: JSONObject()
            val path = route.getString("path").let { template ->
                if (template.contains("{id}")) {
                    val resourceId = params.getString("id")
                    require(resourceId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
                    template.replace("{id}", resourceId)
                } else template
            }
            val url = "https://entrytask.invalid$path".toHttpUrl().newBuilder()
            params.keys().forEach { key ->
                require(key in setOf("page", "pageSize", "category", "scene", "id", "direction", "accountId", "type", "scope", "keyword"))
                url.addQueryParameter(key, params.get(key).toString())
            }
            val builder = Request.Builder().url(url.build()).header("X-Request-Id", id).header("X-Demo-Api", apiId)
                .header("X-Demo-Scope", UserAuthManager.storageScope() ?: "guest")
            if (route.getString("method") == "POST") builder.post((input.optJSONObject("body") ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
            val call = (reactApplicationContext.applicationContext as MainApplication).prototypeNetwork.newCall(builder.build())
            call.timeout().timeout(input.optLong("timeoutMs", 10000).coerceIn(1000, 10000), TimeUnit.MILLISECONDS)
            synchronized(calls) {
                if (calls.size >= 32 || calls.putIfAbsent(id, call) != null) { promise.resolve(BridgeReply.error("REQUEST_BUSY_OR_DUPLICATE")); return }
                if (cancelled.remove(id) != null) call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    calls.remove(id, call)
                    if (valid) promise.resolve(BridgeReply.error(if (e is java.io.InterruptedIOException) "TIMEOUT" else if (call.isCanceled()) "CANCELLED" else "NETWORK_UNAVAILABLE"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (epoch != UserAuthManager.sessionEpoch) { if (valid) promise.resolve(BridgeReply.error("STALE_SESSION")); return }
                            val body = it.body
                            if (body != null && body.contentLength() > 262144) { if (valid) promise.resolve(BridgeReply.error("PAYLOAD_TOO_LARGE")); return }
                            val text = body?.string().orEmpty()
                            if (valid) promise.resolve(if (text.toByteArray().size > 262144) BridgeReply.error("PAYLOAD_TOO_LARGE") else BridgeReply.ok(JSONObject().put("httpStatus", it.code).put("bodyText", text)))
                        }
                    } catch (e: Exception) { if (valid) promise.resolve(BridgeReply.error("RESPONSE_INVALID")) }
                    finally { calls.remove(id, call) }
                }
            })
        } catch (e: Exception) { promise.resolve(BridgeReply.error("INVALID_REQUEST")) }
    }
    override fun cancel(requestId: String) {
        synchronized(calls) {
            cancelled.entries.removeAll { SystemClock.elapsedRealtime() - it.value > 30000 }
            val call = calls[requestId]
            if (call != null) call.cancel() else if (cancelled.size < 128) cancelled[requestId] = SystemClock.elapsedRealtime()
        }
    }
    override fun invalidate() { valid = false; calls.values.forEach { it.cancel() }; calls.clear(); cancelled.clear(); super.invalidate() }
}
