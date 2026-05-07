package com.example.lura.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.lura.data.SleepSessionStatus

@Entity(
    tableName = "sleep_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["alarmId"])
    ]
)
data class SleepSessionEntity(
    @PrimaryKey val sessionId: String,
    val alarmId: String,
    val sleepSoundId: String,
    val startedAtEpochMillis: Long,
    val targetAlarmAtEpochMillis: Long,
    val status: SleepSessionStatus
)
