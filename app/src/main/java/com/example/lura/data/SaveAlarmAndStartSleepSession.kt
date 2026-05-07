package com.example.lura.data

data class StartedSleepSessionResult(
    val alarmSchedule: AlarmSchedule,
    val sleepSession: SleepSession
)

interface SaveAlarmAndStartSleepSession {
    fun execute(
        category: SoundCategory?,
        sound: SoundItem?,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>
    ): StartedSleepSessionResult
}
