package com.nkhearn25.toiltracker

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ToilTrackerLogic(private val context: Context) {
    private val dbFile = File(context.filesDir, "hour_tracker_db.json")
    private val gson = Gson()
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    data class Adjustment(val hours: Double, val notes: String)
    data class Config(
        var contract_hours: Double,
        var start_date: String,
        var year_end_month: Int,
        var year_end_day: Int,
        var default_week: MutableMap<String, Double>,
        var adjustments: MutableMap<String, Adjustment>
    )

    fun loadData(): Config {
        val today = LocalDate.now()
        val defaultStartDate = "${today.year}-01-01"
        val fallbackConfig = Config(
            contract_hours = 21.0,
            start_date = defaultStartDate,
            year_end_month = 12,
            year_end_day = 31,
            default_week = days.associateWith { 0.0 }.toMutableMap(),
            adjustments = mutableMapOf()
        )

        if (!dbFile.exists()) return fallbackConfig

        return try {
            val json = dbFile.readText()
            val data = gson.fromJson(json, Config::class.java)

            // Migration / safety checks
            var updated = false
            if (data.default_week == null) {
                data.default_week = fallbackConfig.default_week
                updated = true
            }
            for (day in days) {
                if (!data.default_week.containsKey(day)) {
                    data.default_week[day] = 0.0
                    updated = true
                }
            }
            if (data.adjustments == null) {
                data.adjustments = mutableMapOf()
                updated = true
            }
            if (updated) saveData(data)
            data
        } catch (e: Exception) {
            fallbackConfig
        }
    }

    fun saveData(config: Config) {
        dbFile.writeText(gson.toJson(config))
    }

    fun updateConfig(
        contractHours: Double,
        startDate: String,
        endMonth: Int,
        endDay: Int,
        defaultWeekJson: String
    ): Config {
        val config = loadData()
        config.contract_hours = contractHours
        config.start_date = startDate
        config.year_end_month = endMonth
        config.year_end_day = endDay

        val type = object : TypeToken<MutableMap<String, Double>>() {}.type
        val newDefaultWeek: MutableMap<String, Double>? = gson.fromJson(defaultWeekJson, type)
        if (newDefaultWeek != null) {
            config.default_week = newDefaultWeek
        }

        saveData(config)
        return config
    }

    fun saveAdjustment(date: String, offset: Double, note: String): Config {
        val config = loadData()
        if (offset == 0.0) {
            config.adjustments.remove(date)
        } else {
            config.adjustments[date] = Adjustment(offset, note)
        }
        saveData(config)
        return config
    }

    fun deleteAdjustment(date: String): Config {
        val config = loadData()
        config.adjustments.remove(date)
        saveData(config)
        return config
    }

    fun calculateMetrics(config: Config): Map<String, Any> {
        val targetDate = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val startDate = try {
            LocalDate.parse(config.start_date, formatter)
        } catch (e: Exception) {
            LocalDate.of(targetDate.year, 1, 1)
        }

        var endDate = try {
            val yearMonth = YearMonth.of(targetDate.year, config.year_end_month)
            yearMonth.atDay(config.year_end_day.coerceIn(1, yearMonth.lengthOfMonth()))
        } catch (e: Exception) {
            LocalDate.of(targetDate.year, config.year_end_month, 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth())
        }

        if (endDate.isBefore(startDate)) {
            endDate = endDate.plusYears(1)
        }

        val calcEndToday = if (targetDate.isBefore(endDate)) targetDate else endDate
        val finalCalcEndToday = if (calcEndToday.isBefore(startDate)) startDate else calcEndToday

        val daysElapsed = ChronoUnit.DAYS.between(startDate, finalCalcEndToday) + 1
        val weeksElapsed = daysElapsed.toDouble() / 7.0

        val weeklyContract = config.contract_hours
        val expectedContractedToday = weeklyContract * weeksElapsed

        var expectedDefaultWorkedToday = 0.0
        var currentDay = startDate
        while (!currentDay.isAfter(finalCalcEndToday)) {
            val dayName = days[if (currentDay.dayOfWeek.value == 7) 6 else currentDay.dayOfWeek.value - 1]
            expectedDefaultWorkedToday += config.default_week[dayName] ?: 0.0
            currentDay = currentDay.plusDays(1)
        }

        var totalAdjustmentsToday = 0.0
        val adjustmentsInPeriod = mutableListOf<Map<String, Any>>()

        for ((dateStr, adj) in config.adjustments) {
            try {
                val adjDate = LocalDate.parse(dateStr, formatter)
                if (!adjDate.isBefore(startDate) && !adjDate.isAfter(endDate)) {
                    val valHours = adj.hours
                    adjustmentsInPeriod.add(mapOf(
                        "date" to dateStr,
                        "adjustment" to valHours,
                        "note" to adj.notes
                    ))

                    if (!adjDate.isAfter(finalCalcEndToday)) {
                        totalAdjustmentsToday += valHours
                    }
                }
            } catch (e: Exception) { continue }
        }

        val actualWorkedToday = expectedDefaultWorkedToday + totalAdjustmentsToday
        val runningBalance = actualWorkedToday - expectedContractedToday

        val daysTotal = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val weeksTotal = daysTotal.toDouble() / 7.0
        val expectedContractedYe = weeklyContract * weeksTotal

        var expectedDefaultWorkedYe = 0.0
        currentDay = startDate
        while (!currentDay.isAfter(endDate)) {
            val dayName = days[if (currentDay.dayOfWeek.value == 7) 6 else currentDay.dayOfWeek.value - 1]
            expectedDefaultWorkedYe += config.default_week[dayName] ?: 0.0
            currentDay = currentDay.plusDays(1)
        }

        val totalAdjustmentsYe = adjustmentsInPeriod.sumOf { it["adjustment"] as Double }
        val actualWorkedYe = expectedDefaultWorkedYe + totalAdjustmentsYe
        val forecastBalance = actualWorkedYe - expectedContractedYe

        adjustmentsInPeriod.sortByDescending { it["date"] as String }

        val chartData = mutableListOf<Map<String, Any>>()
        var runDt = startDate
        var tempContractedAccum = 0.0
        var tempWorkedAccum = 0.0
        val dailyContractRate = weeklyContract / 7.0

        while (!runDt.isAfter(finalCalcEndToday)) {
            tempContractedAccum += dailyContractRate
            val dayName = days[if (runDt.dayOfWeek.value == 7) 6 else runDt.dayOfWeek.value - 1]
            val dayBase = config.default_week[dayName] ?: 0.0
            val dateStr = runDt.format(formatter)

            val adjustmentVal = config.adjustments[dateStr]?.hours ?: 0.0
            tempWorkedAccum += (dayBase + adjustmentVal)

            chartData.add(mapOf(
                "date" to dateStr,
                "contracted" to Math.round(tempContractedAccum * 10.0) / 10.0,
                "worked" to Math.round(tempWorkedAccum * 10.0) / 10.0
            ))
            runDt = runDt.plusDays(1)
        }

        return mapOf(
            "start_date" to startDate.format(formatter),
            "end_date" to endDate.format(formatter),
            "days_elapsed" to daysElapsed,
            "cycle_span_days" to daysTotal,
            "weeks_elapsed" to Math.round(weeksElapsed * 100.0) / 100.0,
            "expected_contracted" to Math.round(expectedContractedToday * 10.0) / 10.0,
            "expected_default" to Math.round(expectedDefaultWorkedToday * 10.0) / 10.0,
            "adjustments_total" to Math.round(totalAdjustmentsToday * 10.0) / 10.0,
            "actual_worked" to Math.round(actualWorkedToday * 10.0) / 10.0,
            "balance" to Math.round(runningBalance * 10.0) / 10.0,
            "forecast_balance" to Math.round(forecastBalance * 10.0) / 10.0,
            "adjustments_list" to adjustmentsInPeriod,
            "chart_data" to chartData
        )
    }
}
