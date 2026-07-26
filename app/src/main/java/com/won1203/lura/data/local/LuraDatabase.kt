package com.won1203.lura.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, SleepSessionEntity::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(AlarmConverters::class)
abstract class LuraDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        @Volatile
        private var instance: LuraDatabase? = null

        fun getInstance(context: Context): LuraDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        private fun buildDatabase(context: Context): LuraDatabase =
            Room.databaseBuilder(
                context,
                LuraDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .build()

        private const val DATABASE_NAME = "lura.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sleep_sessions` (
                        `sessionId` TEXT NOT NULL,
                        `alarmId` TEXT NOT NULL,
                        `sleepSoundId` TEXT NOT NULL,
                        `startedAtEpochMillis` INTEGER NOT NULL,
                        `targetAlarmAtEpochMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`sessionId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_status` ON `sleep_sessions` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sleep_sessions_alarmId` ON `sleep_sessions` (`alarmId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `soundObjectKey` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `sleepStartHour` INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE `alarms` ADD COLUMN `sleepStartMinute` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sleep_sessions` ADD COLUMN `categoryName` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    UPDATE `sleep_sessions`
                    SET `categoryName` = COALESCE(
                        (
                            SELECT `categoryName`
                            FROM `alarms`
                            WHERE `alarms`.`id` = `sleep_sessions`.`alarmId`
                        ),
                        ''
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
