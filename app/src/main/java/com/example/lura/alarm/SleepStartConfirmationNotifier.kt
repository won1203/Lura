package com.example.lura.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.lura.MainActivity
import com.example.lura.R

object SleepStartConfirmationNotifier {
    fun show(
        context: Context,
        alarmId: String,
        categoryName: String,
        wakeHour: Int?,
        wakeMinute: Int?
    ) {
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(appContext, notificationManager)

        val wakeTime = if (wakeHour != null && wakeMinute != null) {
            appContext.getString(R.string.alarm_time_format, wakeHour, wakeMinute)
        } else {
            appContext.getString(R.string.sleep_start_confirmation_unknown_wake_time)
        }
        val playbackCategory = categoryName.ifBlank {
            appContext.getString(R.string.sleep_start_confirmation_default_category)
        }
        val message = appContext.getString(
            R.string.sleep_start_confirmation_message,
            playbackCategory,
            wakeTime
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play_arrow_24)
            .setContentTitle(appContext.getString(R.string.sleep_start_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(createContentIntent(appContext))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_play_arrow_24,
                appContext.getString(R.string.sleep_start_confirmation_start),
                createActionIntent(
                    context = appContext,
                    alarmId = alarmId,
                    action = AlarmEventReceiver.ACTION_START_SLEEP_PLAYBACK,
                    requestCodeOffset = START_ACTION_REQUEST_CODE_OFFSET
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(R.string.sleep_start_confirmation_skip),
                createActionIntent(
                    context = appContext,
                    alarmId = alarmId,
                    action = AlarmEventReceiver.ACTION_DISABLE_SLEEP_ALARM,
                    requestCodeOffset = DISABLE_ACTION_REQUEST_CODE_OFFSET
                )
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createActionIntent(
        context: Context,
        alarmId: String,
        action: String,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, AlarmEventReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmRingingService.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            (alarmId.hashCode() * REQUEST_CODE_MULTIPLIER) + requestCodeOffset,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createContentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel(
        context: Context,
        notificationManager: NotificationManager
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sleep_start_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                context.getString(R.string.sleep_start_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private const val CHANNEL_ID = "sleep_start_confirmation"
    private const val NOTIFICATION_ID = 2101
    private const val REQUEST_CODE_MULTIPLIER = 31
    private const val START_ACTION_REQUEST_CODE_OFFSET = 20_000
    private const val DISABLE_ACTION_REQUEST_CODE_OFFSET = 20_001
    private const val CONTENT_REQUEST_CODE = 20_002
}
