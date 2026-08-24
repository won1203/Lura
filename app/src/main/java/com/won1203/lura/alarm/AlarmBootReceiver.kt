package com.won1203.lura.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in RESTORE_ACTIONS) return

        val pendingResult = goAsync()
        AlarmRescheduler.restoreEnabledAlarms(
            context = context,
            restorePlaybackInActiveWindow = action == Intent.ACTION_BOOT_COMPLETED,
            reconcileExpiredSessions = action != Intent.ACTION_TIME_CHANGED &&
                action != Intent.ACTION_TIMEZONE_CHANGED,
            onComplete = pendingResult::finish
        )
    }

    private companion object {
        val RESTORE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
