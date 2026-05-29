package com.example.lura.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.lura.playback.SleepPlaybackController

class AlarmEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM_EVENT) return

        val eventType = runCatching {
            intent.getStringExtra(EXTRA_EVENT_TYPE)?.let(AlarmEventType::valueOf)
        }.getOrNull()

        when (eventType) {
            AlarmEventType.SLEEP_START -> startSleepFlow(context, intent)
            AlarmEventType.WAKE_ALARM -> startWakeAlarm(context, intent)
            null -> Unit
        }
    }

    private fun startSleepFlow(context: Context, intent: Intent) {
        SleepPlaybackController.startScheduledAlarm(
            context = context,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
        )
    }

    private fun startWakeAlarm(context: Context, intent: Intent) {
        AlarmTriggerDispatcher.trigger(
            context = context,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            alarmTitle = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_TITLE).orEmpty(),
            showRingingScreen = true,
            alarmHour = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_HOUR),
            alarmMinute = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_MINUTE),
            triggerAtEpochMillis = intent.getLongExtra(
                AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS,
                0L
            )
        )
    }

    private fun Intent.optionalIntExtra(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null

    companion object {
        const val ACTION_ALARM_EVENT = "com.example.lura.alarm.action.EVENT"
        const val EXTRA_EVENT_TYPE = "alarm.extra.EVENT_TYPE"
    }
}
