package dev.schrammel.personal_running_tracker.data

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@TypeConverters(Converters::class)
@Database(entities = [RunEntity::class, GpsEntity::class, SensorEntity::class, EstimatedPositionEntity::class, RunDiagnostic::class, SplitEntity::class, RunStateEntity::class, RunEvent::class, PersonalRecordCache::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun gpsDao(): GpsDao
    abstract fun sensorDao(): SensorDao
    abstract fun estimatedPositionDao(): EstimatedPositionDao
    abstract fun runDiagnosticDao(): RunDiagnosticDao
    abstract fun splitDao(): SplitDao
    abstract fun runStateDao(): RunStateDao
    abstract fun runEventDao(): RunEventDao
    abstract fun personalRecordCacheDao(): PersonalRecordCacheDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Application): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { instance ->
                    INSTANCE = instance
                }
            }
        }

        /** Closes and clears the singleton so the underlying files can be replaced (backup restore). */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private fun buildDatabase(context: Application): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "personal_running_tracker.db"
            )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
        }

        /** Preserves existing data: new columns default to 0, new tables start empty. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN runningDurationSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runs ADD COLUMN walkingDurationSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS run_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "runId INTEGER NOT NULL, timestamp INTEGER NOT NULL, " +
                        "label TEXT NOT NULL, type TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS pr_cache (" +
                        "distanceMeters REAL NOT NULL, bestSeconds INTEGER NOT NULL, " +
                        "runId INTEGER NOT NULL, updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(distanceMeters))"
                )
            }
        }
    }
}
