package com.example.lura.data

interface AlarmRepository {
    fun getAlarms(): List<AlarmSchedule>
    fun saveAlarm(
        category: SoundCategory,
        sound: SoundItem,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>
    ): AlarmSchedule
    fun setAlarmEnabled(alarmId: String, isEnabled: Boolean): AlarmSchedule?
}
