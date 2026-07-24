package com.won1203.lura.data

import java.util.Calendar
import java.util.TimeZone

class AlarmTargetTimeCalculator(
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    fun nextSleepWindow(
        sleepStartHour: Int,
        sleepStartMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        weekdays: List<AlarmWeekday>,
        nowEpochMillis: Long
    ): SleepWindow {
        require(weekdays.isNotEmpty()) { "weekdays must not be empty" }
        require(sleepStartHour in HOURS_RANGE) { "sleepStartHour must be between 0 and 23" }
        require(sleepStartMinute in MINUTES_RANGE) { "sleepStartMinute must be between 0 and 59" }
        require(wakeHour in HOURS_RANGE) { "wakeHour must be between 0 and 23" }
        require(wakeMinute in MINUTES_RANGE) { "wakeMinute must be between 0 and 59" }

        val enabledWeekdays = weekdays.toSet()
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowEpochMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        repeat((DAYS_IN_WEEK * 2) + 1) { dayOffset ->
            val wake = candidate.clone() as Calendar
            wake.add(Calendar.DAY_OF_YEAR, dayOffset)
            wake.set(Calendar.HOUR_OF_DAY, wakeHour)
            wake.set(Calendar.MINUTE, wakeMinute)

            val wakeWeekday = wake.get(Calendar.DAY_OF_WEEK).toAlarmWeekday()
            if (!enabledWeekdays.contains(wakeWeekday) || wake.timeInMillis <= nowEpochMillis) {
                return@repeat
            }

            val sleepStart = wake.clone() as Calendar
            sleepStart.set(Calendar.HOUR_OF_DAY, sleepStartHour)
            sleepStart.set(Calendar.MINUTE, sleepStartMinute)
            if (isOvernightSleepWindow(sleepStartHour, sleepStartMinute, wakeHour, wakeMinute)) {
                sleepStart.add(Calendar.DAY_OF_YEAR, -1)
            }

            return SleepWindow(
                sleepStartAtEpochMillis = sleepStart.timeInMillis,
                wakeAtEpochMillis = wake.timeInMillis
            )
        }

        error("Unable to calculate the next sleep window within a weekly repeat cycle.")
    }

    fun nextSleepWindow(alarm: AlarmSchedule, nowEpochMillis: Long): SleepWindow =
        nextSleepWindow(
            sleepStartHour = alarm.sleepStartHour,
            sleepStartMinute = alarm.sleepStartMinute,
            wakeHour = alarm.hour,
            wakeMinute = alarm.minute,
            weekdays = alarm.weekdays,
            nowEpochMillis = nowEpochMillis
        )

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

    private fun isOvernightSleepWindow(
        sleepStartHour: Int,
        sleepStartMinute: Int,
        wakeHour: Int,
        wakeMinute: Int
    ): Boolean =
        ((sleepStartHour * MINUTES_PER_HOUR) + sleepStartMinute) >=
            ((wakeHour * MINUTES_PER_HOUR) + wakeMinute)

    private companion object {
        val HOURS_RANGE = 0..23
        val MINUTES_RANGE = 0..59
        const val MINUTES_PER_HOUR = 60
        const val DAYS_IN_WEEK = 7
    }
}
