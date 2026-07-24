package com.won1203.lura.data

interface DisableAlarmAndCancelSleepSession {
    fun execute(alarmId: String): Boolean
}
