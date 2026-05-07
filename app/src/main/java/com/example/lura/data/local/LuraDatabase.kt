package com.example.lura.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AlarmEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(AlarmConverters::class)
abstract class LuraDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

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
            ).build()

        private const val DATABASE_NAME = "lura.db"
    }
}
