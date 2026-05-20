package com.example.lura.alarm

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
import com.example.lura.R
import com.example.lura.playback.SleepPlaybackController

class AlarmRingingService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var startRingingRunnable: Runnable? = null
    private var autoStopRunnable: Runnable? = null
    private var fallbackToneRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlarmFlow(intent)
            ACTION_STOP -> stopAlarm()
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

        promoteToForeground(alarmId, alarmTitle)
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

    private fun stopAlarm() {
        clearCallbacks()
        releasePlayer()
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

    private fun promoteToForeground(alarmId: String, alarmTitle: String) {
        createNotificationChannel()
        val notification = buildNotification(alarmId, alarmTitle)
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

    private fun buildNotification(alarmId: String, alarmTitle: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ALARM_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val ringingIntent = createRingingActivityIntent(alarmId, alarmTitle)
        val stopAction = Notification.Action.Builder(
            R.drawable.ic_stop_24,
            getString(R.string.alarm_ringing_stop),
            createStopIntent()
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
            .addAction(stopAction)
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
            description = getString(R.string.alarm_ringing_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createRingingActivityIntent(alarmId: String, alarmTitle: String): PendingIntent {
        val intent = Intent(this, AlarmRingingActivity::class.java)
            .setAction(AlarmRingingActivity.ACTION_SHOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TITLE, alarmTitle)

        return PendingIntent.getActivity(
            this,
            ALARM_CONTENT_REQUEST_CODE + alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createStopIntent(): PendingIntent {
        val intent = Intent(this, AlarmRingingService::class.java)
            .setAction(ACTION_STOP)
        return PendingIntent.getService(
            this,
            ALARM_STOP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun resolveAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    companion object {
        const val ACTION_START = "com.example.lura.alarm.action.START"
        const val ACTION_STOP = "com.example.lura.alarm.action.STOP"
        const val EXTRA_ALARM_ID = "alarm.extra.ALARM_ID"
        const val EXTRA_ALARM_TITLE = "alarm.extra.ALARM_TITLE"

        private const val ALARM_NOTIFICATION_ID = 3001
        private const val ALARM_CONTENT_REQUEST_CODE = 3002
        private const val ALARM_STOP_REQUEST_CODE = 3003
        private const val ALARM_NOTIFICATION_CHANNEL_ID = "alarm_ringing"
        private const val SLEEP_FADE_OUT_DURATION_MS = 5_000L
        private const val MAX_ALARM_RING_DURATION_MS = 10 * 60 * 1_000L
        private const val FALLBACK_TONE_VOLUME = 100
        private const val FALLBACK_TONE_DURATION_MS = 900L
        private const val FALLBACK_TONE_INTERVAL_MS = 1_200L
        private const val TAG = "AlarmRingingService"
    }
}
