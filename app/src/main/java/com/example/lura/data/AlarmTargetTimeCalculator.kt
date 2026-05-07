package com.example.lura.data

import java.util.Calendar
import java.util.TimeZone

class AlarmTargetTimeCalculator(
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    fun nextTargetEpochMillis(
        hour: Int,
        minute: Int,
        weekdays: List<AlarmWeekday>,
        nowEpochMillis: Long
    ): Long {
        require(weekdays.isNotEmpty()) { "weekdays must not be empty" }
        require(hour in HOURS_RANGE) { "hour must be between 0 and 23" }
        require(minute in MINUTES_RANGE) { "minute must be between 0 and 59" }

        val enabledWeekdays = weekdays.toSet()
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowEpochMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        repeat(DAYS_IN_WEEK + 1) { dayOffset ->
            val target = candidate.clone() as Calendar
            target.add(Calendar.DAY_OF_YEAR, dayOffset)
            target.set(Calendar.HOUR_OF_DAY, hour)
            target.set(Calendar.MINUTE, minute)

            val weekday = target.get(Calendar.DAY_OF_WEEK).toAlarmWeekday()
            if (enabledWeekdays.contains(weekday) && target.timeInMillis > nowEpochMillis) {
                return target.timeInMillis
            }
        }

        error("Unable to calculate the next alarm target within a weekly repeat cycle.")
    }

    private fun Int.toAlarmWeekday(): AlarmWeekday =
        when (this) {
            Calendar.SUNDAY -> AlarmWeekday.SUNDAY
            Calendar.MONDAY -> AlarmWeekday.MONDAY
            Calendar.TUESDAY -> AlarmWeekday.TUESDAY
            Calendar.WEDNESDAY -> AlarmWeekday.WEDNESDAY
            Calendar.THURSDAY -> AlarmWeekday.THURSDAY
            Calendar.FRIDAY -> AlarmWeekday.FRIDAY
            Calendar.SATURDAY -> AlarmWeekday.SATURDAY
            else -> error("Unknown calendar weekday: $this")
        }

    private companion object {
        val HOURS_RANGE = 0..23
        val MINUTES_RANGE = 0..59
        const val DAYS_IN_WEEK = 7
    }
}
