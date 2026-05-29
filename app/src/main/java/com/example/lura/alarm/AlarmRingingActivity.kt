package com.example.lura.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.lura.R
import com.example.lura.databinding.ActivityAlarmRingingBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AlarmRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingingBinding
    private var stopReceiverRegistered = false
    private var visibilityRegistered = false
    private var handledTriggerKey: String? = null
    private val alarmStoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AlarmRingingService.ACTION_ALARM_STOPPED) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handledTriggerKey = savedInstanceState?.getString(KEY_HANDLED_TRIGGER)
        configureAlarmWindow()
        if (finishIfAlarmIsNotRinging(intent)) return

        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()
        handleIntent(intent)

        binding.alarmSnoozeButton.setOnClickListener {
            snoozeAlarmAndFinish()
        }
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

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_HANDLED_TRIGGER, handledTriggerKey)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        if (finishIfAlarmIsNotRinging(intent)) return
        registerAlarmStoppedReceiver()
        AlarmAppVisibility.onActivityStarted()
        visibilityRegistered = true
    }

    override fun onStop() {
        if (visibilityRegistered) {
            AlarmAppVisibility.onActivityStopped()
            visibilityRegistered = false
        }
        unregisterAlarmStoppedReceiver()
        super.onStop()
    }

    private fun configureAlarmWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
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

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun renderAlarm(intent: Intent) {
        val displayTime = alarmDisplayTime(intent)
        binding.alarmTimeText.text = getString(
            R.string.alarm_ringing_time_format,
            displayTime.hour,
            displayTime.minute
        )
        binding.alarmDateText.text = DATE_FORMATTER.format(displayTime.calendar.time)
    }

    private fun handleIntent(intent: Intent) {
        if (finishIfAlarmIsNotRinging(intent)) return
        renderAlarm(intent)
        handleAlarmTrigger(intent)
    }

    private fun handleAlarmTrigger(intent: Intent) {
        if (intent.action != ACTION_TRIGGER) return

        val triggerKey = triggerKey(intent)
        if (handledTriggerKey == triggerKey) return
        handledTriggerKey = triggerKey

        AlarmTriggerDispatcher.trigger(
            context = this,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            alarmTitle = alarmTitle(intent),
            showRingingScreen = false,
            alarmHour = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_HOUR),
            alarmMinute = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_MINUTE),
            triggerAtEpochMillis = intent.getLongExtra(
                AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS,
                0L
            )
        )
    }

    private fun snoozeAlarmAndFinish() {
        val triggerAtEpochMillis = System.currentTimeMillis() + SNOOZE_DURATION_MS
        AlarmScheduler.scheduleSnooze(
            context = this,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            alarmTitle = alarmTitle(intent),
            triggerAtEpochMillis = triggerAtEpochMillis
        )
        stopAlarmAndFinish()
    }

    private fun stopAlarmAndFinish() {
        startService(
            Intent(this, AlarmRingingService::class.java)
                .setAction(AlarmRingingService.ACTION_STOP)
        )
        finishAndRemoveTask()
    }

    private fun alarmDisplayTime(intent: Intent): AlarmDisplayTime {
        val calendar = Calendar.getInstance().apply {
            val triggerAtEpochMillis = intent.getLongExtra(
                AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS,
                0L
            )
            if (triggerAtEpochMillis > 0L) {
                timeInMillis = triggerAtEpochMillis
            }
        }
        val hour = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_HOUR)
            ?: calendar.get(Calendar.HOUR_OF_DAY)
        val minute = intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_MINUTE)
            ?: calendar.get(Calendar.MINUTE)

        return AlarmDisplayTime(
            calendar = calendar,
            hour = hour,
            minute = minute
        )
    }

    private fun alarmTitle(intent: Intent): String =
        intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_TITLE)
            ?.takeIf(String::isNotBlank)
            ?: getString(R.string.alarm_ringing_default_title)

    private fun Intent.optionalIntExtra(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null

    private fun triggerKey(intent: Intent): String =
        listOf(
            intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            intent.getLongExtra(AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS, 0L).toString(),
            intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_HOUR)?.toString().orEmpty(),
            intent.optionalIntExtra(AlarmRingingService.EXTRA_ALARM_MINUTE)?.toString().orEmpty()
        ).joinToString(separator = ":")

    private fun finishIfAlarmIsNotRinging(intent: Intent): Boolean {
        if (isUnprocessedTrigger(intent)) return false

        val isCurrentRinging = AlarmRingingState.isCurrentRinging(
            context = this,
            alarmId = intent.getStringExtra(AlarmRingingService.EXTRA_ALARM_ID).orEmpty(),
            triggerAtEpochMillis = intent.getLongExtra(
                AlarmRingingService.EXTRA_ALARM_TRIGGER_AT_EPOCH_MILLIS,
                0L
            )
        )
        if (isCurrentRinging) return false

        finishAndRemoveTask()
        return true
    }

    private fun isUnprocessedTrigger(intent: Intent): Boolean =
        intent.action == ACTION_TRIGGER && handledTriggerKey != triggerKey(intent)

    private fun registerAlarmStoppedReceiver() {
        if (stopReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            alarmStoppedReceiver,
            IntentFilter(AlarmRingingService.ACTION_ALARM_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        stopReceiverRegistered = true
    }

    private fun unregisterAlarmStoppedReceiver() {
        if (!stopReceiverRegistered) return
        unregisterReceiver(alarmStoppedReceiver)
        stopReceiverRegistered = false
    }

    companion object {
        const val ACTION_SHOW = "com.example.lura.alarm.action.SHOW"
        const val ACTION_TRIGGER = "com.example.lura.alarm.action.TRIGGER"

        private const val KEY_HANDLED_TRIGGER = "alarm.handledTrigger"
        private const val SNOOZE_DURATION_MS = 5 * 60 * 1_000L
        private val DATE_FORMATTER = SimpleDateFormat("M월 d일 EEEE", Locale.KOREAN)
    }

    private data class AlarmDisplayTime(
        val calendar: Calendar,
        val hour: Int,
        val minute: Int
    )
}
