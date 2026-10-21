package com.entrytask.bridge

import com.entry.lib.log.MLog
import com.entrytask.BuildConfig
import com.entrytask.specs.NativeTelemetrySpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject

/** 接收 RN 白名单诊断事件并转发到 Native 日志，不记录业务载荷。 */
class TelemetryModule(context: ReactApplicationContext) : NativeTelemetrySpec(context) {
    private val seen = LinkedHashSet<String>()
    @Synchronized override fun emitBatch(batchJson: String, promise: Promise) {
        try {
            require(batchJson.toByteArray().size <= 32768)
            val events = JSONArray(batchJson); require(events.length() <= 20)
            val fields = setOf("eventId", "layer", "level", "module", "event", "timestamp", "apiId", "flowId", "requestId", "spanId", "parentSpanId", "transport", "durationMs", "code", "pageId")
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i); val id = event.getString("eventId")
                if (!seen.add(id)) continue
                if (seen.size > 1000) seen.remove(seen.first())
                val safe = JSONObject()
                event.keys().asSequence().filter { it in fields }.forEach { key ->
                    val value = event.get(key)
                    safe.put(key, if (value is String) value.take(160).replace(Regex("\\b[0-9]{11,19}\\b"), "[REDACTED]") else if (value is Number) value else JSONObject.NULL)
                }
                val line = safe.toString()
                when (event.optString("level")) {
                    "error" -> MLog.e("EntryTask.RN", line)
                    "warn" -> MLog.w("EntryTask.RN", line)
                    else -> if (BuildConfig.DEBUG) MLog.i("EntryTask.RN", line)
                }
            }
            promise.resolve(BridgeReply.ok(true))
        } catch (e: Exception) {
            MLog.e("EntryTask.Bridge", "telemetry.batch.failed code=INVALID_LOG_BATCH exception=${e.javaClass.simpleName}")
            promise.resolve(BridgeReply.error("INVALID_LOG_BATCH"))
        }
    }
}
