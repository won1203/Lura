package com.example.lura.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.lura.MainActivity
import com.example.lura.data.AlarmSchedule
import com.example.lura.data.AlarmTargetTimeCalculator
import com.example.lura.data.SleepWindow

data class AlarmSchedulePlan(
    val sleepWindow: SleepWindow,
    val sleepStartScheduled: Boolean,
    val wakeAlarmScheduled: Boolean
)

object AlarmScheduler {
    fun schedule(
        context: Context,
        alarm: AlarmSchedule,
        skipSleepStart: Boolean = false,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): AlarmSchedulePlan? {
        val sleepWindow = alarmTargetTimeCalculator.nextSleepWindow(alarm, nowEpochMillis)
        if (sleepWindow.wakeAtEpochMillis <= nowEpochMillis) {
            return null
        }

        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = createShowIntent(appContext, alarm)
        var sleepStartScheduled = false

        if (!skipSleepStart && sleepWindow.sleepStartAtEpochMillis > nowEpochMillis) {
            val sleepStartOperation = createAlarmOperation(
                context = appContext,
                alarm = alarm,
                eventType = AlarmEventType.SLEEP_START,
                pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT
            )
            val sleepStartSuccess = runCatching {
                scheduleExact(
                    alarmManager = alarmManager,
                    triggerAtEpochMillis = sleepWindow.sleepStartAtEpochMillis,
                    operation = sleepStartOperation
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to schedule sleep start: ${alarm.id}", error)
                sleepStartOperation.cancel()
            }.isSuccess

            if (!sleepStartSuccess) {
                return null
            }
            sleepStartScheduled = true
        }

        cancelWakeBroadcastOperation(appContext, alarmManager, alarm.id)
        cancelWakeActivityOperation(appContext, alarmManager, alarm.id)
        val wakeOperation = createAlarmOperation(
            context = appContext,
            alarm = alarm,
            eventType = AlarmEventType.WAKE_ALARM,
            pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT,
            triggerAtEpochMillis = sleepWindow.wakeAtEpochMillis
        )
        val wakeSuccess = runCatching {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(sleepWindow.wakeAtEpochMillis, showIntent),
                wakeOperation
            )
        }.onFailure { error ->
            Log.e(TAG, "Failed to schedule wake alarm: ${alarm.id}", error)
            wakeOperation.cancel()
        }.isSuccess

        if (!wakeSuccess) {
            cancel(context, alarm.id)
            return null
        }

        return AlarmSchedulePlan(
            sleepWindow = sleepWindow,
            sleepStartScheduled = sleepStartScheduled,
            wakeAlarmScheduled = true
        )
    }

