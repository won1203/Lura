package com.won1203.lura.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.won1203.lura.data.AlarmWeekday

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val categoryName: String,
    val soundId: String,
    val soundTitle: String,
    val soundTags: String,
    val soundDurationMinutes: Int,
    val soundObjectKey: String,
    val sleepStartHour: Int,
    val sleepStartMinute: Int,
    val hour: Int,
    val minute: Int,
    val weekdays: List<AlarmWeekday>,
    val isEnabled: Boolean,
    val createdAtEpochMillis: Long
)
