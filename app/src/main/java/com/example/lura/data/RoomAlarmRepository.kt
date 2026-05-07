package com.example.lura.data

import com.example.lura.data.local.AlarmDao
import com.example.lura.data.local.AlarmEntityMapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomAlarmRepository(
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
        weekdays: List<AlarmWeekday>
    ): AlarmSchedule =
        executeOnDisk {
            val entity = AlarmEntityMapper.createEntity(
                category = category,
                sound = sound,
                hour = hour,
                minute = minute,
                weekdays = weekdays,
                createdAtEpochMillis = System.currentTimeMillis()
            )
            alarmDao.upsertAlarm(entity)
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

    private fun <T> executeOnDisk(block: () -> T): T {
        // 기존 Repository 계약을 지키면서 Room의 메인 스레드 접근 금지를 우회하지 않기 위해 모든 DB I/O를 전용 executor에서 수행한다.
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
