package com.nkhearn25.toiltracker

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nkhearn25.toiltracker.data.AdjustmentEntity
import com.nkhearn25.toiltracker.data.ConfigEntity
import com.nkhearn25.toiltracker.data.ToilTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ToilTrackerLogic(private val context: Context? = null) {
    private val dbFile = context?.let { File(it.filesDir, "hour_tracker_db.json") }
    private val gson = Gson()
    private val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    private val database = context?.let { ToilTrackerDatabase.getDatabase(it) }
    private val dao = database?.toilTrackerDao()

    data class Adjustment(val hours: Double, val notes: String)
    private data class InternalAdjustment(val date: LocalDate, val dateStr: String, val hours: Double, val notes: String)
    data class Config(
        var contract_hours: Double,
        var start_date: String,
        var year_end_month: Int,
        var year_end_day: Int,
        var default_week: MutableMap<String, Double>?,
        var adjustments: MutableMap<String, Adjustment>?
    )

    private fun getFallbackConfig(): Config {
        val today = LocalDate.now()
        val defaultStartDate = "${today.year}-01-01"
        return Config(
            contract_hours = 21.0,
            start_date = defaultStartDate,
            year_end_month = 12,
            year_end_day = 31,
            default_week = days.associateWith { 0.0 }.toMutableMap(),
            adjustments = mutableMapOf()
        )
    }

    suspend fun loadData(): Config = withContext(Dispatchers.IO) {
        val fallbackConfig = getFallbackConfig()

        // Try to migrate if JSON exists
        if (dbFile != null && dbFile.exists() && dao != null) {
            try {
                val json = dbFile.readText()
                val data = gson.fromJson(json, Config::class.java)
                if (data != null) {
                    // Migrate to Room
                    val configEntity = ConfigEntity(
                        contractHours = data.contract_hours,
                        startDate = data.start_date,
                        yearEndMonth = data.year_end_month,
                        yearEndDay = data.year_end_day,
                        defaultWeekJson = gson.toJson(data.default_week)
                    )
                    dao.saveConfig(configEntity)

                    val adjustments = data.adjustments?.map { (date, adj) ->
                        AdjustmentEntity(date, adj.hours, adj.notes)
                    } ?: emptyList()
                    dao.insertAllAdjustments(adjustments)

                    // Delete JSON file after successful migration
                    dbFile.delete()
                }
            } catch (e: Exception) {
                // Ignore migration errors, will use Room or fallback
            }
        }

        if (dao == null) return@withContext fallbackConfig

        val configEntity = dao.getConfig()
        val adjustmentsEntities = dao.getAllAdjustments()

        if (configEntity == null) return@withContext fallbackConfig

        val type = object : TypeToken<MutableMap<String, Double>>() {}.type
        val defaultWeek: MutableMap<String, Double> = gson.fromJson(configEntity.defaultWeekJson, type) ?: fallbackConfig.default_week!!

        val adjustments = adjustmentsEntities.associate {
            it.date to Adjustment(it.hours, it.notes)
        }.toMutableMap()

        Config(
            contract_hours = configEntity.contractHours,
            start_date = configEntity.startDate,
            year_end_month = configEntity.yearEndMonth,
            year_end_day = configEntity.yearEndDay,
            default_week = defaultWeek,
            adjustments = adjustments
        )
    }

    suspend fun updateConfig(
        contractHours: Double,
        startDate: String,
        endMonth: Int,
        endDay: Int,
        defaultWeekJson: String
    ): Config = withContext(Dispatchers.IO) {
        dao?.saveConfig(ConfigEntity(
            contractHours = contractHours,
            startDate = startDate,
            yearEndMonth = endMonth,
            yearEndDay = endDay,
            defaultWeekJson = defaultWeekJson
        ))
        loadData()
    }

    suspend fun saveAdjustment(date: String, offset: Double, note: String): Config = withContext(Dispatchers.IO) {
        if (offset == 0.0) {
            dao?.deleteAdjustment(date)
        } else {
            dao?.saveAdjustment(AdjustmentEntity(date, offset, note))
        }
        loadData()
    }

    suspend fun deleteAdjustment(date: String): Config = withContext(Dispatchers.IO) {
        dao?.deleteAdjustment(date)
        loadData()
    }

    suspend fun exportConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val configEntity = dao?.getConfig() ?: return@withContext false
        val json = gson.toJson(configEntity)
        try {
            context?.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importConfig(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dao = dao ?: return@withContext false
        try {
            val json = context?.contentResolver?.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val configEntity: ConfigEntity? = gson.fromJson(json, ConfigEntity::class.java)
            if (configEntity != null) {
                dao.saveConfig(configEntity)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun exportAdjustments(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val adjustments = dao?.getAllAdjustments() ?: return@withContext false
        val json = gson.toJson(adjustments)
        try {
            context?.contentResolver?.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importAdjustments(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = context?.contentResolver?.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            val type = object : TypeToken<List<AdjustmentEntity>>() {}.type
            val adjustments: List<AdjustmentEntity>? = gson.fromJson(json, type)
            if (adjustments != null) {
                dao?.insertAllAdjustments(adjustments)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
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
        val weeksElapsed = (daysElapsed - 1).toDouble() / 7.0

        val weeklyContract = config.contract_hours
        val expectedContractedToday = weeklyContract * weeksElapsed

        var expectedDefaultWorkedToday = 0.0
        var currentDay = startDate
        val defaultWeek = config.default_week.orEmpty()
        while (!currentDay.isAfter(finalCalcEndToday)) {
            val dayName = days[currentDay.dayOfWeek.value - 1]
            expectedDefaultWorkedToday += defaultWeek[dayName] ?: 0.0
            currentDay = currentDay.plusDays(1)
        }

        val adjustmentsInPeriod = config.adjustments.orEmpty().mapNotNull { (dateStr, adj) ->
            runCatching { LocalDate.parse(dateStr, formatter) }.getOrNull()?.let { adjDate ->
                if (!adjDate.isBefore(startDate) && !adjDate.isAfter(endDate)) {
                    InternalAdjustment(adjDate, dateStr, adj.hours, adj.notes)
                } else null
            }
        }.sortedByDescending { it.dateStr }

        val totalAdjustmentsToday = adjustmentsInPeriod
            .filter { !it.date.isAfter(finalCalcEndToday) }
            .sumOf { it.hours }

        val actualWorkedToday = expectedDefaultWorkedToday + totalAdjustmentsToday
        val runningBalance = actualWorkedToday - expectedContractedToday

        val daysTotal = ChronoUnit.DAYS.between(startDate, endDate) + 1
        val weeksTotal = (daysTotal - 1).toDouble() / 7.0
        val expectedContractedYe = weeklyContract * weeksTotal

        var expectedDefaultWorkedYe = 0.0
        currentDay = startDate
        while (!currentDay.isAfter(endDate)) {
            val dayName = days[currentDay.dayOfWeek.value - 1]
            expectedDefaultWorkedYe += defaultWeek[dayName] ?: 0.0
            currentDay = currentDay.plusDays(1)
        }

        val totalAdjustmentsYe = adjustmentsInPeriod.sumOf { it.hours }
        val actualWorkedYe = expectedDefaultWorkedYe + totalAdjustmentsYe
        val forecastBalance = actualWorkedYe - expectedContractedYe

        val chartData = mutableListOf<Map<String, Any>>()
        var runDt = startDate
        var tempContractedAccum = 0.0
        var tempWorkedAccum = 0.0
        val dailyContractRate = weeklyContract / 7.0

        while (!runDt.isAfter(endDate)) {
            tempContractedAccum += dailyContractRate
            val dayName = days[runDt.dayOfWeek.value - 1]
            val dayBase = defaultWeek[dayName] ?: 0.0
            val dateStr = runDt.format(formatter)

            val adjustmentVal = config.adjustments?.get(dateStr)?.hours ?: 0.0
            tempWorkedAccum += (dayBase + adjustmentVal)

            val workedToRecord = if (runDt.isAfter(finalCalcEndToday)) {
                Math.round(tempWorkedAccum * 10.0) / 10.0
            } else {
                Math.round(tempWorkedAccum * 10.0) / 10.0
            }

            chartData.add(mapOf(
                "date" to dateStr,
                "contracted" to Math.round(tempContractedAccum * 10.0) / 10.0,
                "worked" to workedToRecord
            ))
            runDt = runDt.plusDays(1)
        }

        val adjustmentsListForWebView = adjustmentsInPeriod.map {
            mapOf(
                "date" to it.dateStr,
                "adjustment" to it.hours,
                "note" to it.notes
            )
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
            "adjustments_list" to adjustmentsListForWebView,
            "chart_data" to chartData
        )
    }
}
