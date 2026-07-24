package com.example.lura.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.lura.data.DisableAlarmAndCancelSleepSessionProvider
import com.example.lura.playback.SleepPlaybackController
import java.util.concurrent.Executors

class AlarmEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ALARM_EVENT -> handleAlarmEvent(context, intent)
            ACTION_START_SLEEP_PLAYBACK -> startConfirmedSleepFlow(context, intent)
            ACTION_DISABLE_SLEEP_ALARM -> disableSleepAlarm(context, intent)
        }
    }

    private fun handleAlarmEvent(context: Context, intent: Intent) {
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
        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
        if (alarmId.isBlank()) return

        SleepStartConfirmationNotifier.show(
            context = context,
            alarmId = alarmId,
            categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty(),
            wakeHour = intent.optionalIntExtra(EXTRA_WAKE_HOUR),
            wakeMinute = intent.optionalIntExtra(EXTRA_WAKE_MINUTE)
        )
    }

    private fun startConfirmedSleepFlow(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
        if (alarmId.isBlank()) return

        SleepStartConfirmationNotifier.cancel(context)
        SleepPlaybackController.startScheduledAlarm(
            context = context,
            alarmId = alarmId
        )
    }

    private fun disableSleepAlarm(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
        if (alarmId.isBlank()) return

        val appContext = context.applicationContext
        SleepStartConfirmationNotifier.cancel(appContext)
        AlarmScheduler.cancel(appContext, alarmId)

        val pendingResult = goAsync()
        receiverExecutor.execute {
            runCatching {
                val cancelledActivePlayback = DisableAlarmAndCancelSleepSessionProvider
                    .get(appContext)
                    .execute(alarmId)
                if (cancelledActivePlayback) {
                    SleepPlaybackController.stop(appContext)
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to disable skipped sleep alarm: $alarmId", error)
            }
            pendingResult.finish()
        }
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
        const val ACTION_START_SLEEP_PLAYBACK =
            "com.example.lura.alarm.action.START_SLEEP_PLAYBACK"
        const val ACTION_DISABLE_SLEEP_ALARM =
            "com.example.lura.alarm.action.DISABLE_SLEEP_ALARM"
        const val EXTRA_EVENT_TYPE = "alarm.extra.EVENT_TYPE"
        const val EXTRA_CATEGORY_NAME = "alarm.extra.CATEGORY_NAME"
        const val EXTRA_WAKE_HOUR = "alarm.extra.WAKE_HOUR"
        const val EXTRA_WAKE_MINUTE = "alarm.extra.WAKE_MINUTE"

        private val receiverExecutor = Executors.newSingleThreadExecutor()
        private const val TAG = "AlarmEventReceiver"
    }
}
