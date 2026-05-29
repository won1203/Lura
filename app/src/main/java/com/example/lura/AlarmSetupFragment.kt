package com.example.lura

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.lura.alarm.AlarmScheduler
import com.example.lura.data.AlarmRepository
import com.example.lura.data.AlarmRepositoryProvider
import com.example.lura.data.AlarmWeekday
import com.example.lura.data.DisableAlarmAndCancelSleepSession
import com.example.lura.data.DisableAlarmAndCancelSleepSessionProvider
import com.example.lura.data.SaveAlarmAndStartSleepSession
import com.example.lura.data.SaveAlarmAndStartSleepSessionProvider
import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundItem
import com.example.lura.data.SoundRepositoryProvider
import com.example.lura.data.UnselectedAlarmSound
import com.example.lura.databinding.FragmentAlarmSetupBinding
import com.example.lura.playback.SleepPlaybackController
import com.example.lura.playback.SleepPlaybackRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.roundToInt

class AlarmSetupFragment : Fragment() {

    private var _binding: FragmentAlarmSetupBinding? = null
    private val binding get() = _binding!!
    private val soundRepository = SoundRepositoryProvider.get()
    private val alarmRepository: AlarmRepository by lazy {
        AlarmRepositoryProvider.get(requireContext().applicationContext)
    }
    private val saveAlarmAndStartSleepSession: SaveAlarmAndStartSleepSession by lazy {
        SaveAlarmAndStartSleepSessionProvider.get(requireContext().applicationContext)
    }
    private val disableAlarmAndCancelSleepSession: DisableAlarmAndCancelSleepSession by lazy {
        DisableAlarmAndCancelSleepSessionProvider.get(requireContext().applicationContext)
    }
    private val weekdays = AlarmWeekday.values().sortedBy { it.sortOrder }
    private val selectedWeekdays = weekdays.toMutableSet()
    private val weekdayButtons = mutableMapOf<AlarmWeekday, TextView>()
    private var selectedCategory: SoundCategory? = null
    private var selectedRecommendedSound: SoundItem? = null
    private var startImmediatelyOnSave = false
    private var selectedTimePickerMode = TimePickerMode.WAKE_ALARM
    private var wakeAlarmHour = DEFAULT_ALARM_HOUR
    private var wakeAlarmMinute = DEFAULT_ALARM_MINUTE
    private var sleepStartHour = DEFAULT_SLEEP_START_HOUR
    private var sleepStartMinute = DEFAULT_SLEEP_START_MINUTE

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categoryId = arguments?.getString(ARG_CATEGORY_ID)
        categoryId?.let(::loadSelectedSound)

        binding.wakeTimeModeButton.setOnClickListener {
            showTimePickerMode(TimePickerMode.WAKE_ALARM)
        }
        binding.sleepTimeModeButton.setOnClickListener {
            showTimePickerMode(TimePickerMode.SLEEP_START)
        }
        binding.sleepStartOptionRow.setOnClickListener {
            showTimePickerMode(TimePickerMode.SLEEP_START)
        }
        binding.cancelAlarmButton.setOnClickListener {
            findNavController().navigateUp()
        }

        setupTimePickers()
        binding.useCurrentTimeButton.setOnClickListener {
            showTimePickerMode(TimePickerMode.SLEEP_START)
            val now = Calendar.getInstance()
            setPickerTime(
                isSleepStart = true,
                hour = now.get(Calendar.HOUR_OF_DAY),
                minute = now.get(Calendar.MINUTE)
            )
            startImmediatelyOnSave = true
        }

        renderWeekdaySelector()
        showTimePickerMode(TimePickerMode.SLEEP_START)

        binding.saveAlarmButton.setOnClickListener {
            val repeatWeekdays = selectedWeekdays.sortedBy { it.sortOrder }
            if (repeatWeekdays.isEmpty()) {
                Toast.makeText(requireContext(), R.string.weekday_select_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                saveAlarm(categoryId, repeatWeekdays)
            }
        }
    }

