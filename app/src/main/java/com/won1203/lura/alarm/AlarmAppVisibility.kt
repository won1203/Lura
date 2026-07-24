package com.won1203.lura.alarm

object AlarmAppVisibility {
    @Volatile
    private var foregroundActivityCount: Int = 0

    val isForeground: Boolean
        get() = foregroundActivityCount > 0

    fun onActivityStarted() {
        foregroundActivityCount += 1
    }

    fun onActivityStopped() {
        foregroundActivityCount = (foregroundActivityCount - 1).coerceAtLeast(0)
    }
}
