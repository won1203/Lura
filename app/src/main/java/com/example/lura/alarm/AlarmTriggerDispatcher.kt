package com.example.lura.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object AlarmTriggerDispatcher {
    fun trigger(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        showRingingScreen: Boolean
    ) {
        val appContext = context.applicationContext
        val normalizedTitle = alarmTitle.ifBlank { null }

        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, AlarmRingingService::class.java)
                .setAction(AlarmRingingService.ACTION_START)
                .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, normalizedTitle)
        )

        if (showRingingScreen) {
            showRingingActivity(appContext, alarmId, normalizedTitle.orEmpty())
        }
    }

    fun showRingingActivity(
        context: Context,
        alarmId: String,
        alarmTitle: String
    ) {
        val appContext = context.applicationContext
        appContext.startActivity(
            Intent(appContext, AlarmRingingActivity::class.java)
                .setAction(AlarmRingingActivity.ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, alarmTitle)
        )
    }
}
