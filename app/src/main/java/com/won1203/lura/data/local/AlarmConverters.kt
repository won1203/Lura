package com.won1203.lura.data.local

import androidx.room.TypeConverter
import com.won1203.lura.data.AlarmWeekday
import com.won1203.lura.data.SleepSessionStatus

class AlarmConverters {
    @JvmName("weekdaysToStorageValueConverter")
    @TypeConverter
    fun weekdaysToStorageValue(weekdays: List<AlarmWeekday>): String =
        weekdays
            .sortedBy { it.sortOrder }
            .joinToString(LIST_DELIMITER) { it.name }

    @JvmName("storageValueToWeekdaysConverter")
    @TypeConverter
    fun storageValueToWeekdays(value: String): List<AlarmWeekday> {
        if (value.isBlank()) return emptyList()
        val weekdaysByName = AlarmWeekday.values().associateBy { it.name }
        return value
            .split(LIST_DELIMITER)
            .mapNotNull { weekdaysByName[it] }
            .sortedBy { it.sortOrder }
    }

    @TypeConverter
    fun sleepSessionStatusToStorageValue(status: SleepSessionStatus): String =
        status.name

    @TypeConverter
    fun storageValueToSleepSessionStatus(value: String): SleepSessionStatus =
        SleepSessionStatus.valueOf(value)

    private companion object {
        const val LIST_DELIMITER = "|"
    }
}
