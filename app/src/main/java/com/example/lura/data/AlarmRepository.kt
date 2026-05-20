package com.example.lura.data

interface AlarmRepository {
    fun getAlarms(): List<AlarmSchedule>
    fun saveAlarm(
        category: SoundCategory,
        sound: SoundItem,
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        isEnabled: Boolean = true
    ): AlarmSchedule
    fun setAlarmEnabled(alarmId: String, isEnabled: Boolean): AlarmSchedule?
    fun updateAlarmSound(
        alarmId: String,
        category: SoundCategory,
        sound: SoundItem
    ): AlarmSchedule?
    fun updateAlarmSoundObjectKey(alarmId: String, objectKey: String): AlarmSchedule?
    fun deleteAlarm(alarmId: String): AlarmDeleteResult
}
