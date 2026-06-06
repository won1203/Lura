package com.example.lura

object SleepReportTime {
    private const val MILLIS_PER_MINUTE = 60_000L

    fun displayedMinuteDurationMillis(
        startedAtEpochMillis: Long,
        targetAlarmAtEpochMillis: Long
    ): Long {
        val startedAtMinute = startedAtEpochMillis / MILLIS_PER_MINUTE
        val targetAlarmAtMinute = targetAlarmAtEpochMillis / MILLIS_PER_MINUTE
        return ((targetAlarmAtMinute - startedAtMinute).coerceAtLeast(0L)) * MILLIS_PER_MINUTE
    }
}
