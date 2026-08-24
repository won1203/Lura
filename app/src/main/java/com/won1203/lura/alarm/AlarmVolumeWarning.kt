package com.won1203.lura.alarm

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.won1203.lura.R

object AlarmVolumeWarning {
    fun runOrWarn(fragment: Fragment, onContinue: () -> Unit) {
        val context = fragment.requireContext()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0) {
            onContinue()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.alarm_volume_zero_title)
            .setMessage(R.string.alarm_volume_zero_message)
            .setNegativeButton(R.string.alarm_volume_continue) { _, _ -> onContinue() }
            .setPositiveButton(R.string.alarm_volume_open_settings) { _, _ ->
                fragment.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            }
            .show()
    }
}
