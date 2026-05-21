package com.example.lura.data

data class ScheduledAlarmResult(
    val alarmSchedule: AlarmSchedule,
    val sleepWindow: SleepWindow,
    val sleepSession: SleepSession?
)

interface SaveAlarmAndStartSleepSession {
    fun execute(
        category: SoundCategory?,
        sound: SoundItem?,
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        startImmediately: Boolean
    ): ScheduledAlarmResult
}
