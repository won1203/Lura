package com.example.lura

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.example.lura.data.AlarmSchedule
import com.example.lura.databinding.DialogAlarmTimeEditBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AlarmTimeEditDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogAlarmTimeEditBinding? = null
    private val binding get() = _binding!!

    private lateinit var alarmId: String
    private lateinit var activeMode: TimeEditMode
    private var initialSleepStartHour = 0
    private var initialSleepStartMinute = 0
    private var initialWakeHour = 0
    private var initialWakeMinute = 0
    private var sleepStartHour = 0
    private var sleepStartMinute = 0
    private var wakeHour = 0
    private var wakeMinute = 0
    private var isApplyingPickerValues = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        alarmId = args.getString(ARG_ALARM_ID).orEmpty()
        initialSleepStartHour = args.getInt(ARG_SLEEP_START_HOUR)
        initialSleepStartMinute = args.getInt(ARG_SLEEP_START_MINUTE)
        initialWakeHour = args.getInt(ARG_WAKE_HOUR)
        initialWakeMinute = args.getInt(ARG_WAKE_MINUTE)
        sleepStartHour = savedInstanceState?.getInt(STATE_SLEEP_START_HOUR) ?: initialSleepStartHour
        sleepStartMinute = savedInstanceState?.getInt(STATE_SLEEP_START_MINUTE) ?: initialSleepStartMinute
        wakeHour = savedInstanceState?.getInt(STATE_WAKE_HOUR) ?: initialWakeHour
        wakeMinute = savedInstanceState?.getInt(STATE_WAKE_MINUTE) ?: initialWakeMinute
        activeMode = TimeEditMode.valueOf(
            savedInstanceState?.getString(STATE_ACTIVE_MODE)
                ?: args.getString(ARG_INITIAL_MODE)
                ?: TimeEditMode.SLEEP_START.name
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAlarmTimeEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureTimePickers()
        binding.sleepTimeModeButton.setOnClickListener {
            showTimePickerMode(TimeEditMode.SLEEP_START)
        }
        binding.wakeTimeModeButton.setOnClickListener {
            showTimePickerMode(TimeEditMode.WAKE_ALARM)
        }
        binding.cancelTimeEditButton.setOnClickListener {
            dismiss()
        }
        binding.saveTimeEditButton.setOnClickListener {
            publishResultAndDismiss()
        }
        showTimePickerMode(activeMode)
        updateTimeSummaries()
    }

    override fun onStart() {
        super.onStart()
        configureBottomSheetBackground()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ACTIVE_MODE, activeMode.name)
        outState.putInt(STATE_SLEEP_START_HOUR, sleepStartHour)
        outState.putInt(STATE_SLEEP_START_MINUTE, sleepStartMinute)
        outState.putInt(STATE_WAKE_HOUR, wakeHour)
        outState.putInt(STATE_WAKE_MINUTE, wakeMinute)
    }

    private fun configureBottomSheetBackground() {
        (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun configureTimePickers() {
        configurePeriodPicker()
        configureHourPicker()
        configureMinutePicker()
        binding.periodPicker.setOnValueChangedListener { _, _ -> updateActiveTimeFromPickers() }
        binding.hourPicker.setOnValueChangedListener { _, _ -> updateActiveTimeFromPickers() }
        binding.minutePicker.setOnValueChangedListener { _, _ -> updateActiveTimeFromPickers() }
    }

    private fun configurePeriodPicker() {
        binding.periodPicker.selectedTextSizeSp = PERIOD_PICKER_SELECTED_TEXT_SIZE_SP
        binding.periodPicker.secondaryTextSizeSp = PERIOD_PICKER_SECONDARY_TEXT_SIZE_SP
        binding.periodPicker.setRange(
            minValue = PERIOD_AM,
            maxValue = PERIOD_PM,
            displayedValues = arrayOf(
                getString(R.string.time_period_am),
                getString(R.string.time_period_pm)
            ),
            wrapSelectorWheel = false
        )
    }

    private fun configureHourPicker() {
        binding.hourPicker.setRange(
            minValue = MIN_DISPLAY_HOUR,
            maxValue = MAX_DISPLAY_HOUR,
            wrapSelectorWheel = true
        )
    }

    private fun configureMinutePicker() {
        binding.minutePicker.setRange(
            minValue = MIN_MINUTE,
            maxValue = MAX_MINUTE,
            displayedValues = (MIN_MINUTE..MAX_MINUTE)
                .map { getString(R.string.two_digit_time_format, it) }
                .toTypedArray(),
            wrapSelectorWheel = true
        )
    }

    private fun showTimePickerMode(mode: TimeEditMode) {
        activeMode = mode
        val isWakeAlarm = mode == TimeEditMode.WAKE_ALARM
        binding.timePickerLabel.text = getString(
            if (isWakeAlarm) {
                R.string.wake_alarm_time_picker_label
            } else {
                R.string.sleep_start_time_picker_label
            }
        )
        updateTimeModeButton(binding.sleepTimeModeButton, !isWakeAlarm)
        updateTimeModeButton(binding.wakeTimeModeButton, isWakeAlarm)
        if (isWakeAlarm) {
            setPickerTime(wakeHour, wakeMinute)
        } else {
            setPickerTime(sleepStartHour, sleepStartMinute)
        }
    }

    private fun updateTimeModeButton(button: View, isSelected: Boolean) {
        button.setBackgroundResource(
            if (isSelected) R.drawable.bg_time_mode_selected else R.drawable.bg_time_mode_unselected
        )
        if (button is android.widget.TextView) {
            button.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.lura_background else R.color.lura_text_secondary
                )
            )
        }
    }

    private fun setPickerTime(hour: Int, minute: Int) {
        isApplyingPickerValues = true
        binding.periodPicker.value = if (hour < NOON_HOUR) PERIOD_AM else PERIOD_PM
        binding.hourPicker.value = displayHour(hour)
        binding.minutePicker.value = minute
        isApplyingPickerValues = false
    }

    private fun updateActiveTimeFromPickers() {
        if (isApplyingPickerValues) return

        val selectedHour = toTwentyFourHour(
            periodValue = binding.periodPicker.value,
            displayHour = binding.hourPicker.value
        )
        val selectedMinute = binding.minutePicker.value
        if (activeMode == TimeEditMode.WAKE_ALARM) {
            wakeHour = selectedHour
            wakeMinute = selectedMinute
        } else {
            sleepStartHour = selectedHour
            sleepStartMinute = selectedMinute
        }
        updateTimeSummaries()
    }

    private fun updateTimeSummaries() {
        binding.sleepStartSummary.text = getString(
            R.string.alarm_time_edit_sleep_summary,
            formatDisplayTime(sleepStartHour, sleepStartMinute)
        )
        binding.wakeAlarmSummary.text = getString(
            R.string.alarm_time_edit_wake_summary,
            formatDisplayTime(wakeHour, wakeMinute)
        )
    }

    private fun publishResultAndDismiss() {
        if (
            sleepStartHour == initialSleepStartHour &&
            sleepStartMinute == initialSleepStartMinute &&
            wakeHour == initialWakeHour &&
            wakeMinute == initialWakeMinute
        ) {
            dismiss()
            return
        }

        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(
                RESULT_ALARM_ID to alarmId,
                RESULT_SLEEP_START_HOUR to sleepStartHour,
                RESULT_SLEEP_START_MINUTE to sleepStartMinute,
                RESULT_WAKE_HOUR to wakeHour,
                RESULT_WAKE_MINUTE to wakeMinute
            )
        )
        dismiss()
    }

    private fun formatDisplayTime(hour: Int, minute: Int): String =
        getString(
            R.string.time_display_format,
            getString(if (hour < NOON_HOUR) R.string.time_period_am else R.string.time_period_pm),
            displayHour(hour),
            minute
        )

    private fun displayHour(hour: Int): Int {
        val hourInTwelveHourClock = hour % NOON_HOUR
        return if (hourInTwelveHourClock == 0) NOON_HOUR else hourInTwelveHourClock
    }

    private fun toTwentyFourHour(periodValue: Int, displayHour: Int): Int =
        when (periodValue) {
            PERIOD_AM -> if (displayHour == NOON_HOUR) 0 else displayHour
            else -> if (displayHour == NOON_HOUR) NOON_HOUR else displayHour + NOON_HOUR
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    enum class TimeEditMode {
        SLEEP_START,
        WAKE_ALARM
    }

    companion object {
        const val REQUEST_KEY = "alarm_time_edit_result"
        const val RESULT_ALARM_ID = "alarmId"
        const val RESULT_SLEEP_START_HOUR = "sleepStartHour"
        const val RESULT_SLEEP_START_MINUTE = "sleepStartMinute"
        const val RESULT_WAKE_HOUR = "wakeHour"
        const val RESULT_WAKE_MINUTE = "wakeMinute"
        const val TAG = "AlarmTimeEditDialog"

        private const val ARG_ALARM_ID = "alarmId"
        private const val ARG_SLEEP_START_HOUR = "sleepStartHour"
        private const val ARG_SLEEP_START_MINUTE = "sleepStartMinute"
        private const val ARG_WAKE_HOUR = "wakeHour"
        private const val ARG_WAKE_MINUTE = "wakeMinute"
        private const val ARG_INITIAL_MODE = "initialMode"
        private const val STATE_ACTIVE_MODE = "stateActiveMode"
        private const val STATE_SLEEP_START_HOUR = "stateSleepStartHour"
        private const val STATE_SLEEP_START_MINUTE = "stateSleepStartMinute"
        private const val STATE_WAKE_HOUR = "stateWakeHour"
        private const val STATE_WAKE_MINUTE = "stateWakeMinute"
        private const val NOON_HOUR = 12
        private const val PERIOD_AM = 0
        private const val PERIOD_PM = 1
        private const val PERIOD_PICKER_SELECTED_TEXT_SIZE_SP = 34f
        private const val PERIOD_PICKER_SECONDARY_TEXT_SIZE_SP = 18f
        private const val MIN_DISPLAY_HOUR = 1
        private const val MAX_DISPLAY_HOUR = 12
        private const val MIN_MINUTE = 0
        private const val MAX_MINUTE = 59

        fun newInstance(
            alarm: AlarmSchedule,
            initialMode: TimeEditMode
        ): AlarmTimeEditDialogFragment =
            AlarmTimeEditDialogFragment().apply {
                arguments = bundleOf(
                    ARG_ALARM_ID to alarm.id,
                    ARG_SLEEP_START_HOUR to alarm.sleepStartHour,
                    ARG_SLEEP_START_MINUTE to alarm.sleepStartMinute,
                    ARG_WAKE_HOUR to alarm.hour,
                    ARG_WAKE_MINUTE to alarm.minute,
                    ARG_INITIAL_MODE to initialMode.name
                )
            }
    }
}
