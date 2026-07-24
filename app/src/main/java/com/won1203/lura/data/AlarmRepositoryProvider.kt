package com.won1203.lura.data

import android.content.Context
import com.won1203.lura.data.local.LuraDatabase
import java.util.concurrent.Executors

object AlarmRepositoryProvider {
    @Volatile
    private var repository: AlarmRepository? = null

    fun get(context: Context): AlarmRepository =
        repository ?: synchronized(this) {
            repository ?: createRepository(context.applicationContext).also { repository = it }
        }

    private fun createRepository(context: Context): AlarmRepository {
        val database = LuraDatabase.getInstance(context)
        return RoomAlarmRepository(
            database = database,
            alarmDao = database.alarmDao(),
            diskExecutor = Executors.newSingleThreadExecutor()
        )
    }
}
