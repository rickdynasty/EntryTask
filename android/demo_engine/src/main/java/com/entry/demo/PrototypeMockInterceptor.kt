package com.entry.demo

import android.content.Context
import android.os.SystemClock
import com.entry.lib.log.MLog
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dedicated local demo client. Never registered globally or on Metro/RN public HTTP. */
class PrototypeMockInterceptor(context: Context) : Interceptor {
    private val assets = context.applicationContext.assets
    private val preferences = context.getSharedPreferences("prototype_demo_v1", Context.MODE_PRIVATE)
    private val routes by lazy { fixture("manifest.json").getJSONArray("routes") }
    companion object { private val stateLock = Any() }
    private fun fixture(file: String): JSONObject = JSONObject(assets.open("mock/$file").bufferedReader().use { it.readText() })
    private fun date(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date())
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val trace = request.header("X-Request-Id").orEmpty()
        val delay = (1000L..3000L).random()
        val deadline = SystemClock.elapsedRealtime() + delay
        while (SystemClock.elapsedRealtime() < deadline) {
            if (chain.call().isCanceled()) throw IOException("CANCELLED")
            try { Thread.sleep(minOf(25L, deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
            catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("CANCELLED", e) }
        }
        if (chain.call().isCanceled()) throw IOException("CANCELLED")
        var status = 200
        val reply = try {
            val route = (0 until routes.length()).map { routes.getJSONObject(it) }.firstOrNull {
                it.getString("apiId") == request.header("X-Demo-Api") && it.getString("method") == request.method &&
                    request.url.encodedPath.matches(Regex(it.getString("path").replace("{id}", "[A-Za-z0-9_-]+")))
            }
            if (route == null) { status = 404; error("MOCK_NOT_FOUND") }
            else {
                val scope = request.header("X-Demo-Scope") ?: "guest"
                if (route.optBoolean("requiresAuth") && scope == "guest") { status = 401; error("AUTH_REQUIRED") }
                else synchronized(stateLock) {
                    val state = JSONObject(preferences.getString(scope, "{}") ?: "{}")
                    val data = process(route, chain, state)
                    if (chain.call().isCanceled()) throw IOException("CANCELLED")
                    if (request.method == "POST") {
                        check(scope != "guest")
                        check(preferences.edit().putString(scope, state.toString()).commit())
                    }
                    MLog.i("EntryTask.Mock", "request.completed apiId=${route.getString("apiId")} requestId=$trace delayMs=$delay")
                    JSONObject().put("code", 200).put("message", "success").put("data", data)
                }
            }
        } catch (e: DemoFailure) { status = e.status; error(e.code) }
          catch (e: IOException) { throw e }
          catch (e: Exception) {
            MLog.e("EntryTask.Mock", "request.failed requestId=$trace code=MOCK_DATA_INVALID exception=${e.javaClass.simpleName}")
            status = 500; error("MOCK_DATA_INVALID")
        }
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Local Mock")
            .body(reply.put("traceId", trace).toString().toResponseBody("application/json; charset=utf-8".toMediaType())).build()
    }
    private fun error(code: String) = JSONObject().put("code", code).put("message", code).put("data", JSONObject.NULL)
    private fun source(apiId: String): Any {
        val route = (0 until routes.length()).map { routes.getJSONObject(it) }.first { it.getString("apiId") == apiId }
        return fixture(route.getString("response")).get("data")
    }
    private fun objects(array: JSONArray) = (0 until array.length()).map { array.getJSONObject(it) }
    private fun page(items: List<JSONObject>, chain: Interceptor.Chain): JSONObject {
        val page = chain.request().url.queryParameter("page")?.toIntOrNull() ?: 1
        val size = chain.request().url.queryParameter("pageSize")?.toIntOrNull() ?: 10
        if (page < 1 || size !in 1..50) throw DemoFailure("VALIDATION_ERROR", 400)
        val offset = (page.toLong() - 1) * size
        val selected = if (offset >= items.size) emptyList() else items.drop(offset.toInt()).take(size)
        return JSONObject().put("items", JSONArray(selected)).put("page", page).put("pageSize", size).put("total", items.size).put("hasMore", offset + size < items.size)
    }
    private val ledger by lazy { CheckinLedger({ date() }, { JSONObject(source("checkin.status").toString()) }) }
    private fun checkin(state: JSONObject) = ledger.snapshot(state)
    private fun benefits(state: JSONObject): List<JSONObject> {
        val claimed = objects(state.optJSONArray("claims") ?: JSONArray()).map { it.getString("benefitId") }
        return objects((source("benefit.list") as JSONObject).getJSONArray("items")).map {
            val item = JSONObject(it.toString())
            if (item.getString("id") in claimed) item.put("status", "claimed")
            item
        }
    }
    private fun process(route: JSONObject, chain: Interceptor.Chain, state: JSONObject): Any {
        val request = chain.request()
        val id = request.url.pathSegments.lastOrNull { it != "claims" && it != "read" }.orEmpty()
        val category = request.url.queryParameter("category")
        val data = fixture(route.getString("response")).get("data")
        fun find(list: List<JSONObject>): JSONObject = list.firstOrNull { it.getString("id") == id } ?: throw DemoFailure("RESOURCE_NOT_FOUND", 404)
        fun rows(api: String) = objects((source(api) as JSONObject).getJSONArray("items"))
        return when (route.getString("handler")) {
            "fixture" -> data
            "collection" -> page(objects((data as JSONObject).getJSONArray("items")), chain)
            "products" -> page(rows("product.list").filter { category == null || category == "all" || (category == "hot" && it.getString("category") == "fund") || it.getString("category") == category }, chain)
            "productDetail" -> find(rows("product.list"))
            "articleDetail" -> find(rows("article.list"))
            "activityDetail" -> find(rows("activity.list"))
            "assets" -> (data as JSONObject).apply {
                // Registered local accounts start with no real financial assets.
                for (key in listOf("total", "liabilities", "net", "income")) getJSONObject(key).put("amountMinor", "0")
                getJSONArray("categories").let { array -> objects(array).forEach { it.put("amountMinor", "0") } }
            }
            "summary" -> (data as JSONObject).put("bankCards", 0).put("todos", 0).put("points", 0)
                .put("coupons", state.optJSONArray("claims")?.length() ?: 0).put("energy", state.optInt("energy")).apply {
                    optJSONObject("tierProgress")?.put("asOf", date().take(7))
                }
            "financialOverview" -> (data as JSONObject).put("month", date().take(7))
            "accountDetail", "transactionDetail", "itemDetail" -> throw DemoFailure("RESOURCE_NOT_FOUND", 404)
            "transactions", "items" -> page(emptyList(), chain)
            "checkin" -> checkin(state)
            "checkinSubmit" -> {
                val buffer = Buffer(); request.body?.writeTo(buffer); val body = JSONObject(buffer.readUtf8())
                if (body.optString("expectedBusinessDate") != date()) throw DemoFailure("BUSINESS_DATE_CHANGED", 409)
                require(body.optString("idempotencyKey").matches(Regex("[A-Za-z0-9-]{1,100}")))
                ledger.submit(state, body.optString("expectedBusinessDate"))
            }
            "benefits" -> page(benefits(state), chain)
            "benefitDetail" -> find(benefits(state))
            "claim" -> {
                val benefit = find(benefits(state))
                val claims = state.optJSONArray("claims") ?: JSONArray()
                objects(claims).firstOrNull { it.getString("benefitId") == id } ?: run {
                    if (benefit.optString("status") != "available") throw DemoFailure(if (benefit.optInt("stock") == 0) "BENEFIT_SOLD_OUT" else "BENEFIT_NOT_ELIGIBLE", 403)
                    val claim = JSONObject().put("id", "claim-$id").put("benefitId", id).put("title", benefit.getString("title")).put("date", date()).put("status", "已领取").put("paragraphs", benefit.getJSONArray("paragraphs"))
                    claims.put(claim); state.put("claims", claims); claim
                }
            }
            "claims" -> page(objects(state.optJSONArray("claims") ?: JSONArray()), chain)
            "claimDetail" -> find(objects(state.optJSONArray("claims") ?: JSONArray()))
            "energy" -> page(objects(state.optJSONArray("energyRecords") ?: JSONArray()), chain).put("balance", state.optInt("energy"))
            "messages", "messageDetail", "messageRead", "unread" -> {
                val readIds = state.optJSONArray("readIds") ?: JSONArray()
                val read = (0 until readIds.length()).map { readIds.getString(it) }.toMutableSet()
                val messages = rows("message.list")
                if (route.getString("handler") == "messageRead") { find(messages); read.add(id); state.put("readIds", JSONArray(read)) }
                messages.forEach { it.put("isRead", it.getString("id") in read) }
                val unread = messages.count { !it.getBoolean("isRead") }
                when (route.getString("handler")) {
                    "messages" -> page(messages, chain)
                    "messageDetail" -> find(messages)
                    else -> JSONObject().put("isRead", true).put("unreadCount", unread)
                }
            }
            "search" -> {
                val word = request.url.queryParameter("keyword").orEmpty().trim()
                val scope = request.url.queryParameter("scope") ?: "services"
                val list = when(scope) {
                    "products" -> rows("product.list").map { JSONObject().put("id", it.getString("id")).put("title", it.getString("name")).put("summary", it.getString("riskText")).put("target", JSONObject().put("kind", "product").put("title", it.getString("name")).put("id", it.getString("id")).put("requiresAuth", false)) }
                    "articles" -> rows("article.list").map { it.put("target", JSONObject().put("kind", "article").put("title", it.getString("title")).put("id", it.getString("id")).put("requiresAuth", false)) }
                    "activities" -> rows("activity.list").map { it.put("target", JSONObject().put("kind", "activity").put("title", it.getString("title")).put("id", it.getString("id")).put("requiresAuth", false)) }
                    else -> rows("service.catalog")
                }
                page(list.filter { word.isNotEmpty() && it.getString("title").contains(word, ignoreCase = true) }, chain)
            }
            else -> data
        }.let { result ->
            if (route.getString("apiId") == "account.list") page(emptyList(), chain) else result
        }
    }
}
