package com.example.lura.alarm

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

object AlarmTriggerDispatcher {
    fun trigger(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        showRingingScreen: Boolean,
        alarmHour: Int? = null,
        alarmMinute: Int? = null,
        triggerAtEpochMillis: Long = 0L
    ) {
        val appContext = context.applicationContext
        val normalizedTitle = alarmTitle.ifBlank { null }

        if (AlarmRingingState.isStopped(appContext, alarmId, triggerAtEpochMillis)) {
            Log.i(TAG, "Ignoring stopped alarm trigger: $alarmId")
            return
        }

        AlarmRingingState.markRinging(
            context = appContext,
            alarmId = alarmId,
            triggerAtEpochMillis = triggerAtEpochMillis
        )

        val serviceIntent = Intent(appContext, AlarmRingingService::class.java)
            .setAction(AlarmRingingService.ACTION_START)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, normalizedTitle)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        alarmHour?.let { serviceIntent.putExtra(AlarmRingingService.EXTRA_ALARM_HOUR, it) }
        alarmMinute?.let { serviceIntent.putExtra(AlarmRingingService.EXTRA_ALARM_MINUTE, it) }

        ContextCompat.startForegroundService(appContext, serviceIntent)
        AlarmRescheduler.rescheduleNext(appContext, alarmId)

        if (showRingingScreen) {
            showRingingActivity(
                context = appContext,
                alarmId = alarmId,
                alarmTitle = normalizedTitle.orEmpty(),
                alarmHour = alarmHour,
                alarmMinute = alarmMinute,
                triggerAtEpochMillis = triggerAtEpochMillis
            )
        }
    }

    fun showRingingActivity(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        alarmHour: Int? = null,
        alarmMinute: Int? = null,
        triggerAtEpochMillis: Long = 0L
    ) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, AlarmRingingActivity::class.java)
            .setAction(AlarmRingingActivity.ACTION_SHOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        alarmHour?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_HOUR, it) }
        alarmMinute?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_MINUTE, it) }

        runCatching {
            appContext.startActivity(intent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to show alarm ringing screen.", error)
        }
    }

    private const val TAG = "AlarmTriggerDispatcher"
}
