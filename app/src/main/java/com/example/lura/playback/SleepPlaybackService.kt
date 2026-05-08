package com.example.lura.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.lura.MainActivity
import com.example.lura.data.SleepSessionStatus
import com.example.lura.data.local.LuraDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SleepPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var stopAtAlarmRunnable: Runnable? = null
    private var currentSessionId: String? = null

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
            ACTION_STOP -> stopPlayback(SleepSessionStatus.CANCELLED)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player?.isPlaying != true) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
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
        currentSessionId = request.sessionId
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
        sessionStatus?.let(::updateCurrentSessionStatus)
        player?.stop()
        stopSelf()
    }

    private fun scheduleStopAtTargetAlarm(targetAlarmAtEpochMillis: Long) {
        clearScheduledStop()
        if (targetAlarmAtEpochMillis <= 0L) return

        val delayMillis = (targetAlarmAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val runnable = Runnable { stopPlayback(SleepSessionStatus.COMPLETED) }
        stopAtAlarmRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun clearScheduledStop() {
        stopAtAlarmRunnable?.let(mainHandler::removeCallbacks)
        stopAtAlarmRunnable = null
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

        private const val SESSION_ACTIVITY_REQUEST_CODE = 1001
        private const val TAG_SEPARATOR = " · "
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
