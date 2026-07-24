package com.won1203.lura.data

data class SleepSession(
    val sessionId: String,
    val alarmId: String,
    val sleepSoundId: String,
    val startedAtEpochMillis: Long,
    val targetAlarmAtEpochMillis: Long,
    val status: SleepSessionStatus
)
