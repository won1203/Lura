package com.won1203.lura.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.won1203.lura.data.SleepSessionStatus

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
        UPDATE sleep_sessions
        SET status = :completedStatus
        WHERE alarmId = :alarmId
          AND status IN ('PLAYING', 'ALARMING')
        """
    )
    fun completeActiveSessionsForAlarm(
        alarmId: String,
        completedStatus: SleepSessionStatus
    ): Int

    @Query(
        """
        UPDATE sleep_sessions
        SET status = :completedStatus
        WHERE status IN ('PLAYING', 'ALARMING')
          AND targetAlarmAtEpochMillis <= :nowEpochMillis
        """
    )
    fun completeExpiredActiveSessions(
        nowEpochMillis: Long,
        completedStatus: SleepSessionStatus
    ): Int

    @Query(
        """
        UPDATE sleep_sessions
        SET targetAlarmAtEpochMillis = :targetAlarmAtEpochMillis
        WHERE alarmId = :alarmId
          AND status IN ('PLAYING', 'ALARMING')
        """
    )
    fun updateActiveSessionTarget(
        alarmId: String,
        targetAlarmAtEpochMillis: Long
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

    @Query(
        """
        SELECT * FROM sleep_sessions
        WHERE status = 'COMPLETED'
          AND targetAlarmAtEpochMillis >= :startInclusiveEpochMillis
          AND targetAlarmAtEpochMillis < :endExclusiveEpochMillis
        ORDER BY targetAlarmAtEpochMillis ASC
        """
    )
    fun getCompletedSessionsInTargetRange(
        startInclusiveEpochMillis: Long,
        endExclusiveEpochMillis: Long
    ): List<SleepSessionEntity>
}