    private fun loadSelectedSound(categoryId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val category = soundRepository.getCategory(categoryId)
                val recommendedSound = soundRepository.getRecommendedSound(categoryId)
                category to recommendedSound
            }.onSuccess { (category, recommendedSound) ->
                selectedCategory = category
                selectedRecommendedSound = recommendedSound
                if (category == null || recommendedSound == null) {
                    Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                selectedCategory = null
                selectedRecommendedSound = null
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveAlarm(
        categoryId: String?,
        repeatWeekdays: List<AlarmWeekday>
    ) {
        val category = selectedCategory
        val recommendedSound = selectedRecommendedSound
        val sleepStartHour = this.sleepStartHour
        val sleepStartMinute = this.sleepStartMinute
        val hour = wakeAlarmHour
        val minute = wakeAlarmMinute

        if (category == null || recommendedSound == null) {
            if (categoryId != null) {
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                return
            }

            withContext(Dispatchers.IO) {
                alarmRepository.saveAlarm(
                    category = UnselectedAlarmSound.category,
                    sound = UnselectedAlarmSound.sound,
                    sleepStartHour = sleepStartHour,
                    sleepStartMinute = sleepStartMinute,
                    hour = hour,
                    minute = minute,
                    weekdays = repeatWeekdays,
                    isEnabled = false
                )
            }
            findNavController().navigate(
                R.id.action_alarmSetupFragment_to_alarmHistoryFragment,
                bundleOf(
                    AlarmHistoryFragment.ARG_NOTICE_MESSAGE to
                        getString(R.string.alarm_saved_without_sound_notice)
                )
            )
            return
        }

        val result = withContext(Dispatchers.IO) {
            saveAlarmAndStartSleepSession.execute(
                category = category,
                sound = recommendedSound.copy(objectKey = ""),
                sleepStartHour = sleepStartHour,
                sleepStartMinute = sleepStartMinute,
                hour = hour,
                minute = minute,
                weekdays = repeatWeekdays,
                startImmediately = startImmediatelyOnSave
            )
        }

        val playbackSource = if (result.sleepSession == null) {
            null
        } else {
            runCatching {
                soundRepository.getPlaybackSource(
                    soundId = result.alarmSchedule.soundId,
                    objectKey = result.alarmSchedule.soundObjectKey.ifBlank { null }
                )
            }.onSuccess { source ->
                if (source.objectKey.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        alarmRepository.updateAlarmSoundObjectKey(result.alarmSchedule.id, source.objectKey)
                    }
                }
            }.getOrElse { error ->
                Log.e(TAG, "Failed to load playback source while saving alarm.", error)
                withContext(Dispatchers.IO) {
                    disableAlarmAndCancelSleepSession.execute(result.alarmSchedule.id)
                }
                Toast.makeText(requireContext(), R.string.alarm_playback_source_load_failed, Toast.LENGTH_LONG).show()
                findNavController().navigate(
                    R.id.action_alarmSetupFragment_to_alarmHistoryFragment,
                    bundleOf(
                        AlarmHistoryFragment.ARG_NOTICE_MESSAGE to
                            getString(R.string.alarm_saved_playback_source_failed_notice)
                    )
                )
                return
            }
        }

        AlarmScheduler.cancelAll(requireContext(), alarmRepository.getAlarms())
        val schedulePlan = AlarmScheduler.schedule(
            context = requireContext(),
            alarm = result.alarmSchedule,
            skipSleepStart = result.sleepSession != null
        )
        if (schedulePlan == null) {
            withContext(Dispatchers.IO) {
                disableAlarmAndCancelSleepSession.execute(result.alarmSchedule.id)
            }
            Toast.makeText(requireContext(), R.string.exact_alarm_schedule_failed, Toast.LENGTH_LONG).show()
            return
        }
        result.sleepSession?.let { sleepSession ->
            val sourceUri = playbackSource?.sourceUri
            if (sourceUri.isNullOrBlank()) {
                Log.e(TAG, "Sleep session was created without a playback source URI.")
                Toast.makeText(requireContext(), R.string.alarm_playback_source_load_failed, Toast.LENGTH_LONG).show()
                return
            }
            val playbackRequest = SleepPlaybackRequest.from(
                alarmSchedule = result.alarmSchedule,
                sleepSession = sleepSession,
                sourceUri = sourceUri
            )
            SleepPlaybackController.start(requireContext(), playbackRequest)
        }
        findNavController().navigate(
            R.id.action_alarmSetupFragment_to_alarmHistoryFragment,
            bundleOf(
                AlarmHistoryFragment.ARG_NOTICE_MESSAGE to
                    if (result.sleepSession != null) {
                        getString(
                            R.string.category_sleep_playback_started_notice,
                            result.alarmSchedule.categoryName
                        )
                    } else {
                        getString(
                            R.string.sleep_start_scheduled_notice,
                            result.alarmSchedule.categoryName
                        )
                    }
            )
        )
    }

    private fun renderWeekdaySelector() {
        binding.weekdaySelector.removeAllViews()
        weekdayButtons.clear()

        weekdays.forEachIndexed { index, weekday ->
            val button = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    WEEKDAY_BUTTON_SIZE_DP.dpToPx(),
                    WEEKDAY_BUTTON_SIZE_DP.dpToPx()
                ).apply {
                    if (index < weekdays.lastIndex) {
                        marginEnd = WEEKDAY_BUTTON_GAP_DP.dpToPx()
                    }
                }
                gravity = Gravity.CENTER
                text = AlarmWeekdayFormatter.shortLabel(requireContext(), weekday)
                textSize = WEEKDAY_BUTTON_TEXT_SIZE_SP
                typeface = Typeface.DEFAULT_BOLD
                isClickable = true
                isFocusable = true
                setOnClickListener { toggleWeekday(weekday) }
            }

            weekdayButtons[weekday] = button
            binding.weekdaySelector.addView(button)
        }

