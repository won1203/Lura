package com.won1203.lura.data

import com.won1203.lura.data.local.AlarmEntityMapper
import com.won1203.lura.data.local.LuraDatabase
import com.won1203.lura.data.local.SleepSessionEntityMapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomStartSleepSessionForAlarm(
    private val database: LuraDatabase,
    private val alarmTargetTimeCalculator: AlarmTargetTimeCalculator,
    private val diskExecutor: ExecutorService
) : StartSleepSessionForAlarm {

    override fun execute(alarmId: String): ScheduledAlarmResult? =
        executeOnDisk {
            val nowEpochMillis = System.currentTimeMillis()
            var result: ScheduledAlarmResult? = null

            database.runInTransaction {
                val alarmEntity = database.alarmDao().getAlarm(alarmId)
                    ?: return@runInTransaction
                if (alarmEntity.soundId == UnselectedAlarmSound.SOUND_ID) {
                    return@runInTransaction
                }

                database.alarmDao().disableEnabledAlarms()
                database.alarmDao().setAlarmEnabled(alarmId, true)
                val enabledAlarmEntity = alarmEntity.copy(isEnabled = true)
                val sleepWindow = alarmTargetTimeCalculator.nextSleepWindow(
                    sleepStartHour = enabledAlarmEntity.sleepStartHour,
                    sleepStartMinute = enabledAlarmEntity.sleepStartMinute,
                    wakeHour = enabledAlarmEntity.hour,
                    wakeMinute = enabledAlarmEntity.minute,
                    weekdays = enabledAlarmEntity.weekdays,
                    nowEpochMillis = nowEpochMillis
                )
                val sessionEntity = if (sleepWindow.contains(nowEpochMillis)) {
                    SleepSessionEntityMapper.createPlayingEntity(
                        alarmId = enabledAlarmEntity.id,
                        sleepSoundId = enabledAlarmEntity.soundId,
                        categoryName = enabledAlarmEntity.categoryName,
                        startedAtEpochMillis = nowEpochMillis,
                        targetAlarmAtEpochMillis = sleepWindow.wakeAtEpochMillis
                    )
                } else {
                    null
                }

                database.sleepSessionDao().cancelActiveSessions(SleepSessionStatus.CANCELLED)
                sessionEntity?.let(database.sleepSessionDao()::insertSession)
                result = ScheduledAlarmResult(
                    alarmSchedule = AlarmEntityMapper.toDomain(enabledAlarmEntity),
                    sleepWindow = sleepWindow,
                    sleepSession = sessionEntity?.let(SleepSessionEntityMapper::toDomain)
                )
            }

            result
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
