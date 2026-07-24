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
