package com.nkhearn25.toiltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val id: Int = 0,
    val contractHours: Double,
    val startDate: String,
    val yearEndMonth: Int,
    val yearEndDay: Int,
    val defaultWeekJson: String // Store as JSON for simplicity as it was before
)

@Entity(tableName = "adjustments")
data class AdjustmentEntity(
    @PrimaryKey val date: String,
    val hours: Double,
    val notes: String
)
