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
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        isEnabled: Boolean
    ): AlarmSchedule =
        executeOnDisk {
            val entity = AlarmEntityMapper.createEntity(
                category = category,
                sound = sound,
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
                soundDurationMinutes = sound.durationMinutes
            )
            if (updatedRows == 0) {
                null
            } else {
                alarmDao.getAlarm(alarmId)?.let(AlarmEntityMapper::toDomain)
            }
        }

    override fun deleteAlarm(alarmId: String): Boolean =
        executeOnDisk {
            var deletedRows = 0
            database.runInTransaction {
                database.sleepSessionDao().cancelActiveSessionsForAlarm(
                    alarmId = alarmId,
                    cancelledStatus = SleepSessionStatus.CANCELLED
                )
                deletedRows = alarmDao.deleteAlarm(alarmId)
            }
            deletedRows > 0
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        // 기존 Repository 계약을 지키면서 Room의 메인 스레드 접근 금지를 우회하지 않기 위해 모든 DB I/O를 전용 executor에서 수행한다.
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
