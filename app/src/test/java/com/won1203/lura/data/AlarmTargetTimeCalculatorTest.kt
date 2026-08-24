package com.won1203.lura.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmTargetTimeCalculatorTest {

    private val timeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val calculator = AlarmTargetTimeCalculator(timeZone)

    @Test
    fun nextTargetEpochMillis_returnsTodayWhenSelectedTimeIsStillFuture() {
        val now = epochMillis(2026, Calendar.MAY, 7, 21, 30)
        val expected = epochMillis(2026, Calendar.MAY, 7, 22, 0)

        val actual = calculator.nextTargetEpochMillis(
            hour = 22,
            minute = 0,
            weekdays = listOf(AlarmWeekday.THURSDAY),
            nowEpochMillis = now
        )

        assertEquals(expected, actual)
    }

    @Test
    fun nextTargetEpochMillis_returnsNextWeekWhenSelectedTimeAlreadyPassedToday() {
        val now = epochMillis(2026, Calendar.MAY, 7, 22, 30)
        val expected = epochMillis(2026, Calendar.MAY, 14, 22, 0)

        val actual = calculator.nextTargetEpochMillis(
            hour = 22,
            minute = 0,
            weekdays = listOf(AlarmWeekday.THURSDAY),
            nowEpochMillis = now
        )

        assertEquals(expected, actual)
    }

    @Test
    fun nextTargetEpochMillis_returnsNearestSelectedWeekday() {
        val now = epochMillis(2026, Calendar.MAY, 7, 22, 30)
        val expected = epochMillis(2026, Calendar.MAY, 8, 7, 0)

        val actual = calculator.nextTargetEpochMillis(
            hour = 7,
            minute = 0,
            weekdays = listOf(AlarmWeekday.MONDAY, AlarmWeekday.FRIDAY),
            nowEpochMillis = now
        )

        assertEquals(expected, actual)
    }

    @Test
    fun nextSleepWindow_keepsWakeAlarmOnSameTuesdayWhenSleepAlreadyStarted() {
        val sleepStart = epochMillis(2026, Calendar.AUGUST, 25, 6, 33)
        val now = sleepStart + 30_000L
        val expectedWake = epochMillis(2026, Calendar.AUGUST, 25, 12, 30)

        val actual = calculator.nextSleepWindow(
            sleepStartHour = 6,
            sleepStartMinute = 33,
            wakeHour = 12,
            wakeMinute = 30,
            weekdays = listOf(AlarmWeekday.TUESDAY),
            nowEpochMillis = now
        )

        assertEquals(sleepStart, actual.sleepStartAtEpochMillis)
        assertEquals(expectedWake, actual.wakeAtEpochMillis)
    }

    @Test
    fun defaultCalculator_usesLatestSystemTimeZoneForEachCalculation() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            val dynamicCalculator = AlarmTargetTimeCalculator()
            val now = epochMillis(2026, Calendar.AUGUST, 24, 20, 0)

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
            val seoulTarget = dynamicCalculator.nextTargetEpochMillis(
                hour = 21,
                minute = 0,
                weekdays = listOf(AlarmWeekday.MONDAY),
                nowEpochMillis = now
            )

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val losAngelesTarget = dynamicCalculator.nextTargetEpochMillis(
                hour = 21,
                minute = 0,
                weekdays = listOf(AlarmWeekday.MONDAY),
                nowEpochMillis = now
            )

            org.junit.Assert.assertNotEquals(seoulTarget, losAngelesTarget)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long =
        Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
