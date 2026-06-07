package com.example.lura.data

import com.example.lura.data.local.AlarmDao
import com.example.lura.data.local.AlarmEntityMapper
import com.example.lura.data.local.LuraDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomAlarmRepository(
    private val database: LuraDatabase,
    private val alarmDao: AlarmDao,
    private val diskExecutor: ExecutorService
) : AlarmRepository {

    override fun getAlarms(): List<AlarmSchedule> =
        executeOnDisk {
            alarmDao.getAlarms().map(AlarmEntityMapper::toDomain)
        }

    override fun saveAlarm(
        category: SoundCategory,
        sound: SoundItem,
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        isEnabled: Boolean
    ): AlarmSchedule =
        executeOnDisk {
            val entity = AlarmEntityMapper.createEntity(
                category = category,
                sound = sound,
                sleepStartHour = sleepStartHour,
                sleepStartMinute = sleepStartMinute,
                hour = hour,
                minute = minute,
                weekdays = weekdays,
                isEnabled = isEnabled,
                createdAtEpochMillis = System.currentTimeMillis()
            )
            database.runInTransaction {
                if (isEnabled) {
                    alarmDao.disableEnabledAlarms()
                }
                alarmDao.upsertAlarm(entity)
            }
            AlarmEntityMapper.toDomain(entity)
        }

    override fun setAlarmEnabled(alarmId: String, isEnabled: Boolean): AlarmSchedule? =
        executeOnDisk {
            val updatedRows = alarmDao.setAlarmEnabled(alarmId, isEnabled)
            if (updatedRows == 0) {
                null
            } else {
                alarmDao.getAlarm(alarmId)?.let(AlarmEntityMapper::toDomain)
            }
        }

    override fun updateAlarmSound(
        alarmId: String,
        category: SoundCategory,
        sound: SoundItem
    ): AlarmSchedule? =
        executeOnDisk {
            val updatedRows = alarmDao.updateAlarmSound(
                alarmId = alarmId,
                categoryId = category.id,
                categoryName = category.name,
                soundId = sound.id,
                soundTitle = sound.title,
                soundTags = AlarmEntityMapper.encodeTags(sound.tags),
                soundDurationMinutes = sound.durationMinutes,
                soundObjectKey = sound.objectKey
            )
            if (updatedRows == 0) {
                null
            } else {
                alarmDao.getAlarm(alarmId)?.let(AlarmEntityMapper::toDomain)
            }
        }

    override fun updateAlarmTimes(
        alarmId: String,
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int
    ): AlarmSchedule? =
        executeOnDisk {
            val updatedRows = alarmDao.updateAlarmTimes(
                alarmId = alarmId,
                sleepStartHour = sleepStartHour,
                sleepStartMinute = sleepStartMinute,
                hour = hour,
                minute = minute
            )
            if (updatedRows == 0) {
                null
            } else {
                alarmDao.getAlarm(alarmId)?.let(AlarmEntityMapper::toDomain)
            }
        }

    override fun updateAlarmSoundObjectKey(alarmId: String, objectKey: String): AlarmSchedule? =
        executeOnDisk {
            val updatedRows = alarmDao.updateAlarmSoundObjectKey(
                alarmId = alarmId,
                soundObjectKey = objectKey
            )
            if (updatedRows == 0) {
                null
            } else {
                alarmDao.getAlarm(alarmId)?.let(AlarmEntityMapper::toDomain)
            }
        }

    override fun deleteAlarm(alarmId: String): AlarmDeleteResult =
        executeOnDisk {
            var deletedRows = 0
            var cancelledActiveSession = false
            database.runInTransaction {
                val cancelledRows = database.sleepSessionDao().cancelActiveSessionsForAlarm(
                    alarmId = alarmId,
                    cancelledStatus = SleepSessionStatus.CANCELLED
                )
                cancelledActiveSession = cancelledRows > 0
                deletedRows = alarmDao.deleteAlarm(alarmId)
            }
            AlarmDeleteResult(
                deleted = deletedRows > 0,
                cancelledActivePlayback = cancelledActiveSession
            )
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        // 기존 Repository 계약을 지키면서 Room의 메인 스레드 접근 금지를 우회하지 않기 위해 모든 DB I/O를 전용 executor에서 수행한다.
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
