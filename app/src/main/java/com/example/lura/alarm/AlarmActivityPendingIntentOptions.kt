package com.example.lura.alarm

import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle

object AlarmActivityPendingIntentOptions {
    fun bundle(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentCreatorBackgroundActivityStartMode(
                    backgroundActivityStartMode()
                )
                .toBundle()
        } else {
            null
        }

    private fun backgroundActivityStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            legacyBackgroundActivityStartMode()
        }

    @Suppress("DEPRECATION")
    private fun legacyBackgroundActivityStartMode(): Int =
        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
}
