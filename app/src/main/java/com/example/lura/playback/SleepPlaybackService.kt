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
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.lura.alarm.AlarmAppVisibility
import com.example.lura.alarm.AlarmRingingService
import com.example.lura.alarm.AlarmTriggerDispatcher
import com.example.lura.MainActivity
import com.example.lura.R
import com.example.lura.data.AlarmRepositoryProvider
import com.example.lura.data.ScheduledAlarmResult
import com.example.lura.data.SleepSessionStatus
import com.example.lura.data.SoundRepositoryProvider
import com.example.lura.data.StartSleepSessionForAlarmProvider
import com.example.lura.data.local.LuraDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var currentPlaybackTitle: String? = null
    private var currentPlaybackCategoryName: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

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
            ACTION_START_FOR_ALARM -> startScheduledPlaybackFromAlarm(
                intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty()
            )
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
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
        updatePlaybackNotification(startInForegroundRequired)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (currentSessionId == null) {
            pauseAllPlayersAndStopSelf()
            return
        }

        // 사용자가 앱 화면을 닫아도 수면 세션은 알람 시각까지 독립적으로 유지되어야 한다.
        // 재생 중/버퍼링/일시정지 상태 판단에 따라 서비스를 종료하면 백그라운드 수면음이 끊길 수 있다.
        updatePlaybackNotification(startInForegroundRequired = true)
    }

    override fun onDestroy() {
        clearScheduledStop()
        serviceScope.cancel()
        databaseExecutor.shutdown()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun startPlayback(request: SleepPlaybackRequest) {
        val exoPlayer = player ?: return
        currentSessionId = request.sessionId
        currentAlarmId = request.alarmId
        currentAlarmTitle = request.title
        currentPlaybackTitle = request.title
        currentPlaybackCategoryName = request.categoryName
        exoPlayer.volume = DEFAULT_PLAYBACK_VOLUME
        promoteToForeground(request)
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

    private fun startScheduledPlaybackFromAlarm(alarmId: String) {
        if (alarmId.isBlank()) {
            stopSelf()
            return
        }

        // 예약 이벤트에서 곧바로 재생 서비스를 포그라운드로 승격해야 앱 프로세스가 내려간
        // 상태에서도 Android의 백그라운드 서비스 시작 제한에 흔들리지 않는다.
        startForegroundWithNotification(buildPreparingNotification())

        serviceScope.launch {
            val playbackPlan = runCatching {
                withContext(Dispatchers.IO) {
                    val result = StartSleepSessionForAlarmProvider
                        .get(applicationContext)
                        .execute(alarmId)
                        ?: return@withContext null
                    val session = result.sleepSession
                        ?: return@withContext ScheduledPlaybackPlan(result, null)
                    val playbackSource = SoundRepositoryProvider.get().getPlaybackSource(
                        soundId = result.alarmSchedule.soundId,
                        objectKey = result.alarmSchedule.soundObjectKey.ifBlank { null }
                    )

                    if (
                        playbackSource.objectKey.isNotBlank() &&
                        playbackSource.objectKey != result.alarmSchedule.soundObjectKey
                    ) {
                        AlarmRepositoryProvider.get(applicationContext)
                            .updateAlarmSoundObjectKey(alarmId, playbackSource.objectKey)
                    }

                    ScheduledPlaybackPlan(
                        result = result,
                        sourceUri = playbackSource.sourceUri
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to start scheduled sleep playback.", error)
            }.getOrNull()

            val session = playbackPlan?.result?.sleepSession
            val sourceUri = playbackPlan?.sourceUri
            if (playbackPlan == null || session == null || sourceUri.isNullOrBlank()) {
                stopSelf()
                return@launch
            }

            showSleepStartNotice(playbackPlan.result)
            startPlayback(
                SleepPlaybackRequest.from(
                    alarmSchedule = playbackPlan.result.alarmSchedule,
                    sleepSession = session,
                    sourceUri = sourceUri
                )
            )
        }
    }

    private fun pausePlayback() {
        player?.pause()
        updatePlaybackNotification()
    }

    private fun resumePlayback() {
        val exoPlayer = player ?: return
        if (exoPlayer.currentMediaItem == null) return

        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
        exoPlayer.play()
        updatePlaybackNotification()
    }

    private fun stopPlayback(sessionStatus: SleepSessionStatus? = null) {
        clearScheduledStop()
        clearFadeOut()
        sessionStatus?.let(::updateCurrentSessionStatus)
        player?.stop()
        currentAlarmId = null
        currentAlarmTitle = null
        currentPlaybackTitle = null
        currentPlaybackCategoryName = null
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
        startForegroundWithNotification(
            buildPlaybackNotification(
                title = request.title,
                categoryName = request.categoryName,
                isPlaying = true
            )
        )
    }

    private fun updatePlaybackNotification(startInForegroundRequired: Boolean = false) {
        val exoPlayer = player ?: return
        if (exoPlayer.currentMediaItem == null && currentPlaybackTitle == null) return

        val notification = buildPlaybackNotification(
            title = exoPlayer.mediaMetadata.title?.toString()
                ?: currentPlaybackTitle
                ?: getString(R.string.app_name),
            categoryName = exoPlayer.mediaMetadata.artist?.toString()
                ?: currentPlaybackCategoryName.orEmpty(),
            isPlaying = exoPlayer.isPlaying
        )

        val shouldRemainForeground = startInForegroundRequired ||
            exoPlayer.currentMediaItem != null ||
            exoPlayer.playbackState == Player.STATE_BUFFERING ||
            exoPlayer.playbackState == Player.STATE_READY

        if (shouldRemainForeground) {
            startForegroundWithNotification(notification)
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(PLAYBACK_NOTIFICATION_ID, notification)
        }
    }

    private fun buildPlaybackNotification(
        title: String,
        categoryName: String,
        isPlaying: Boolean
    ): Notification {
        createPlaybackNotificationChannel()
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PLAYBACK_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val toggleAction = if (isPlaying) {
            Notification.Action.Builder(
                R.drawable.ic_pause_24,
                getString(R.string.player_pause),
                createPlaybackActionIntent(ACTION_PAUSE, PAUSE_REQUEST_CODE)
            ).build()
        } else {
            Notification.Action.Builder(
                R.drawable.ic_play_arrow_24,
                getString(R.string.player_play),
                createPlaybackActionIntent(ACTION_RESUME, RESUME_REQUEST_CODE)
            ).build()
        }
        val stopAction = Notification.Action.Builder(
            R.drawable.ic_stop_24,
            getString(R.string.player_stop),
            createPlaybackActionIntent(ACTION_STOP, STOP_REQUEST_CODE)
        ).build()

        return notificationBuilder
            .setSmallIcon(if (isPlaying) R.drawable.ic_pause_24 else R.drawable.ic_play_arrow_24)
            .setContentTitle(title)
            .setContentText(categoryName)
            .setContentIntent(createSessionActivityIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(toggleAction)
            .addAction(stopAction)
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun buildPreparingNotification(): Notification {
        createPlaybackNotificationChannel()
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PLAYBACK_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return notificationBuilder
            .setSmallIcon(R.drawable.ic_play_arrow_24)
            .setContentTitle(getString(R.string.sleep_start_notification_title))
            .setContentText(getString(R.string.sleep_start_notification_preparing))
            .setContentIntent(createSessionActivityIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showSleepStartNotice(result: ScheduledAlarmResult) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val wakeTime = getString(
            R.string.alarm_time_format,
            result.alarmSchedule.hour,
            result.alarmSchedule.minute
        )

        notificationManager.notify(
            SLEEP_START_NOTICE_NOTIFICATION_ID,
            buildSleepStartNoticeNotification(
                text = getString(
                    R.string.sleep_start_notification_message,
                    result.alarmSchedule.categoryName,
                    wakeTime
                )
            )
        )
    }

    private fun buildSleepStartNoticeNotification(text: String): Notification {
        createPlaybackNotificationChannel()
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, PLAYBACK_NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return notificationBuilder
            .setSmallIcon(R.drawable.ic_play_arrow_24)
            .setContentTitle(getString(R.string.sleep_start_notification_title))
            .setContentText(text)
            .setContentIntent(createSessionActivityIntent())
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
    }

    private fun startForegroundWithNotification(notification: Notification) {
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

    private fun createPlaybackActionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, SleepPlaybackService::class.java)
            .setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
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
        const val ACTION_START_FOR_ALARM = "com.example.lura.playback.action.START_FOR_ALARM"
        const val ACTION_PAUSE = "com.example.lura.playback.action.PAUSE"
        const val ACTION_RESUME = "com.example.lura.playback.action.RESUME"
        const val ACTION_STOP = "com.example.lura.playback.action.STOP"
        const val ACTION_FADE_OUT_AND_COMPLETE = "com.example.lura.playback.action.FADE_OUT_AND_COMPLETE"

        private const val SESSION_ACTIVITY_REQUEST_CODE = 1001
        private const val PAUSE_REQUEST_CODE = 1002
        private const val RESUME_REQUEST_CODE = 1003
        private const val STOP_REQUEST_CODE = 1004
        private const val PLAYBACK_NOTIFICATION_ID = 2001
        private const val SLEEP_START_NOTICE_NOTIFICATION_ID = 2002
        private const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "sleep_playback"
        private const val TAG_SEPARATOR = " · "
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DEFAULT_PLAYBACK_VOLUME = 1f
        private const val FADE_OUT_STEPS = 20
        private const val FADE_OUT_STEP_DELAY_MS = 250L
        private const val TAG = "SleepPlaybackService"
    }

    private data class ScheduledPlaybackPlan(
        val result: ScheduledAlarmResult,
        val sourceUri: String?
    )
}
