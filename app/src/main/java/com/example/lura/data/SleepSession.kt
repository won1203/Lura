package com.example.lura.data

data class SleepSession(
    val sessionId: String,
    val alarmId: String,
    val sleepSoundId: String,
    val startedAtEpochMillis: Long,
    val targetAlarmAtEpochMillis: Long,
    val status: SleepSessionStatus
)
