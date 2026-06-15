package com.nkhearn25.toiltracker.data

import androidx.room.*

@Dao
interface ToilTrackerDao {
    @Query("SELECT * FROM config WHERE id = 0")
    suspend fun getConfig(): ConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ConfigEntity)

    @Query("SELECT * FROM adjustments")
    suspend fun getAllAdjustments(): List<AdjustmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAdjustment(adjustment: AdjustmentEntity)

    @Query("DELETE FROM adjustments WHERE date = :date")
    suspend fun deleteAdjustment(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAdjustments(adjustments: List<AdjustmentEntity>)
}
