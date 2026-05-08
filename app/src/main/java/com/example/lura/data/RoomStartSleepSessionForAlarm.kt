package com.example.lura.data

import com.example.lura.data.local.AlarmEntityMapper
import com.example.lura.data.local.LuraDatabase
import com.example.lura.data.local.SleepSessionEntityMapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomStartSleepSessionForAlarm(
    private val database: LuraDatabase,
    private val alarmTargetTimeCalculator: AlarmTargetTimeCalculator,
    private val diskExecutor: ExecutorService
) : StartSleepSessionForAlarm {

    override fun execute(alarmId: String): StartedSleepSessionResult? =
        executeOnDisk {
            val nowEpochMillis = System.currentTimeMillis()
            var result: StartedSleepSessionResult? = null

            database.runInTransaction {
                val alarmEntity = database.alarmDao().getAlarm(alarmId)
                    ?: return@runInTransaction
                if (alarmEntity.soundId == UnselectedAlarmSound.SOUND_ID) {
                    return@runInTransaction
                }

                database.alarmDao().disableEnabledAlarms()
                database.alarmDao().setAlarmEnabled(alarmId, true)
                val enabledAlarmEntity = alarmEntity.copy(isEnabled = true)
                val targetAlarmAtEpochMillis = alarmTargetTimeCalculator.nextTargetEpochMillis(
                    hour = enabledAlarmEntity.hour,
                    minute = enabledAlarmEntity.minute,
                    weekdays = enabledAlarmEntity.weekdays,
                    nowEpochMillis = nowEpochMillis
                )
                val sessionEntity = SleepSessionEntityMapper.createPlayingEntity(
                    alarmId = enabledAlarmEntity.id,
                    sleepSoundId = enabledAlarmEntity.soundId,
                    startedAtEpochMillis = nowEpochMillis,
                    targetAlarmAtEpochMillis = targetAlarmAtEpochMillis
                )

                database.sleepSessionDao().cancelActiveSessions(SleepSessionStatus.CANCELLED)
                database.sleepSessionDao().insertSession(sessionEntity)
                result = StartedSleepSessionResult(
                    alarmSchedule = AlarmEntityMapper.toDomain(enabledAlarmEntity),
                    sleepSession = SleepSessionEntityMapper.toDomain(sessionEntity)
                )
            }

            result
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
