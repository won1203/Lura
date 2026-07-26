package com.won1203.lura.data.local

import com.won1203.lura.data.SleepSession
import com.won1203.lura.data.SleepSessionStatus
import java.util.UUID

object SleepSessionEntityMapper {
    fun createPlayingEntity(
        alarmId: String,
        sleepSoundId: String,
        categoryName: String,
        startedAtEpochMillis: Long,
        targetAlarmAtEpochMillis: Long
    ): SleepSessionEntity =
        SleepSessionEntity(
            sessionId = UUID.randomUUID().toString(),
            alarmId = alarmId,
            sleepSoundId = sleepSoundId,
            categoryName = categoryName,
            startedAtEpochMillis = startedAtEpochMillis,
            targetAlarmAtEpochMillis = targetAlarmAtEpochMillis,
            status = SleepSessionStatus.PLAYING
        )

    fun toDomain(entity: SleepSessionEntity): SleepSession =
        SleepSession(
            sessionId = entity.sessionId,
            alarmId = entity.alarmId,
            sleepSoundId = entity.sleepSoundId,
            categoryName = entity.categoryName,
            startedAtEpochMillis = entity.startedAtEpochMillis,
            targetAlarmAtEpochMillis = entity.targetAlarmAtEpochMillis,
            status = entity.status
        )
}