    fun cancel(context: Context, alarmId: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        AlarmEventType.values().forEach { eventType ->
            val operation = createAlarmOperation(
                context = appContext,
                alarmId = alarmId,
                eventType = eventType,
                pendingIntentFlag = PendingIntent.FLAG_NO_CREATE
            )
            operation?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            if (eventType == AlarmEventType.WAKE_ALARM) {
                cancelWakeActivityOperation(appContext, alarmManager, alarmId)
            }
        }
        createSnoozeOperation(
            context = appContext,
            alarmId = alarmId,
            alarmTitle = "",
            triggerAtEpochMillis = 0L,
            pendingIntentFlag = PendingIntent.FLAG_NO_CREATE
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
        cancelLegacySnoozeBroadcastOperation(appContext, alarmManager, alarmId)
    }

    fun cancelAll(context: Context, alarms: List<AlarmSchedule>) {
        alarms.forEach { cancel(context, it.id) }
    }

    private fun scheduleExact(
        alarmManager: AlarmManager,
        triggerAtEpochMillis: Long,
        operation: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMillis,
                operation
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMillis,
                operation
            )
        }
    }

    private fun createAlarmOperation(
        context: Context,
        alarm: AlarmSchedule,
        eventType: AlarmEventType,
        pendingIntentFlag: Int
    ): PendingIntent =
        requireNotNull(
            createAlarmOperation(
                context = context,
                alarmId = alarm.id,
                eventType = eventType,
                pendingIntentFlag = pendingIntentFlag,
                alarmTitle = alarm.soundTitle,
                categoryName = alarm.categoryName,
                wakeHour = alarm.hour,
                wakeMinute = alarm.minute
            )
        )

    private fun createAlarmOperation(
        context: Context,
        alarmId: String,
        eventType: AlarmEventType,
        pendingIntentFlag: Int,
        alarmTitle: String = "",
        categoryName: String = "",
        wakeHour: Int? = null,
        wakeMinute: Int? = null
    ): PendingIntent? =
        createBroadcastAlarmOperation(
            context = context,
            alarmId = alarmId,
            eventType = eventType,
            pendingIntentFlag = pendingIntentFlag,
            alarmTitle = alarmTitle,
            categoryName = categoryName,
            wakeHour = wakeHour,
            wakeMinute = wakeMinute
        )

    private fun createBroadcastAlarmOperation(
        context: Context,
        alarmId: String,
        eventType: AlarmEventType,
        pendingIntentFlag: Int,
        alarmTitle: String = "",
        triggerAtEpochMillis: Long = 0L,
        alarmHour: Int? = null,
        alarmMinute: Int? = null,
        categoryName: String = "",
        wakeHour: Int? = null,
        wakeMinute: Int? = null,
        requestCodeOverride: Int? = null
    ): PendingIntent? {
        val intent = Intent(context, AlarmEventReceiver::class.java)
            .setAction(AlarmEventReceiver.ACTION_ALARM_EVENT)
            .putExtra(AlarmEventReceiver.EXTRA_EVENT_TYPE, eventType.name)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        alarmHour?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_HOUR, it) }
        alarmMinute?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_MINUTE, it) }
        if (categoryName.isNotBlank()) {
            intent.putExtra(AlarmEventReceiver.EXTRA_CATEGORY_NAME, categoryName)
        }
        wakeHour?.let { intent.putExtra(AlarmEventReceiver.EXTRA_WAKE_HOUR, it) }
        wakeMinute?.let { intent.putExtra(AlarmEventReceiver.EXTRA_WAKE_MINUTE, it) }

        return PendingIntent.getBroadcast(
            context,
            requestCodeOverride ?: stableRequestCode(alarmId, eventType),
            intent,
            PendingIntent.FLAG_IMMUTABLE or pendingIntentFlag
        )
    }

    private fun cancelWakeBroadcastOperation(
        context: Context,
        alarmManager: AlarmManager,
        alarmId: String
    ) {
        createBroadcastAlarmOperation(
            context = context,
            alarmId = alarmId,
            eventType = AlarmEventType.WAKE_ALARM,
            pendingIntentFlag = PendingIntent.FLAG_NO_CREATE
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun cancelWakeActivityOperation(
        context: Context,
        alarmManager: AlarmManager,
        alarmId: String
    ) {
        createWakeActivityOperation(
            context = context,
            alarmId = alarmId,
            alarmTitle = "",
            triggerAtEpochMillis = 0L,
            alarmHour = null,
            alarmMinute = null,
            requestCode = stableRequestCode(alarmId, AlarmEventType.WAKE_ALARM),
            pendingIntentFlag = PendingIntent.FLAG_NO_CREATE
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun createAlarmOperation(
        context: Context,
        alarm: AlarmSchedule,
        eventType: AlarmEventType,
        pendingIntentFlag: Int,
        triggerAtEpochMillis: Long
    ): PendingIntent =
        if (eventType == AlarmEventType.WAKE_ALARM) {
            requireNotNull(
                createWakeActivityOperation(
                    context = context,
                    alarmId = alarm.id,
                    alarmTitle = alarm.soundTitle,
                    triggerAtEpochMillis = triggerAtEpochMillis,
                    alarmHour = alarm.hour,
                    alarmMinute = alarm.minute,
                    requestCode = stableRequestCode(alarm.id, eventType),
                    pendingIntentFlag = pendingIntentFlag
                )
            )
        } else {
            createAlarmOperation(
                context = context,
                alarm = alarm,
                eventType = eventType,
                pendingIntentFlag = pendingIntentFlag
            )
        }

    fun scheduleSnooze(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        triggerAtEpochMillis: Long
    ) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = triggerAtEpochMillis
        }
        val operation = createSnoozeOperation(
            context = appContext,
            alarmId = alarmId,
            alarmTitle = alarmTitle,
            triggerAtEpochMillis = triggerAtEpochMillis,
            pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT,
            alarmHour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            alarmMinute = calendar.get(java.util.Calendar.MINUTE)
        ) ?: return

        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtEpochMillis, createShowIntent(appContext)),
            operation
        )
    }

    private fun createSnoozeOperation(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        triggerAtEpochMillis: Long,
        pendingIntentFlag: Int,
        alarmHour: Int? = null,
        alarmMinute: Int? = null
    ): PendingIntent? =
        createWakeActivityOperation(
            context = context,
            alarmId = alarmId,
            alarmTitle = alarmTitle,
            triggerAtEpochMillis = triggerAtEpochMillis,
            alarmHour = alarmHour,
            alarmMinute = alarmMinute,
            requestCode = stableSnoozeRequestCode(alarmId),
            pendingIntentFlag = pendingIntentFlag
        )

    private fun cancelLegacySnoozeBroadcastOperation(
        context: Context,
        alarmManager: AlarmManager,
        alarmId: String
    ) {
        createBroadcastAlarmOperation(
            context = context,
            alarmId = alarmId,
            eventType = AlarmEventType.WAKE_ALARM,
            pendingIntentFlag = PendingIntent.FLAG_NO_CREATE,
            requestCodeOverride = stableSnoozeRequestCode(alarmId)
        )?.let { operation ->
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    private fun createWakeActivityOperation(
        context: Context,
        alarmId: String,
        alarmTitle: String,
        triggerAtEpochMillis: Long,
        alarmHour: Int?,
        alarmMinute: Int?,
        requestCode: Int,
        pendingIntentFlag: Int
    ): PendingIntent? {
        val intent = Intent(context, AlarmRingingActivity::class.java)
            .setAction(AlarmRingingActivity.ACTION_TRIGGER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        alarmHour?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_HOUR, it) }
        alarmMinute?.let { intent.putExtra(AlarmRingingService.EXTRA_ALARM_MINUTE, it) }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or pendingIntentFlag,
            AlarmActivityPendingIntentOptions.bundle()
        )
    }

    private fun createShowIntent(context: Context, alarm: AlarmSchedule): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            stableRequestCode(alarm.id, AlarmEventType.WAKE_ALARM),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createShowIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            SNOOZE_SHOW_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stableRequestCode(alarmId: String, eventType: AlarmEventType): Int =
        (alarmId.hashCode() * REQUEST_CODE_MULTIPLIER) + eventType.ordinal

    private fun stableSnoozeRequestCode(alarmId: String): Int =
        (alarmId.hashCode() * REQUEST_CODE_MULTIPLIER) + SNOOZE_REQUEST_CODE_OFFSET

    private val alarmTargetTimeCalculator = AlarmTargetTimeCalculator()
    private const val REQUEST_CODE_MULTIPLIER = 31
    private const val SNOOZE_REQUEST_CODE_OFFSET = 10_000
    private const val SNOOZE_SHOW_REQUEST_CODE = 10_001
    private const val TAG = "AlarmScheduler"
}
