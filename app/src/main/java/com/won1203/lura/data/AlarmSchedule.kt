package com.won1203.lura.data

data class AlarmSchedule(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val soundId: String,
    val soundTitle: String,
    val soundTags: List<String>,
    val soundDurationMinutes: Int,
    val soundObjectKey: String,
    val sleepStartHour: Int,
    val sleepStartMinute: Int,
    val hour: Int,
    val minute: Int,
    val weekdays: List<AlarmWeekday>,
    val isEnabled: Boolean
)
