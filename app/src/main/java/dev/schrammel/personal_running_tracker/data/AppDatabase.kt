package dev.schrammel.personal_running_tracker.data

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@TypeConverters(Converters::class)
@Database(entities = [RunEntity::class, GpsEntity::class, SensorEntity::class, EstimatedPositionEntity::class, RunDiagnostic::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun gpsDao(): GpsDao
    abstract fun sensorDao(): SensorDao
    abstract fun estimatedPositionDao(): EstimatedPositionDao
    abstract fun runDiagnosticDao(): RunDiagnosticDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Application): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { instance ->
                    INSTANCE = instance
                }
            }
        }

        private fun buildDatabase(context: Application): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "personal_running_tracker.db"
            ).fallbackToDestructiveMigration().build()
        }
    }
}
