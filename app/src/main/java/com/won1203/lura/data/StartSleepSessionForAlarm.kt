package com.won1203.lura.data

interface StartSleepSessionForAlarm {
    fun execute(alarmId: String): ScheduledAlarmResult?
}
