package com.nkhearn25.toiltracker

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ToilTrackerLogicTest {

    private val logic = ToilTrackerLogic(null)

    @Test
    fun `test initial loadData provides default values`() {
        val config = logic.loadData()
        assertEquals(21.0, config.contract_hours, 0.0)
        assertEquals(12, config.year_end_month)
        assertEquals(31, config.year_end_day)
        assertNotNull(config.default_week)
        assertEquals(7, config.default_week?.size)
        assertTrue(config.adjustments?.isEmpty() ?: false)
    }

    @Test
    fun `test calculateMetrics for perfect balance`() {
        val today = LocalDate.now()
        // Define a period of exactly 7 days
        val config = ToilTrackerLogic.Config(
            contract_hours = 35.0,
            start_date = today.minusDays(7).toString(),
            year_end_month = 12,
            year_end_day = 31,
            default_week = mutableMapOf(
                "Monday" to 7.0, "Tuesday" to 7.0, "Wednesday" to 7.0,
                "Thursday" to 7.0, "Friday" to 7.0, "Saturday" to 0.0, "Sunday" to 0.0
            ),
            adjustments = mutableMapOf()
        )

        val metrics = logic.calculateMetrics(config)

        // Days elapsed: 8 (from 7 days ago until today inclusive)
        // Weeks elapsed: (8-1)/7 = 1.0
        // Expected contracted: 35.0 * 1.0 = 35.0
        // Expected default: 1 full cycle of Mon-Sun + 1 day (today)
        // If today is Friday: 35 + 7 = 42
        // Balance = 42 - 35 = 7 (Correct, as we worked one extra day beyond the week of contracted hours)

        val bal = metrics["balance"] as Double
        val expectedDef = metrics["expected_default"] as Double
        val expectedCon = metrics["expected_contracted"] as Double
        assertEquals(expectedDef - expectedCon, bal, 0.1)
    }

    @Test
    fun `test calculateMetrics with overtime`() {
        val today = LocalDate.now()
        val startDate = today.minusDays(7)
        val config = ToilTrackerLogic.Config(
            contract_hours = 35.0,
            start_date = startDate.toString(),
            year_end_month = 12,
            year_end_day = 31,
            default_week = mutableMapOf(
                "Monday" to 7.0, "Tuesday" to 7.0, "Wednesday" to 7.0,
                "Thursday" to 7.0, "Friday" to 7.0, "Saturday" to 0.0, "Sunday" to 0.0
            ),
            adjustments = mutableMapOf(
                today.toString() to ToilTrackerLogic.Adjustment(2.0, "Extra work")
            )
        )

        val metrics = logic.calculateMetrics(config)
        val baseMetrics = logic.calculateMetrics(config.copy(adjustments = mutableMapOf()))

        assertEquals((baseMetrics["balance"] as Double) + 2.0, metrics["balance"] as Double, 0.1)
    }
}
