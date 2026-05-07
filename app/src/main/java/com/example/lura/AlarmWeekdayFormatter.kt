package com.example.lura

import android.content.Context
import com.example.lura.data.AlarmWeekday

object AlarmWeekdayFormatter {
    fun shortLabel(context: Context, weekday: AlarmWeekday): String =
        when (weekday) {
            AlarmWeekday.SUNDAY -> context.getString(R.string.weekday_sunday_short)
            AlarmWeekday.MONDAY -> context.getString(R.string.weekday_monday_short)
            AlarmWeekday.TUESDAY -> context.getString(R.string.weekday_tuesday_short)
            AlarmWeekday.WEDNESDAY -> context.getString(R.string.weekday_wednesday_short)
            AlarmWeekday.THURSDAY -> context.getString(R.string.weekday_thursday_short)
            AlarmWeekday.FRIDAY -> context.getString(R.string.weekday_friday_short)
            AlarmWeekday.SATURDAY -> context.getString(R.string.weekday_saturday_short)
        }

    fun summary(context: Context, weekdays: List<AlarmWeekday>): String {
        val sortedWeekdays = weekdays.sortedBy { it.sortOrder }
        if (sortedWeekdays.size == AlarmWeekday.values().size) {
            return context.getString(R.string.repeat_every_day)
        }

        return sortedWeekdays.joinToString(separator = " ") { weekday ->
            shortLabel(context, weekday)
        }
    }
}
