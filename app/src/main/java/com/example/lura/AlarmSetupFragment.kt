package com.example.lura

import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
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

        binding.alarmTimePicker.setIs24HourView(true)
        binding.alarmTimePicker.hour = DEFAULT_ALARM_HOUR
        binding.alarmTimePicker.minute = DEFAULT_ALARM_MINUTE
        updateSelectedTimeOverlay(DEFAULT_ALARM_HOUR, DEFAULT_ALARM_MINUTE)
        styleTimePicker(binding.alarmTimePicker)
        binding.alarmTimePicker.post { styleTimePicker(binding.alarmTimePicker) }
        binding.alarmTimePicker.setOnTimeChangedListener { picker, hourOfDay, minute ->
            updateSelectedTimeOverlay(hourOfDay, minute)
            picker.post { styleTimePicker(picker) }
            picker.postDelayed({ styleTimePicker(picker) }, TIME_PICKER_RESTYLE_DELAY_MS)
        }

        renderWeekdaySelector()

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
        val hour = binding.alarmTimePicker.hour
        val minute = binding.alarmTimePicker.minute

        if (category == null || recommendedSound == null) {
            if (categoryId != null) {
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                return
            }

            withContext(Dispatchers.IO) {
                alarmRepository.saveAlarm(
                    category = UnselectedAlarmSound.category,
                    sound = UnselectedAlarmSound.sound,
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

        val playbackSource = runCatching {
            soundRepository.getPlaybackSource(recommendedSound.id)
        }.getOrElse {
            Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val fixedSound = recommendedSound.copy(objectKey = playbackSource.objectKey)

        val result = withContext(Dispatchers.IO) {
            saveAlarmAndStartSleepSession.execute(
                category = category,
                sound = fixedSound,
                hour = hour,
                minute = minute,
                weekdays = repeatWeekdays
            )
        }
        AlarmScheduler.cancelAll(requireContext(), alarmRepository.getAlarms())
        val scheduled = AlarmScheduler.schedule(requireContext(), result.alarmSchedule, result.sleepSession)
        if (!scheduled) {
            withContext(Dispatchers.IO) {
                disableAlarmAndCancelSleepSession.execute(result.alarmSchedule.id)
            }
            Toast.makeText(requireContext(), R.string.exact_alarm_schedule_failed, Toast.LENGTH_LONG).show()
            return
        }
        val playbackRequest = SleepPlaybackRequest.from(
            alarmSchedule = result.alarmSchedule,
            sleepSession = result.sleepSession,
            sourceUri = playbackSource.sourceUri
        )
        SleepPlaybackController.start(requireContext(), playbackRequest)
        findNavController().navigate(
            R.id.action_alarmSetupFragment_to_alarmHistoryFragment,
            bundleOf(
                AlarmHistoryFragment.ARG_NOTICE_MESSAGE to
                    getString(
                        R.string.category_sleep_playback_started_notice,
                        result.alarmSchedule.categoryName
                )
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

    private fun updateSelectedTimeOverlay(hour: Int, minute: Int) {
        binding.selectedHourText.text = getString(R.string.two_digit_time_format, hour)
        binding.selectedMinuteText.text = getString(R.string.two_digit_time_format, minute)
    }

    private fun styleTimePicker(view: View) {
        val wheelTextColor = ContextCompat.getColor(requireContext(), R.color.lura_text_secondary)
        when (view) {
            is NumberPicker -> {
                view.descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                applyNumberPickerStyle(view, wheelTextColor)
            }
            is ViewGroup -> {
                repeat(view.childCount) { index ->
                    styleTimePicker(view.getChildAt(index))
                }
            }
        }
    }

    private fun applyTimeTextStyle(view: View, textColor: Int) {
        when (view) {
            is EditText -> {
                view.setTextColor(Color.TRANSPARENT)
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TIME_PICKER_WHEEL_TEXT_SIZE_SP)
            }
            is TextView -> {
                view.setTextColor(textColor)
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TIME_PICKER_WHEEL_TEXT_SIZE_SP)
            }
            is ViewGroup -> {
                repeat(view.childCount) { index ->
                    applyTimeTextStyle(view.getChildAt(index), textColor)
                }
            }
        }
    }

    private fun applyNumberPickerStyle(numberPicker: NumberPicker, textColor: Int) {
        setNumberPickerTextColor(numberPicker, textColor)
        applyTimeTextStyle(numberPicker, textColor)
        numberPicker.invalidate()
    }

    private fun setNumberPickerTextColor(numberPicker: NumberPicker, textColor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            numberPicker.setTextColor(textColor)
        }
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
        private const val DEFAULT_ALARM_HOUR = 7
        private const val DEFAULT_ALARM_MINUTE = 0
        private const val TIME_PICKER_WHEEL_TEXT_SIZE_SP = 22f
        private const val TIME_PICKER_RESTYLE_DELAY_MS = 80L
        private const val WEEKDAY_BUTTON_SIZE_DP = 44
        private const val WEEKDAY_BUTTON_GAP_DP = 10
        private const val WEEKDAY_BUTTON_TEXT_SIZE_SP = 16f
        private const val ENABLED_BUTTON_ALPHA = 1f
        private const val DISABLED_BUTTON_ALPHA = 0.45f
    }
}
