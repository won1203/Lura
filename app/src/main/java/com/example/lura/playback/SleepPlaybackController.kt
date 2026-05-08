package com.example.lura.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object SleepPlaybackController {
    fun start(context: Context, request: SleepPlaybackRequest) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, SleepPlaybackService::class.java)
            .setAction(SleepPlaybackService.ACTION_START)
        ContextCompat.startForegroundService(appContext, request.writeTo(intent))
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        appContext.startService(
            Intent(appContext, SleepPlaybackService::class.java)
                .setAction(SleepPlaybackService.ACTION_STOP)
        )
    }
}
