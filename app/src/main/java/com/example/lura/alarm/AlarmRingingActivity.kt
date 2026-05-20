package com.example.lura.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.lura.R
import com.example.lura.databinding.ActivityAlarmRingingBinding

class AlarmRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()

        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleIntent(intent)

        binding.alarmDismissButton.setOnClickListener {
            stopAlarmAndFinish()
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    stopAlarmAndFinish()
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AlarmAppVisibility.onActivityStarted()
    }

    override fun onStop() {
        AlarmAppVisibility.onActivityStopped()
        super.onStop()
    }

    private fun configureAlarmWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun renderAlarm(intent: Intent) {
        val alarmTitle = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.alarm_ringing_default_title)
        binding.alarmRingingTitle.text = alarmTitle
    }

    private fun handleIntent(intent: Intent) {
        renderAlarm(intent)
    }

    private fun stopAlarmAndFinish() {
        startService(
            Intent(this, AlarmRingingService::class.java)
                .setAction(AlarmRingingService.ACTION_STOP)
        )
        finishAndRemoveTask()
    }

    companion object {
        const val ACTION_SHOW = "com.example.lura.alarm.action.SHOW"
    }
}
