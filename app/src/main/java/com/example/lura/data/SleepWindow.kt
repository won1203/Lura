package com.example.lura.data

data class SleepWindow(
    val sleepStartAtEpochMillis: Long,
    val wakeAtEpochMillis: Long
) {
    fun contains(epochMillis: Long): Boolean =
        epochMillis >= sleepStartAtEpochMillis && epochMillis < wakeAtEpochMillis
}
