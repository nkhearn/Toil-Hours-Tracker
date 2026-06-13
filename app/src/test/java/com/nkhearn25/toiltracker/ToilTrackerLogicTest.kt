package com.nkhearn25.toiltracker

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class ToilTrackerLogicTest {

    @Test
    fun testInitialMetrics() {
        val logic = ToilTrackerLogic()
        val config = logic.loadData()
        val metrics = logic.calculateMetrics(config)

        assertNotNull(metrics["balance"])
        assertNotNull(metrics["forecast_balance"])
    }

    @Test
    fun testCalculateMetricsFixedSpan() {
        val logic = ToilTrackerLogic()
        val config = logic.loadData()

        config.contract_hours = 21.0
        config.start_date = "2024-01-01"
        config.year_end_month = 12
        config.year_end_day = 31
        config.default_week = mutableMapOf(
            "Monday" to 7.0,
            "Tuesday" to 7.0,
            "Wednesday" to 7.0,
            "Thursday" to 0.0,
            "Friday" to 0.0,
            "Saturday" to 0.0,
            "Sunday" to 0.0
        )

        // Overriding the 'today' logic in calculateMetrics is hard without more refactoring
        // but we can test the loadData and basic structure.
        // Given current implementation uses LocalDate.now(), we will test with relative dates
        // but ensure we cover exactly 7 days to avoid day-of-week variance.

        val today = LocalDate.now()
        val sevenDaysAgo = today.minusDays(6) // Today + 6 days ago = 7 days
        config.start_date = sevenDaysAgo.toString()

        // Exactly one week means one of each day of the week is counted.
        // Total default worked = sum of all days in default_week = 21.0
        // Total expected contract = 21.0 * (7/7) = 21.0
        // Balance should be 0.0

        val metrics = logic.calculateMetrics(config)
        val balance = metrics["balance"] as Double

        assertEquals(0.0, balance, 0.1)
    }
}
