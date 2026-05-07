package com.example.lura.data

import com.example.lura.data.local.AlarmEntityMapper
import com.example.lura.data.local.LuraDatabase
import com.example.lura.data.local.SleepSessionEntityMapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomSaveAlarmAndStartSleepSession(
    private val database: LuraDatabase,
    private val alarmTargetTimeCalculator: AlarmTargetTimeCalculator,
    private val diskExecutor: ExecutorService
) : SaveAlarmAndStartSleepSession {

    override fun execute(
        category: SoundCategory?,
        sound: SoundItem?,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>
    ): StartedSleepSessionResult =
        executeOnDisk {
            val nowEpochMillis = System.currentTimeMillis()
            val normalizedWeekdays = weekdays.sortedBy { it.sortOrder }
            val selectedCategory = category ?: UnselectedAlarmSound.category
            val selectedSound = sound ?: UnselectedAlarmSound.sound
            val targetAlarmAtEpochMillis = alarmTargetTimeCalculator.nextTargetEpochMillis(
                hour = hour,
                minute = minute,
                weekdays = normalizedWeekdays,
                nowEpochMillis = nowEpochMillis
            )

            var result: StartedSleepSessionResult? = null
            database.runInTransaction {
                val alarmEntity = AlarmEntityMapper.createEntity(
                    category = selectedCategory,
                    sound = selectedSound,
                    hour = hour,
                    minute = minute,
                    weekdays = normalizedWeekdays,
                    createdAtEpochMillis = nowEpochMillis
                )
                val sessionEntity = SleepSessionEntityMapper.createPlayingEntity(
                    alarmId = alarmEntity.id,
                    sleepSoundId = selectedSound.id,
                    startedAtEpochMillis = nowEpochMillis,
                    targetAlarmAtEpochMillis = targetAlarmAtEpochMillis
                )

                database.alarmDao().upsertAlarm(alarmEntity)
                // A single running sleep flow prevents playback, alarm scheduling, and recovery
                // from competing over multiple active sessions after repeated save taps.
                database.sleepSessionDao().cancelActiveSessions(SleepSessionStatus.CANCELLED)
                database.sleepSessionDao().insertSession(sessionEntity)

                result = StartedSleepSessionResult(
                    alarmSchedule = AlarmEntityMapper.toDomain(alarmEntity),
                    sleepSession = SleepSessionEntityMapper.toDomain(sessionEntity)
                )
            }

            requireNotNull(result)
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
