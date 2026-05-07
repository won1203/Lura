package com.example.lura.data

import android.content.Context
import com.example.lura.data.local.LuraDatabase
import java.util.concurrent.Executors

object SaveAlarmAndStartSleepSessionProvider {
    @Volatile
    private var useCase: SaveAlarmAndStartSleepSession? = null

    fun get(context: Context): SaveAlarmAndStartSleepSession =
        useCase ?: synchronized(this) {
            useCase ?: createUseCase(context.applicationContext).also { useCase = it }
        }

    private fun createUseCase(context: Context): SaveAlarmAndStartSleepSession =
        RoomSaveAlarmAndStartSleepSession(
            database = LuraDatabase.getInstance(context),
            alarmTargetTimeCalculator = AlarmTargetTimeCalculator(),
            diskExecutor = Executors.newSingleThreadExecutor()
        )
}
