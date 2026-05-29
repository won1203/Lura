package com.example.lura.alarm

import android.content.Context

object AlarmRingingState {
    fun markRinging(
        context: Context,
        alarmId: String,
        triggerAtEpochMillis: Long
    ) {
        prefs(context).edit()
            .putBoolean(KEY_IS_RINGING, true)
            .putString(KEY_ALARM_ID, alarmId)
            .putLong(KEY_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
            .putLong(KEY_UPDATED_AT_EPOCH_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun markStopped(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_IS_RINGING, false)
            .remove(KEY_ALARM_ID)
            .remove(KEY_TRIGGER_AT_EPOCH_MILLIS)
            .putLong(KEY_UPDATED_AT_EPOCH_MILLIS, System.currentTimeMillis())
            .commit()
    }

    fun isCurrentRinging(
        context: Context,
        alarmId: String,
        triggerAtEpochMillis: Long
    ): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_IS_RINGING, false)) return false

        val updatedAtEpochMillis = prefs.getLong(KEY_UPDATED_AT_EPOCH_MILLIS, 0L)
        if (updatedAtEpochMillis > 0L &&
            System.currentTimeMillis() - updatedAtEpochMillis > STALE_RINGING_STATE_MS
        ) {
            return false
        }

        val currentAlarmId = prefs.getString(KEY_ALARM_ID, null)
        if (alarmId.isNotBlank() && currentAlarmId != alarmId) return false

        val currentTriggerAtEpochMillis = prefs.getLong(KEY_TRIGGER_AT_EPOCH_MILLIS, 0L)
        if (triggerAtEpochMillis > 0L &&
            currentTriggerAtEpochMillis > 0L &&
            currentTriggerAtEpochMillis != triggerAtEpochMillis
        ) {
            return false
        }

        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "alarm_ringing_state"
    private const val KEY_IS_RINGING = "is_ringing"
    private const val KEY_ALARM_ID = "alarm_id"
    private const val KEY_TRIGGER_AT_EPOCH_MILLIS = "trigger_at_epoch_millis"
    private const val KEY_UPDATED_AT_EPOCH_MILLIS = "updated_at_epoch_millis"
    private const val STALE_RINGING_STATE_MS = 10 * 60 * 1_000L + 30_000L
}
