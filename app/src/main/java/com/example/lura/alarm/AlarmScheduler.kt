package com.example.lura.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lura.MainActivity
import com.example.lura.data.AlarmSchedule
import com.example.lura.data.SleepSession

object AlarmScheduler {
    fun schedule(
        context: Context,
        alarm: AlarmSchedule,
        sleepSession: SleepSession
    ): Boolean {
        if (sleepSession.targetAlarmAtEpochMillis <= System.currentTimeMillis()) {
            return false
        }

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = createAlarmOperation(appContext, alarm, PendingIntent.FLAG_UPDATE_CURRENT)
        val showIntent = createShowIntent(appContext, alarm)

        return runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(sleepSession.targetAlarmAtEpochMillis, showIntent),
                operation
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule exact alarm: ${alarm.id}", error)
            operation.cancel()
        }.isSuccess
    }

    fun cancel(context: Context, alarmId: String) {
        val appContext = context.applicationContext
        val operation = createAlarmOperation(appContext, alarmId, PendingIntent.FLAG_NO_CREATE) ?: return
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(operation)
        operation.cancel()
    }

    fun cancelAll(context: Context, alarms: List<AlarmSchedule>) {
        alarms.forEach { cancel(context, it.id) }
    }

    private fun createAlarmOperation(
        context: Context,
        alarm: AlarmSchedule,
        pendingIntentFlag: Int
    ): PendingIntent =
        requireNotNull(createAlarmOperation(context, alarm.id, pendingIntentFlag, alarm.soundTitle))

    private fun createAlarmOperation(
        context: Context,
        alarmId: String,
        pendingIntentFlag: Int,
        alarmTitle: String = ""
    ): PendingIntent? {
        val intent = Intent(context, AlarmEventReceiver::class.java)
            .setAction(AlarmEventReceiver.ACTION_ALARM_TRIGGERED)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, alarmTitle)

        return PendingIntent.getBroadcast(
            context,
            alarmId.stableRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or pendingIntentFlag
        )
    }

    private fun createShowIntent(context: Context, alarm: AlarmSchedule): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            alarm.id.stableRequestCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun String.stableRequestCode(): Int =
        hashCode()

    private const val TAG = "AlarmScheduler"
}
