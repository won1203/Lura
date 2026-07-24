package com.won1203.lura.data

import android.content.Context
import com.won1203.lura.data.local.LuraDatabase
import java.util.concurrent.Executors

object DisableAlarmAndCancelSleepSessionProvider {
    @Volatile
    private var useCase: DisableAlarmAndCancelSleepSession? = null

    fun get(context: Context): DisableAlarmAndCancelSleepSession =
        useCase ?: synchronized(this) {
            useCase ?: createUseCase(context.applicationContext).also { useCase = it }
        }

    private fun createUseCase(context: Context): DisableAlarmAndCancelSleepSession =
        RoomDisableAlarmAndCancelSleepSession(
            database = LuraDatabase.getInstance(context),
            diskExecutor = Executors.newSingleThreadExecutor()
        )
}
