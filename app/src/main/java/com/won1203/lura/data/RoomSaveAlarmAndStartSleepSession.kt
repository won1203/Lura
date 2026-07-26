package com.won1203.lura.data

import com.won1203.lura.data.local.AlarmEntityMapper
import com.won1203.lura.data.local.LuraDatabase
import com.won1203.lura.data.local.SleepSessionEntityMapper
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
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        startImmediately: Boolean
    ): ScheduledAlarmResult =
        executeOnDisk {
            val nowEpochMillis = System.currentTimeMillis()
            val normalizedWeekdays = weekdays.sortedBy { it.sortOrder }
            val selectedCategory = category ?: UnselectedAlarmSound.category
            val selectedSound = sound ?: UnselectedAlarmSound.sound
            val plannedSleepWindow = alarmTargetTimeCalculator.nextSleepWindow(
                sleepStartHour = sleepStartHour,
                sleepStartMinute = sleepStartMinute,
                wakeHour = hour,
                wakeMinute = minute,
                weekdays = normalizedWeekdays,
                nowEpochMillis = nowEpochMillis
            )
            val effectiveSleepWindow = if (startImmediately && !plannedSleepWindow.contains(nowEpochMillis)) {
                plannedSleepWindow.copy(sleepStartAtEpochMillis = nowEpochMillis)
            } else {
                plannedSleepWindow
            }
            val shouldCreateSession = selectedSound.id != UnselectedAlarmSound.SOUND_ID &&
                (startImmediately || effectiveSleepWindow.contains(nowEpochMillis))

            var result: ScheduledAlarmResult? = null
            database.runInTransaction {
                val alarmEntity = AlarmEntityMapper.createEntity(
                    category = selectedCategory,
                    sound = selectedSound,
                    sleepStartHour = sleepStartHour,
                    sleepStartMinute = sleepStartMinute,
                    hour = hour,
                    minute = minute,
                    weekdays = normalizedWeekdays,
                    createdAtEpochMillis = nowEpochMillis
                )
                val sessionEntity = if (shouldCreateSession) {
                    SleepSessionEntityMapper.createPlayingEntity(
                        alarmId = alarmEntity.id,
                        sleepSoundId = selectedSound.id,
                        categoryName = selectedCategory.name,
                        startedAtEpochMillis = nowEpochMillis,
                        targetAlarmAtEpochMillis = effectiveSleepWindow.wakeAtEpochMillis
                    )
                } else {
                    null
                }

                database.alarmDao().disableEnabledAlarms()
                database.alarmDao().upsertAlarm(alarmEntity)
                // A single running sleep flow prevents playback, alarm scheduling, and recovery
                // from competing over multiple active sessions after repeated save taps.
                database.sleepSessionDao().cancelActiveSessions(SleepSessionStatus.CANCELLED)
                sessionEntity?.let(database.sleepSessionDao()::insertSession)

                result = ScheduledAlarmResult(
                    alarmSchedule = AlarmEntityMapper.toDomain(alarmEntity),
                    sleepWindow = effectiveSleepWindow,
                    sleepSession = sessionEntity?.let(SleepSessionEntityMapper::toDomain)
                )
            }

            requireNotNull(result)
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
