package com.example.lura.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.lura.alarm.AlarmRingingService

object SleepPlaybackController {
    fun start(context: Context, request: SleepPlaybackRequest) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, SleepPlaybackService::class.java)
            .setAction(SleepPlaybackService.ACTION_START)
        ContextCompat.startForegroundService(appContext, request.writeTo(intent))
    }

    fun startScheduledAlarm(context: Context, alarmId: String) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, SleepPlaybackService::class.java)
            .setAction(SleepPlaybackService.ACTION_START_FOR_ALARM)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, SleepPlaybackService::class.java)
                .setAction(SleepPlaybackService.ACTION_STOP)
        )
    }

    fun fadeOutAndComplete(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, SleepPlaybackService::class.java)
                .setAction(SleepPlaybackService.ACTION_FADE_OUT_AND_COMPLETE)
        )
    }
}
