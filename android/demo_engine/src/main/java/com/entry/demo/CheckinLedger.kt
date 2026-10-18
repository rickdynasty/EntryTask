package com.entry.demo

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

internal class DemoFailure(val code: String, val status: Int) : Exception(code)

/** Pure ledger transition; the interceptor commits the whole user state atomically afterwards. */
internal class CheckinLedger(private val today: () -> String, private val template: () -> JSONObject) {
    private fun formatter() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
    fun snapshot(state: JSONObject): JSONObject {
        val dates = state.optJSONArray("dates") ?: JSONArray()
        val history = (0 until dates.length()).map { dates.getString(it) }.toSet()
        val date = today()
        val format = formatter()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply { time = format.parse(date)!! }
        if (date !in history) calendar.add(Calendar.DAY_OF_YEAR, -1)
        var count = 0
        while (format.format(calendar.time) in history) { count++; calendar.add(Calendar.DAY_OF_YEAR, -1) }
        return template().put("businessDate", date).put("checkedToday", date in history)
            .put("consecutiveDays", if (count == 0) 0 else (count - 1) % 7 + 1)
            .put("energy", state.optInt("energy")).put("awardedEnergy", 0).put("alreadyChecked", false)
    }
    fun submit(state: JSONObject, expectedDate: String): JSONObject {
        if (expectedDate != today()) throw DemoFailure("BUSINESS_DATE_CHANGED", 409)
        val before = snapshot(state)
        if (before.getBoolean("checkedToday")) return before.put("alreadyChecked", true)
        val dates = state.optJSONArray("dates") ?: JSONArray()
        dates.put(today()); state.put("dates", dates)
        val after = snapshot(state)
        val reward = when (after.getInt("consecutiveDays")) { 3 -> 30; 7 -> 100; else -> 0 }
        state.put("energy", state.optInt("energy") + reward)
        val records = state.optJSONArray("energyRecords") ?: JSONArray()
        if (reward > 0) records.put(JSONObject().put("id", "energy-${today()}").put("title", "连续签到奖励").put("summary", "+$reward 能量值").put("date", today()))
        state.put("energyRecords", records)
        return snapshot(state).put("awardedEnergy", reward)
    }
}
