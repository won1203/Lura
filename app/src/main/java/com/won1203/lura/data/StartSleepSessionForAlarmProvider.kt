package com.won1203.lura.data

import android.content.Context
import com.won1203.lura.data.local.LuraDatabase
import java.util.concurrent.Executors

object StartSleepSessionForAlarmProvider {
    @Volatile
    private var useCase: StartSleepSessionForAlarm? = null

    fun get(context: Context): StartSleepSessionForAlarm =
        useCase ?: synchronized(this) {
            useCase ?: createUseCase(context.applicationContext).also { useCase = it }
        }

    private fun createUseCase(context: Context): StartSleepSessionForAlarm =
        RoomStartSleepSessionForAlarm(
            database = LuraDatabase.getInstance(context),
            alarmTargetTimeCalculator = AlarmTargetTimeCalculator(),
            diskExecutor = Executors.newSingleThreadExecutor()
        )
}
