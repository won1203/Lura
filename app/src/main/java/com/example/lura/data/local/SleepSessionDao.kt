package com.example.lura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lura.data.SleepSessionStatus

@Dao
interface SleepSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSession(session: SleepSessionEntity)

    @Query(
        """
        UPDATE sleep_sessions
        SET status = :cancelledStatus
        WHERE status IN ('PLAYING', 'ALARMING')
        """
    )
    fun cancelActiveSessions(cancelledStatus: SleepSessionStatus): Int

    @Query(
        """
        UPDATE sleep_sessions
        SET status = :cancelledStatus
        WHERE alarmId = :alarmId
          AND status IN ('PLAYING', 'ALARMING')
        """
    )
    fun cancelActiveSessionsForAlarm(
        alarmId: String,
        cancelledStatus: SleepSessionStatus
    ): Int

    @Query(
        """
        UPDATE sleep_sessions
        SET status = :status
        WHERE sessionId = :sessionId
          AND status IN ('PLAYING', 'ALARMING')
        """
    )
    fun updateActiveSessionStatus(
        sessionId: String,
        status: SleepSessionStatus
    ): Int

    @Query(
        """
        SELECT * FROM sleep_sessions
        WHERE status IN ('PLAYING', 'ALARMING')
        ORDER BY startedAtEpochMillis DESC
        LIMIT 1
        """
    )
    fun getActiveSession(): SleepSessionEntity?
}
