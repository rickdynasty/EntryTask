package com.entry.demo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CheckinLedgerTest {
    private var date = "2026-10-01"

    private fun ledger() =
        CheckinLedger(
            { date },
            { JSONObject().put("rewardDays", org.json.JSONArray(listOf(3, 7))) },
        )

    @Test
    fun rewardsOnlyDayThreeAndSevenAndStartsNewCycle() {
        val state = JSONObject()
        val ledger = ledger()
        for (day in 1..8) {
            date = "2026-10-${day.toString().padStart(2, '0')}"
            val result = ledger.submit(state, date)
            assertEquals(if (day == 8) 1 else day, result.getInt("consecutiveDays"))
            assertEquals(
                when (day) {
                    3 -> 30
                    7 -> 100
                    else -> 0
                },
                result.getInt("awardedEnergy"),
            )
            val duplicate = ledger.submit(state, date)
            assertTrue(duplicate.getBoolean("alreadyChecked"))
            assertEquals(0, duplicate.getInt("awardedEnergy"))
        }
        assertEquals(130, state.getInt("energy"))
        assertEquals(2, state.getJSONArray("energyRecords").length())
    }

    @Test
    fun gapStartsAgainAndStaleDateDoesNotMutate() {
        val state = JSONObject()
        val ledger = ledger()
        ledger.submit(state, date)
        date = "2026-10-03"
        val before = state.toString()
        try {
            ledger.submit(state, "2026-10-02")
            fail("must reject stale business day")
        } catch (e: DemoFailure) {
            assertEquals("BUSINESS_DATE_CHANGED", e.code)
        }
        assertEquals(before, state.toString())
        assertEquals(1, ledger.submit(state, date).getInt("consecutiveDays"))
    }

    @Test
    fun independentUserStatesNeverShareEnergyOrDates() {
        val a = JSONObject()
        val b = JSONObject()
        val ledger = ledger()
        ledger.submit(a, date)
        assertFalse(ledger.snapshot(b).getBoolean("checkedToday"))
        assertEquals(0, ledger.snapshot(b).getInt("energy"))
    }

    @Test
    fun calendarProjectsActualHistoryAcrossMonthBoundaryWithoutGuessing() {
        val state = JSONObject()
        val ledger = ledger()
        date = "2026-09-30"
        ledger.submit(state, date)
        date = "2026-10-02"
        ledger.submit(state, date)
        val rows = ledger.snapshot(state).getJSONArray("calendar")
        assertEquals(7, rows.length())
        assertEquals("2026-09-26", rows.getJSONObject(0).getString("date"))
        assertTrue(rows.getJSONObject(4).getBoolean("checked"))
        assertFalse(rows.getJSONObject(5).getBoolean("checked"))
        assertTrue(rows.getJSONObject(6).getBoolean("checked"))
    }
}
