package com.example.lura.playback

import android.content.Intent
import android.os.Bundle
import com.example.lura.data.AlarmSchedule
import com.example.lura.data.SleepSession

data class SleepPlaybackRequest(
    val sessionId: String,
    val alarmId: String,
    val soundId: String,
    val title: String,
    val categoryName: String,
    val tags: List<String>,
    val durationMinutes: Int,
    val targetAlarmAtEpochMillis: Long,
    val sourceUri: String
) {
    fun toBundle(): Bundle =
        Bundle().apply {
            putString(KEY_SESSION_ID, sessionId)
            putString(KEY_ALARM_ID, alarmId)
            putString(KEY_SOUND_ID, soundId)
            putString(KEY_TITLE, title)
            putString(KEY_CATEGORY_NAME, categoryName)
            putStringArrayList(KEY_TAGS, ArrayList(tags))
            putInt(KEY_DURATION_MINUTES, durationMinutes)
            putLong(KEY_TARGET_ALARM_AT_EPOCH_MILLIS, targetAlarmAtEpochMillis)
            putString(KEY_SOURCE_URI, sourceUri)
        }

    fun writeTo(intent: Intent): Intent =
        intent.apply {
            putExtras(toBundle())
        }

    companion object {
        private const val KEY_SESSION_ID = "sleepPlayback.sessionId"
        private const val KEY_ALARM_ID = "sleepPlayback.alarmId"
        private const val KEY_SOUND_ID = "sleepPlayback.soundId"
        private const val KEY_TITLE = "sleepPlayback.title"
        private const val KEY_CATEGORY_NAME = "sleepPlayback.categoryName"
        private const val KEY_TAGS = "sleepPlayback.tags"
        private const val KEY_DURATION_MINUTES = "sleepPlayback.durationMinutes"
        private const val KEY_TARGET_ALARM_AT_EPOCH_MILLIS = "sleepPlayback.targetAlarmAtEpochMillis"
        private const val KEY_SOURCE_URI = "sleepPlayback.sourceUri"

        fun from(
            alarmSchedule: AlarmSchedule,
            sleepSession: SleepSession,
            sourceUri: String
        ): SleepPlaybackRequest =
            SleepPlaybackRequest(
                sessionId = sleepSession.sessionId,
                alarmId = alarmSchedule.id,
                soundId = alarmSchedule.soundId,
                title = alarmSchedule.soundTitle,
                categoryName = alarmSchedule.categoryName,
                tags = alarmSchedule.soundTags,
                durationMinutes = alarmSchedule.soundDurationMinutes,
                targetAlarmAtEpochMillis = sleepSession.targetAlarmAtEpochMillis,
                sourceUri = sourceUri
            )

        fun fromIntent(intent: Intent?): SleepPlaybackRequest? =
            fromBundle(intent?.extras)

        fun fromBundle(bundle: Bundle?): SleepPlaybackRequest? {
            if (bundle == null) return null

            val sessionId = bundle.getString(KEY_SESSION_ID) ?: return null
            val alarmId = bundle.getString(KEY_ALARM_ID) ?: return null
            val soundId = bundle.getString(KEY_SOUND_ID) ?: return null
            val title = bundle.getString(KEY_TITLE) ?: return null
            val categoryName = bundle.getString(KEY_CATEGORY_NAME) ?: return null
            val sourceUri = bundle.getString(KEY_SOURCE_URI) ?: return null

            return SleepPlaybackRequest(
                sessionId = sessionId,
                alarmId = alarmId,
                soundId = soundId,
                title = title,
                categoryName = categoryName,
                tags = bundle.getStringArrayList(KEY_TAGS)?.toList().orEmpty(),
                durationMinutes = bundle.getInt(KEY_DURATION_MINUTES),
                targetAlarmAtEpochMillis = bundle.getLong(KEY_TARGET_ALARM_AT_EPOCH_MILLIS),
                sourceUri = sourceUri
            )
        }
    }
}
