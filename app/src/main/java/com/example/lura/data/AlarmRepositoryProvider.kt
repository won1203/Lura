package com.example.lura.data

import android.content.Context
import com.example.lura.data.local.LuraDatabase
import java.util.concurrent.Executors

object AlarmRepositoryProvider {
    @Volatile
    private var repository: AlarmRepository? = null

    fun get(context: Context): AlarmRepository =
        repository ?: synchronized(this) {
            repository ?: createRepository(context.applicationContext).also { repository = it }
        }

    private fun createRepository(context: Context): AlarmRepository =
        RoomAlarmRepository(
            alarmDao = LuraDatabase.getInstance(context).alarmDao(),
            diskExecutor = Executors.newSingleThreadExecutor()
        )
}
