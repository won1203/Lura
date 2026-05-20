package com.example.lura.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_ALARM_TRIGGERED) return

        AlarmTriggerDispatcher.trigger(
            context = context,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            alarmTitle = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_TITLE).orEmpty(),
            showRingingScreen = AlarmAppVisibility.isForeground
        )
    }

    companion object {
        const val ACTION_ALARM_TRIGGERED = "com.example.lura.alarm.action.TRIGGERED"
    }
}
