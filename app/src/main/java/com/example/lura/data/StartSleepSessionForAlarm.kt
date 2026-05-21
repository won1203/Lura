package com.example.lura.data

interface StartSleepSessionForAlarm {
    fun execute(alarmId: String): ScheduledAlarmResult?
}
