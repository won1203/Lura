package com.example.lura.data.local

import com.example.lura.data.SleepSession
import com.example.lura.data.SleepSessionStatus
import java.util.UUID

object SleepSessionEntityMapper {
    fun createPlayingEntity(
        alarmId: String,
        sleepSoundId: String,
        startedAtEpochMillis: Long,
        targetAlarmAtEpochMillis: Long
    ): SleepSessionEntity =
        SleepSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            alarmId = alarmId,
            sleepSoundId = sleepSoundId,
            startedAtEpochMillis = startedAtEpochMillis,
            targetAlarmAtEpochMillis = targetAlarmAtEpochMillis,
            status = SleepSessionStatus.PLAYING
        )

    fun toDomain(entity: SleepSessionEntity): SleepSession =
        SleepSession(
            sessionId = entity.sessionId,
            alarmId = entity.alarmId,
            sleepSoundId = entity.sleepSoundId,
            startedAtEpochMillis = entity.startedAtEpochMillis,
            targetAlarmAtEpochMillis = entity.targetAlarmAtEpochMillis,
            status = entity.status
        )
}
