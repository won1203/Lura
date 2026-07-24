package com.won1203.lura.data

import com.won1203.lura.data.local.LuraDatabase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

class RoomDisableAlarmAndCancelSleepSession(
    private val database: LuraDatabase,
    private val diskExecutor: ExecutorService
) : DisableAlarmAndCancelSleepSession {

    override fun execute(alarmId: String): Boolean =
        executeOnDisk {
            var cancelledActiveSession = false
            database.runInTransaction {
                database.alarmDao().setAlarmEnabled(alarmId, false)
                val cancelledRows = database.sleepSessionDao().cancelActiveSessionsForAlarm(
                    alarmId = alarmId,
                    cancelledStatus = SleepSessionStatus.CANCELLED
                )
                cancelledActiveSession = cancelledRows > 0
            }
            cancelledActiveSession
        }

    private fun <T> executeOnDisk(block: () -> T): T {
        val future: Future<T> = diskExecutor.submit<T> { block() }
        return future.get()
    }
}
