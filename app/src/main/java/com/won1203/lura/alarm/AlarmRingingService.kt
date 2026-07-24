package com.won1203.lura.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.won1203.lura.R
import com.won1203.lura.playback.SleepPlaybackController

class AlarmRingingService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var startRingingRunnable: Runnable? = null
    private var autoStopRunnable: Runnable? = null
    private var fallbackToneRunnable: Runnable? = null
    private var currentAlarmId: String = ""
    private var currentTriggerAtEpochMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarmFlow(intent)
            ACTION_SNOOZE -> snoozeAlarm(intent)
            ACTION_STOP -> stopAlarm(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        clearCallbacks()
        releasePlayer()
        super.onDestroy()
    }

    private fun startAlarmFlow(intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        val alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.alarm_ringing_default_title)
        val alarmHour = intent.optionalIntExtra(EXTRA_ALARM_HOUR)
        val alarmMinute = intent.optionalIntExtra(EXTRA_ALARM_MINUTE)
        val triggerAtEpochMillis = intent.getLongExtra(EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, 0L)
        if (AlarmRingingState.isStopped(this, alarmId, triggerAtEpochMillis)) {
            stopSelf()
            return
        }

        currentAlarmId = alarmId
        currentTriggerAtEpochMillis = triggerAtEpochMillis

        AlarmRingingState.markRinging(
            context = this,
            alarmId = alarmId,
            triggerAtEpochMillis = triggerAtEpochMillis
        )
        promoteToForeground(alarmId, alarmTitle, alarmHour, alarmMinute, triggerAtEpochMillis)
        SleepPlaybackController.fadeOutAndComplete(this)

        clearCallbacks()
        releasePlayer()
        startRingingRunnable = Runnable { startRinging() }.also {
            mainHandler.postDelayed(it, SLEEP_FADE_OUT_DURATION_MS)
        }
        autoStopRunnable = Runnable { stopAlarm() }.also {
            mainHandler.postDelayed(it, MAX_ALARM_RING_DURATION_MS)
        }
    }

    private fun startRinging() {
        if (mediaPlayer?.isPlaying == true) return

        val alarmUri = resolveAlarmUri()
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmRingingService, alarmUri)
                isLooping = true
                prepare()
                start()
            }
        }.onSuccess {
            mediaPlayer = it
        }.onFailure { error ->
            Log.e(TAG, "Failed to play system alarm tone. Starting fallback tone.", error)
            startFallbackTone()
        }
    }

    private fun snoozeAlarm(intent: Intent) {
        val triggerAtEpochMillis = System.currentTimeMillis() + SNOOZE_DURATION_MS
        AlarmScheduler.scheduleSnooze(
            context = this,
            alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty(),
            alarmTitle = intent.getStringExtra(EXTRA_ALARM_TITLE)
                ?.takeIf(String::isNotBlank)
                ?: getString(R.string.alarm_ringing_default_title),
            triggerAtEpochMillis = triggerAtEpochMillis
        )
        stopAlarm(intent)
    }

    private fun stopAlarm(intent: Intent? = null) {
        val stoppedAlarmId = intent?.getStringExtra(EXTRA_ALARM_ID)
            ?.takeIf(String::isNotBlank)
            ?: currentAlarmId
        val stoppedTriggerAtEpochMillis = intent
            ?.getLongExtra(EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, 0L)
            ?.takeIf { it > 0L }
            ?: currentTriggerAtEpochMillis
        clearCallbacks()
        releasePlayer()
        AlarmRingingState.markStopped(
            context = this,
            alarmId = stoppedAlarmId,
            triggerAtEpochMillis = stoppedTriggerAtEpochMillis
        )
        currentAlarmId = ""
        currentTriggerAtEpochMillis = 0L
        sendBroadcast(
            Intent(ACTION_ALARM_STOPPED)
                .setPackage(packageName)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun releasePlayer() {
        mediaPlayer?.run {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        stopFallbackTone()
    }

    private fun clearCallbacks() {
        startRingingRunnable?.let(mainHandler::removeCallbacks)
        autoStopRunnable?.let(mainHandler::removeCallbacks)
        fallbackToneRunnable?.let(mainHandler::removeCallbacks)
        startRingingRunnable = null
        autoStopRunnable = null
        fallbackToneRunnable = null
    }

    private fun startFallbackTone() {
        stopFallbackTone()
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, FALLBACK_TONE_VOLUME)
        val runnable = object : Runnable {
            override fun run() {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, FALLBACK_TONE_DURATION_MS.toInt())
                fallbackToneRunnable = this
                mainHandler.postDelayed(this, FALLBACK_TONE_INTERVAL_MS)
            }
        }
        fallbackToneRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopFallbackTone() {
        fallbackToneRunnable?.let(mainHandler::removeCallbacks)
        fallbackToneRunnable = null
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun promoteToForeground(
        alarmId: String,
        alarmTitle: String,
        alarmHour: Int?,
        alarmMinute: Int?,
        triggerAtEpochMillis: Long
    ) {
        createNotificationChannel()
        val notification = buildNotification(
            alarmId = alarmId,
            alarmTitle = alarmTitle,
            alarmHour = alarmHour,
            alarmMinute = alarmMinute,
            triggerAtEpochMillis = triggerAtEpochMillis
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ALARM_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(ALARM_NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        alarmId: String,
        alarmTitle: String,
        alarmHour: Int?,
        alarmMinute: Int?,
        triggerAtEpochMillis: Long
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ALARM_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val ringingIntent = createRingingActivityIntent(
            alarmId = alarmId,
            alarmTitle = alarmTitle,
            alarmHour = alarmHour,
            alarmMinute = alarmMinute,
            triggerAtEpochMillis = triggerAtEpochMillis
        )
        val stopAction = Notification.Action.Builder(
            R.drawable.ic_stop_24,
            getString(R.string.alarm_ringing_dismiss),
            createStopIntent(alarmId, triggerAtEpochMillis)
        ).build()
        val snoozeAction = Notification.Action.Builder(
            R.drawable.ic_pause_24,
            getString(R.string.alarm_ringing_snooze),
            createSnoozeIntent(alarmId, alarmTitle, triggerAtEpochMillis)
        ).build()

        return builder
            .setSmallIcon(R.drawable.ic_stop_24)
            .setContentTitle(getString(R.string.alarm_ringing_notification_title))
            .setContentText(alarmTitle)
            .setContentIntent(ringingIntent)
            .setFullScreenIntent(ringingIntent, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                @Suppress("DEPRECATION")
                setDefaults(0)
                @Suppress("DEPRECATION")
                setSound(null)
                @Suppress("DEPRECATION")
                setVibrate(null)
            }
            .addAction(stopAction)
            .addAction(snoozeAction)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(ALARM_NOTIFICATION_CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            ALARM_NOTIFICATION_CHANNEL_ID,
            getString(R.string.alarm_ringing_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            description = getString(R.string.alarm_ringing_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createRingingActivityIntent(
        alarmId: String,
        alarmTitle: String,
        alarmHour: Int?,
        alarmMinute: Int?,
        triggerAtEpochMillis: Long
    ): PendingIntent {
        val intent = Intent(this, AlarmRingingActivity::class.java)
            .setAction(AlarmRingingActivity.ACTION_SHOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        alarmHour?.let { intent.putExtra(EXTRA_ALARM_HOUR, it) }
        alarmMinute?.let { intent.putExtra(EXTRA_ALARM_MINUTE, it) }

        return PendingIntent.getActivity(
            this,
            ALARM_CONTENT_REQUEST_CODE + alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            AlarmActivityPendingIntentOptions.bundle()
        )
    }

    private fun createStopIntent(
        alarmId: String = "",
        triggerAtEpochMillis: Long = 0L
    ): PendingIntent {
        val intent = Intent(this, AlarmRingingService::class.java)
            .setAction(ACTION_STOP)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        return PendingIntent.getService(
            this,
            ALARM_STOP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createSnoozeIntent(
        alarmId: String,
        alarmTitle: String,
        triggerAtEpochMillis: Long
    ): PendingIntent {
        val intent = Intent(this, AlarmRingingService::class.java)
            .setAction(ACTION_SNOOZE)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TITLE, alarmTitle)
            .putExtra(EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
        return PendingIntent.getService(
            this,
            ALARM_SNOOZE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun resolveAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    private fun Intent.optionalIntExtra(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null

    companion object {
        const val ACTION_START = "com.won1203.lura.alarm.action.START"
        const val ACTION_SNOOZE = "com.won1203.lura.alarm.action.SNOOZE"
        const val ACTION_STOP = "com.won1203.lura.alarm.action.STOP"
        const val ACTION_ALARM_STOPPED = "com.won1203.lura.alarm.action.STOPPED"
        const val EXTRA_ALARM_ID = "alarm.extra.ALARM_ID"
        const val EXTRA_ALARM_TITLE = "alarm.extra.ALARM_TITLE"
        const val EXTRA_ALARM_HOUR = "alarm.extra.ALARM_HOUR"
        const val EXTRA_ALARM_MINUTE = "alarm.extra.ALARM_MINUTE"
        const val EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS = "alarm.extra.TRIGGER_AT_EPOCH_MILLIS"

        private const val ALARM_NOTIFICATION_ID = 3001
        private const val ALARM_CONTENT_REQUEST_CODE = 3002
        private const val ALARM_STOP_REQUEST_CODE = 3003
        private const val ALARM_SNOOZE_REQUEST_CODE = 3004
        private const val ALARM_NOTIFICATION_CHANNEL_ID = "alarm_ringing_full_screen"
        private const val SLEEP_FADE_OUT_DURATION_MS = 5_000L
        private const val SNOOZE_DURATION_MS = 5 * 60 * 1_000L
        private const val MAX_ALARM_RING_DURATION_MS = 10 * 60 * 1_000L
        private const val FALLBACK_TONE_VOLUME = 100
        private const val FALLBACK_TONE_DURATION_MS = 900L
        private const val FALLBACK_TONE_INTERVAL_MS = 1_200L
        private const val TAG = "AlarmRingingService"
    }
}
