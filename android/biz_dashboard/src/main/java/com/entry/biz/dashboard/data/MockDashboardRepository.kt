package com.entry.biz.dashboard.data

import android.content.res.AssetManager
import com.entry.biz.dashboard.domain.*
import com.entry.lib.log.MLog
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 使用宿主注入的同一Mock client；assets只读取路由配置，不直接读取响应fixture。 */
class MockDashboardRepository(
    private val assets: AssetManager,
    private val client: OkHttpClient,
    private val scope: () -> String?,
    private val epoch: () -> Long,
) : DashboardRepository {
    private suspend fun request(api: String, category: String? = null): JSONObject = withContext(Dispatchers.IO) {
        val version = epoch()
        val userScope = scope() ?: throw IOException("AUTH_REQUIRED")
        val routes = JSONObject(assets.open("mock/manifest.json").bufferedReader().use { it.readText() }).getJSONArray("routes")
        val route = (0 until routes.length()).map { routes.getJSONObject(it) }.first { it.getString("apiId") == api }
        require(route.getString("method") == "GET")
        val url = ("https://entrytask.invalid" + route.getString("path")).toHttpUrl().newBuilder()
        if (category != null) url.addQueryParameter("category", category)
        val id = UUID.randomUUID().toString()
        val call = client.newCall(Request.Builder().url(url.build()).header("X-Demo-Api", api)
            .header("X-Demo-Scope", userScope).header("X-Request-Id", id).build())
        MLog.i("EntryTask.NativeDashboard", "request.started apiId=$api requestId=$id")
        val body = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP_${it.code}")
                            val value = it.body?.string().orEmpty()
                            require(value.length <= 262144)
                            value
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
        ensureActive()
        check(epoch() == version && scope() == userScope) { "STALE_SESSION" }
        val reply = JSONObject(body)
        check(reply.optInt("code") == 200) { "MOCK_RESPONSE_INVALID" }
        MLog.i("EntryTask.NativeDashboard", "request.completed apiId=$api requestId=$id")
        reply.getJSONObject("data")
    }
    private fun rows(array: JSONArray?) = if (array == null) emptyList() else (0 until array.length()).map { array.getJSONObject(it) }
    private fun services(data: JSONObject, key: String) = rows(data.optJSONArray(key)).map {
        ServiceEntry(it.getString("title"), it.optString("icon", "more"), it.optJSONObject("target")?.optString("category").orEmpty(), it.optString("badge"))
    }
    private fun promotion(item: JSONObject) = Promotion(item.getString("title"), item.optString("subtitle"), item.optString("assetKey"))
    private fun money(value: JSONObject?): String = value?.let {
        BigDecimal(it.getString("amountMinor")).movePointLeft(it.getInt("minorUnit")).setScale(2).toPlainString()
    } ?: "0.00"

    override suspend fun load(tab: Int, category: String?): DashboardPage = coroutineScope {
        require(tab in 1..4)
        val content = async { request(listOf("home.content", "wealth.content", "member.content", "profile.marketing")[tab - 1]) }
        val assets = if (tab == 2 || tab == 4) async { request("asset.summary") } else null
        val summary = if (tab == 3 || tab == 4) async { request(if (tab == 3) "member.summary" else "profile.summary") } else null
        val products = if (tab <= 2) async { request("product.list", category) } else null
        val data = content.await()
        val balances = assets?.await()
        val identity = summary?.await()
        DashboardPage(tab = tab, services = services(data, "services"), quick = services(data, "quickServices"),
            shortcuts = services(data, "shortcuts"),
            promotions = rows(data.optJSONArray("banners")).map(::promotion) +
                listOfNotNull(data.optJSONObject("banner")?.let(::promotion)),
            categories = rows(data.optJSONArray("categories")).map { ServiceEntry(it.getString("title"), "", it.getString("id")) },
            products = rows(products?.await()?.optJSONArray("items")).take(6).map {
                Product(it.getString("name"), BigDecimal(it.getString("ratio")).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString() + "%",
                    it.getString("term"), it.getString("riskLevel"))
            }, market = data.optJSONObject("market")?.optString("title").orEmpty(), welcome = data.optString("welcome"),
            counts = listOf("bankCards", "todos", "coupons", "points", "energy").map { identity?.optInt(it)?.toString() ?: "0" },
            total = money(balances?.optJSONObject("total")), income = money(balances?.optJSONObject("income")),
            slogan = data.optString("assetSlogan"), notice = data.optString("assetNotice"),
            loanLimit = data.optJSONObject("loanLimit")?.let { money(it) }.orEmpty(),
            loanRate = data.optString("loanRate").takeIf { it.isNotEmpty() }?.let { BigDecimal(it).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString() + "%" }.orEmpty())
    }
}
