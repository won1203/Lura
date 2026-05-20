package com.example.lura.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.lura.alarm.AlarmAppVisibility
import com.example.lura.alarm.AlarmTriggerDispatcher
import com.example.lura.MainActivity
import com.example.lura.R
import com.example.lura.data.SleepSessionStatus
import com.example.lura.data.local.LuraDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class SleepPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var stopAtAlarmRunnable: Runnable? = null
    private var fadeOutRunnable: Runnable? = null
    private var currentSessionId: String? = null
    private var currentAlarmId: String? = null
    private var currentAlarmTitle: String? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setNotificationId(PLAYBACK_NOTIFICATION_ID)
                .setChannelId(PLAYBACK_NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.sleep_playback_notification_channel_name)
                .build()
        )

        val exoPlayer = ExoPlayer.Builder(this)
            .build()
            .apply {
                // 수면 유도음은 알람 도달 전까지 끊기지 않아야 하므로 재생 엔진에서 단일 음원을 반복한다.
                repeatMode = Player.REPEAT_MODE_ONE
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
            }

        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(createSessionActivityIntent())
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> SleepPlaybackRequest.fromIntent(intent)?.let(::startPlayback)
            ACTION_STOP -> stopPlayback(SleepSessionStatus.CANCELLED)
            ACTION_FADE_OUT_AND_COMPLETE -> fadeOutAndStop(SleepSessionStatus.COMPLETED)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        val shouldForceForeground = player?.let { currentPlayer ->
            currentPlayer.playWhenReady && (
                currentPlayer.playbackState == Player.STATE_BUFFERING ||
                    currentPlayer.playbackState == Player.STATE_READY
                )
        } == true

        // User-triggered sleep playback must promote the service as soon as playback
        // starts, otherwise Android kills it for missing the foreground-service deadline.
        super.onUpdateNotification(
            session,
            startInForegroundRequired || shouldForceForeground
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing()) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        clearScheduledStop()
        databaseExecutor.shutdown()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun startPlayback(request: SleepPlaybackRequest) {
        val exoPlayer = player ?: return
        promoteToForeground(request)
        currentSessionId = request.sessionId
        currentAlarmId = request.alarmId
        currentAlarmTitle = request.title
        exoPlayer.volume = DEFAULT_PLAYBACK_VOLUME
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(request.title)
            .setArtist(request.categoryName)
            .setDescription(request.tags.joinToString(TAG_SEPARATOR))

        if (request.durationMinutes > 0) {
            metadataBuilder.setDurationMs(request.durationMinutes * MILLIS_PER_MINUTE)
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(request.soundId)
            .setUri(request.sourceUri)
            .setMediaMetadata(metadataBuilder.build())
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
        scheduleStopAtTargetAlarm(request.targetAlarmAtEpochMillis)
    }

    private fun stopPlayback(sessionStatus: SleepSessionStatus? = null) {
        clearScheduledStop()
        clearFadeOut()
        sessionStatus?.let(::updateCurrentSessionStatus)
        player?.stop()
        currentAlarmId = null
        currentAlarmTitle = null
        stopSelf()
    }

    private fun scheduleStopAtTargetAlarm(targetAlarmAtEpochMillis: Long) {
        clearScheduledStop()
        if (targetAlarmAtEpochMillis <= 0L) return

        val delayMillis = (targetAlarmAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val runnable = Runnable { triggerAlarmAndFadeOut() }
        stopAtAlarmRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun triggerAlarmAndFadeOut() {
        AlarmTriggerDispatcher.trigger(
            context = this,
            alarmId = currentAlarmId.orEmpty(),
            alarmTitle = currentAlarmTitle.orEmpty(),
            showRingingScreen = AlarmAppVisibility.isForeground
        )
        fadeOutAndStop(SleepSessionStatus.COMPLETED)
    }

    private fun fadeOutAndStop(sessionStatus: SleepSessionStatus) {
        clearScheduledStop()
        clearFadeOut()
        val exoPlayer = player ?: run {
            stopPlayback(sessionStatus)
            return
        }

        if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) {
            stopPlayback(sessionStatus)
            return
        }

        val initialVolume = exoPlayer.volume.coerceAtLeast(0f)
        if (initialVolume == 0f) {
            stopPlayback(sessionStatus)
            return
        }

        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step += 1
                val remainingRatio = ((FADE_OUT_STEPS - step).coerceAtLeast(0)).toFloat() / FADE_OUT_STEPS
                exoPlayer.volume = initialVolume * remainingRatio
                if (step >= FADE_OUT_STEPS) {
                    stopPlayback(sessionStatus)
                } else {
                    fadeOutRunnable = this
                    mainHandler.postDelayed(this, FADE_OUT_STEP_DELAY_MS)
                }
            }
        }
        fadeOutRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun promoteToForeground(request: SleepPlaybackRequest) {
        createPlaybackNotificationChannel()
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PLAYBACK_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setSmallIcon(R.drawable.ic_play_arrow_24)
            .setContentTitle(request.title)
            .setContentText(request.categoryName)
            .setContentIntent(createSessionActivityIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PLAYBACK_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun createPlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(PLAYBACK_NOTIFICATION_CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            PLAYBACK_NOTIFICATION_CHANNEL_ID,
            getString(R.string.sleep_playback_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            description = getString(R.string.sleep_playback_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun clearScheduledStop() {
        stopAtAlarmRunnable?.let(mainHandler::removeCallbacks)
        stopAtAlarmRunnable = null
    }

    private fun clearFadeOut() {
        fadeOutRunnable?.let(mainHandler::removeCallbacks)
        fadeOutRunnable = null
    }

    private fun updateCurrentSessionStatus(status: SleepSessionStatus) {
        val sessionId = currentSessionId ?: return
        databaseExecutor.execute {
            LuraDatabase.getInstance(applicationContext)
                .sleepSessionDao()
                .updateActiveSessionStatus(sessionId, status)
        }
    }

    private fun createSessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val ACTION_START = "com.example.lura.playback.action.START"
        const val ACTION_STOP = "com.example.lura.playback.action.STOP"
        const val ACTION_FADE_OUT_AND_COMPLETE = "com.example.lura.playback.action.FADE_OUT_AND_COMPLETE"

        private const val SESSION_ACTIVITY_REQUEST_CODE = 1001
        private const val PLAYBACK_NOTIFICATION_ID = 2001
        private const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "sleep_playback"
        private const val TAG_SEPARATOR = " · "
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DEFAULT_PLAYBACK_VOLUME = 1f
        private const val FADE_OUT_STEPS = 20
        private const val FADE_OUT_STEP_DELAY_MS = 250L
    }
}
