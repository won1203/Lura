package com.won1203.lura.alarm

import android.content.Context
import android.util.Log
import com.won1203.lura.data.AlarmRepositoryProvider
import com.won1203.lura.playback.SleepPlaybackController
import java.util.concurrent.Executors

object AlarmRescheduler {
    private val executor = Executors.newSingleThreadExecutor()

    fun rescheduleNext(context: Context, alarmId: String) {
        val appContext = context.applicationContext
        executor.execute {
            runCatching {
                val alarm = AlarmRepositoryProvider.get(appContext)
                    .getAlarms()
                    .firstOrNull { it.id == alarmId && it.isEnabled }
                    ?: return@execute

                AlarmScheduler.schedule(
                    context = appContext,
                    alarm = alarm,
                    nowEpochMillis = System.currentTimeMillis() + RESCHEDULE_OFFSET_MS
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to reschedule next alarm cycle: $alarmId", error)
            }
        }
    }

    fun restoreEnabledAlarms(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            runCatching {
                AlarmRepositoryProvider.get(appContext)
                    .getAlarms()
                    .filter { it.isEnabled }
                    .forEach { alarm ->
                        val plan = AlarmScheduler.schedule(appContext, alarm)
                        if (plan?.sleepWindow?.contains(System.currentTimeMillis()) == true) {
                            SleepPlaybackController.startScheduledAlarm(appContext, alarm.id)
                        }
                    }
            }.onFailure { error ->
                Log.e(TAG, "Failed to restore enabled alarms.", error)
            }
        }
    }

    private const val RESCHEDULE_OFFSET_MS = 1_000L
    private const val TAG = "AlarmRescheduler"
}
