package com.entry.biz.dashboard.data.pages

import android.content.res.AssetManager
import com.entry.lib.log.MLog
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** 只按manifest白名单调用宿主client；不会直接读取fixture或切公网。 */
class NativeApiClient(
    private val assets: AssetManager,
    private val client: OkHttpClient,
    private val scope: () -> String?,
    val epoch: () -> Long,
) {
    private val routes by lazy {
        JSONObject(assets.open("mock/manifest.json").bufferedReader().use { it.readText() })
            .getJSONArray("routes")
    }

    suspend fun request(
        api: String,
        params: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val version = epoch()
            val user = scope() ?: throw IOException("AUTH_REQUIRED")
            val route =
                (0 until routes.length())
                    .map { routes.getJSONObject(it) }
                    .first { it.getString("apiId") == api }
            val url =
                ("https://entrytask.invalid" + route.getString("path")).toHttpUrl().newBuilder()
            params.forEach { (key, value) -> url.addQueryParameter(key, value) }
            val id = UUID.randomUUID().toString()
            val builder =
                Request.Builder()
                    .url(url.build())
                    .header("X-Demo-Api", api)
                    .header("X-Demo-Scope", user)
                    .header("X-Request-Id", id)
            if (route.getString("method") == "POST")
                builder.post(
                    requireNotNull(body).toString().toRequestBody("application/json".toMediaType())
                )
            else check(body == null && route.getString("method") == "GET")
            val call = client.newCall(builder.build())
            MLog.i("EntryTask.NativeDashboard", "request.started apiId=$api requestId=$id")
            try {
                val raw =
                    suspendCancellableCoroutine<String> { continuation ->
                        continuation.invokeOnCancellation { call.cancel() }
                        call.enqueue(
                            object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    if (continuation.isActive) continuation.resumeWithException(e)
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    try {
                                        val text =
                                            response.use {
                                                val text = it.body?.string().orEmpty()
                                                check(text.length <= 262144)
                                                if (!it.isSuccessful)
                                                    throw IOException(
                                                        JSONObject(text)
                                                            .optString("code", "HTTP_${it.code}")
                                                    )
                                                text
                                            }
                                        if (continuation.isActive) continuation.resume(text)
                                    } catch (e: Exception) {
                                        if (continuation.isActive)
                                            continuation.resumeWithException(e)
                                    }
                                }
                            }
                        )
                    }
                ensureActive()
                check(version == epoch() && user == scope()) { "STALE_SESSION" }
                val reply = JSONObject(raw)
                check(reply.getInt("code") == 200)
                MLog.i("EntryTask.NativeDashboard", "request.completed apiId=$api requestId=$id")
                reply.getJSONObject("data")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                MLog.d("EntryTask.NativeDashboard", "request.cancelled apiId=$api requestId=$id")
                throw cancelled
            } catch (e: Exception) {
                MLog.e(
                    "EntryTask.NativeDashboard",
                    "request.failed apiId=$api requestId=$id code=REQUEST_FAILED exception=${e.javaClass.simpleName}",
                )
                throw e
            }
        }
}
