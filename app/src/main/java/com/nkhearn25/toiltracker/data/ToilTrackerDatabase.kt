package com.nkhearn25.toiltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ConfigEntity::class, AdjustmentEntity::class], version = 1)
abstract class ToilTrackerDatabase : RoomDatabase() {
    abstract fun toilTrackerDao(): ToilTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: ToilTrackerDatabase? = null

        fun getDatabase(context: Context): ToilTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ToilTrackerDatabase::class.java,
                    "toil_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