        updateWeekdaySelectionUi()
    }

    private fun toggleWeekday(weekday: AlarmWeekday) {
        if (selectedWeekdays.contains(weekday)) {
            selectedWeekdays.remove(weekday)
        } else {
            selectedWeekdays.add(weekday)
        }
        updateWeekdaySelectionUi()
    }

    private fun updateWeekdaySelectionUi() {
        weekdayButtons.forEach { (weekday, button) ->
            val isSelected = selectedWeekdays.contains(weekday)
            val label = AlarmWeekdayFormatter.shortLabel(requireContext(), weekday)
            button.isSelected = isSelected
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_weekday_selected else R.drawable.bg_weekday_unselected
            )
            button.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (isSelected) R.color.lura_background else R.color.lura_accent
                )
            )
            button.contentDescription = getString(
                if (isSelected) {
                    R.string.weekday_selected_content_description
                } else {
                    R.string.weekday_unselected_content_description
                },
                label
            )
        }

        val hasSelectedWeekday = selectedWeekdays.isNotEmpty()
        binding.selectedWeekdaysSummary.text =
            if (hasSelectedWeekday) {
                AlarmWeekdayFormatter.summary(requireContext(), selectedWeekdays.toList())
            } else {
                getString(R.string.weekday_select_required)
            }
        binding.saveAlarmButton.isEnabled = hasSelectedWeekday
        binding.saveAlarmButton.alpha = if (hasSelectedWeekday) ENABLED_BUTTON_ALPHA else DISABLED_BUTTON_ALPHA
    }

    private fun setupTimePickers() {
        configurePeriodPicker(binding.alarmPeriodPicker)
        configureHourPicker(binding.alarmHourPicker)
        configureMinutePicker(binding.alarmMinutePicker)
        configurePeriodPicker(binding.sleepStartPeriodPicker)
        configureHourPicker(binding.sleepStartHourPicker)
        configureMinutePicker(binding.sleepStartMinutePicker)

        setPickerTime(
            isSleepStart = false,
            hour = DEFAULT_ALARM_HOUR,
            minute = DEFAULT_ALARM_MINUTE
        )
        setPickerTime(
            isSleepStart = true,
            hour = DEFAULT_SLEEP_START_HOUR,
            minute = DEFAULT_SLEEP_START_MINUTE
        )

        binding.alarmPeriodPicker.setOnValueChangedListener { _, _ -> updateWakeAlarmTimeFromPickers() }
        binding.alarmHourPicker.setOnValueChangedListener { _, _ -> updateWakeAlarmTimeFromPickers() }
        binding.alarmMinutePicker.setOnValueChangedListener { _, _ -> updateWakeAlarmTimeFromPickers() }
        binding.sleepStartPeriodPicker.setOnValueChangedListener { _, _ ->
            startImmediatelyOnSave = false
            updateSleepStartTimeFromPickers()
        }
        binding.sleepStartHourPicker.setOnValueChangedListener { _, _ ->
            startImmediatelyOnSave = false
            updateSleepStartTimeFromPickers()
        }
        binding.sleepStartMinutePicker.setOnValueChangedListener { _, _ ->
            startImmediatelyOnSave = false
            updateSleepStartTimeFromPickers()
        }
    }

    private fun configurePeriodPicker(picker: WheelPickerView) {
        picker.selectedTextSizeSp = PERIOD_PICKER_SELECTED_TEXT_SIZE_SP
        picker.secondaryTextSizeSp = PERIOD_PICKER_SECONDARY_TEXT_SIZE_SP
        picker.setRange(
            minValue = PERIOD_AM,
            maxValue = PERIOD_PM,
            displayedValues = arrayOf(
                getString(R.string.time_period_am),
                getString(R.string.time_period_pm)
            ),
            wrapSelectorWheel = false
        )
    }

    private fun configureHourPicker(picker: WheelPickerView) {
        picker.setRange(
            minValue = MIN_DISPLAY_HOUR,
            maxValue = MAX_DISPLAY_HOUR,
            wrapSelectorWheel = true
        )
    }

    private fun configureMinutePicker(picker: WheelPickerView) {
        picker.setRange(
            minValue = MIN_MINUTE,
            maxValue = MAX_MINUTE,
            displayedValues = (MIN_MINUTE..MAX_MINUTE)
                .map { getString(R.string.two_digit_time_format, it) }
                .toTypedArray(),
            wrapSelectorWheel = true
        )
    }

    private fun setPickerTime(isSleepStart: Boolean, hour: Int, minute: Int) {
        val periodValue = if (hour < NOON_HOUR) PERIOD_AM else PERIOD_PM
        val displayHour = displayHour(hour)
        if (isSleepStart) {
            binding.sleepStartPeriodPicker.value = periodValue
            binding.sleepStartHourPicker.value = displayHour
            binding.sleepStartMinutePicker.value = minute
            updateSleepStartTimeFromPickers()
        } else {
            binding.alarmPeriodPicker.value = periodValue
            binding.alarmHourPicker.value = displayHour
            binding.alarmMinutePicker.value = minute
            updateWakeAlarmTimeFromPickers()
        }
    }

    private fun updateWakeAlarmTimeFromPickers() {
        wakeAlarmHour = toTwentyFourHour(
            periodValue = binding.alarmPeriodPicker.value,
            displayHour = binding.alarmHourPicker.value
        )
        wakeAlarmMinute = binding.alarmMinutePicker.value
    }

    private fun updateSleepStartTimeFromPickers() {
        sleepStartHour = toTwentyFourHour(
            periodValue = binding.sleepStartPeriodPicker.value,
            displayHour = binding.sleepStartHourPicker.value
        )
        sleepStartMinute = binding.sleepStartMinutePicker.value
        updateSleepStartSummary()
    }

    private fun updateSleepStartSummary() {
        val period = periodText(sleepStartHour)
        binding.sleepStartOptionSummary.text = getString(
            R.string.time_display_format,
            period,
            displayHour(sleepStartHour),
            sleepStartMinute
        )
    }

    private fun showTimePickerMode(mode: TimePickerMode) {
        selectedTimePickerMode = mode
        val isWakeAlarm = mode == TimePickerMode.WAKE_ALARM
        binding.alarmTimePickerPanel.visibility = if (isWakeAlarm) View.VISIBLE else View.GONE
        binding.sleepStartTimePickerPanel.visibility = if (isWakeAlarm) View.GONE else View.VISIBLE
        updateTimeModeButton(
            button = binding.wakeTimeModeButton,
            isSelected = isWakeAlarm
        )
        updateTimeModeButton(
            button = binding.sleepTimeModeButton,
            isSelected = !isWakeAlarm
        )
    }

    private fun updateTimeModeButton(button: TextView, isSelected: Boolean) {
        button.setBackgroundResource(
            if (isSelected) R.drawable.bg_time_mode_selected else R.drawable.bg_time_mode_unselected
        )
        button.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (isSelected) R.color.lura_background else R.color.lura_text_secondary
            )
        )
    }

    private fun periodText(hour: Int): String =
        getString(if (hour < NOON_HOUR) R.string.time_period_am else R.string.time_period_pm)

    private fun displayHour(hour: Int): Int {
        val hourInTwelveHourClock = hour % NOON_HOUR
        return if (hourInTwelveHourClock == 0) NOON_HOUR else hourInTwelveHourClock
    }

    private fun toTwentyFourHour(periodValue: Int, displayHour: Int): Int =
        when (periodValue) {
            PERIOD_AM -> if (displayHour == NOON_HOUR) 0 else displayHour
            else -> if (displayHour == NOON_HOUR) NOON_HOUR else displayHour + NOON_HOUR
        }

    private fun Int.dpToPx(): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).roundToInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_CATEGORY_ID = "categoryId"
        private const val TAG = "AlarmSetupFragment"
        private const val NOON_HOUR = 12
        private const val DEFAULT_SLEEP_START_HOUR = 23
        private const val DEFAULT_SLEEP_START_MINUTE = 0
        private const val DEFAULT_ALARM_HOUR = 7
        private const val DEFAULT_ALARM_MINUTE = 0
        private const val PERIOD_AM = 0
        private const val PERIOD_PM = 1
        private const val PERIOD_PICKER_SELECTED_TEXT_SIZE_SP = 34f
        private const val PERIOD_PICKER_SECONDARY_TEXT_SIZE_SP = 18f
        private const val MIN_DISPLAY_HOUR = 1
        private const val MAX_DISPLAY_HOUR = 12
        private const val MIN_MINUTE = 0
        private const val MAX_MINUTE = 59
        private const val WEEKDAY_BUTTON_SIZE_DP = 34
        private const val WEEKDAY_BUTTON_GAP_DP = 14
        private const val WEEKDAY_BUTTON_TEXT_SIZE_SP = 14f
        private const val ENABLED_BUTTON_ALPHA = 1f
        private const val DISABLED_BUTTON_ALPHA = 0.45f
    }

    private enum class TimePickerMode {
        WAKE_ALARM,
        SLEEP_START
    }
}
