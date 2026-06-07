package com.example.lura

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.lura.alarm.AlarmScheduler
import com.example.lura.data.AlarmRepository
import com.example.lura.data.AlarmRepositoryProvider
import com.example.lura.data.AlarmSchedule
import com.example.lura.data.AlarmTargetTimeCalculator
import com.example.lura.data.DisableAlarmAndCancelSleepSession
import com.example.lura.data.DisableAlarmAndCancelSleepSessionProvider
import com.example.lura.data.StartSleepSessionForAlarm
import com.example.lura.data.StartSleepSessionForAlarmProvider
import com.example.lura.data.SoundCategory
import com.example.lura.data.SoundRepositoryProvider
import com.example.lura.data.UnselectedAlarmSound
import com.example.lura.databinding.FragmentAlarmHistoryBinding
import com.example.lura.playback.SleepPlaybackController
import com.example.lura.playback.SleepPlaybackRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlarmHistoryFragment : Fragment() {

    private var _binding: FragmentAlarmHistoryBinding? = null
    private val binding get() = _binding!!
    private val alarmRepository: AlarmRepository by lazy {
        AlarmRepositoryProvider.get(requireContext().applicationContext)
    }
    private val startSleepSessionForAlarm: StartSleepSessionForAlarm by lazy {
        StartSleepSessionForAlarmProvider.get(requireContext().applicationContext)
    }
    private val disableAlarmAndCancelSleepSession: DisableAlarmAndCancelSleepSession by lazy {
        DisableAlarmAndCancelSleepSessionProvider.get(requireContext().applicationContext)
    }
    private val soundRepository = SoundRepositoryProvider.get()
    private val alarmTargetTimeCalculator = AlarmTargetTimeCalculator()
    private val nextAlarmDateFormat = SimpleDateFormat("M월 d일 (E) a h:mm", Locale.KOREAN)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addAlarmButton.setOnClickListener {
            findNavController().navigate(R.id.alarmSetupFragment)
        }
        registerAlarmTimeEditResultListener()
        renderAlarms(alarmRepository.getAlarms())
        showNoticeIfPresent()
    }

    private fun renderAlarms(alarms: List<AlarmSchedule>) {
        renderNextAlarmSummary(alarms)
        binding.alarmList.removeAllViews()
        binding.emptyAlarmMessage.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE

        alarms.forEach { alarm ->
            val itemView = layoutInflater.inflate(
                R.layout.item_alarm_schedule,
                binding.alarmList,
                false
            )
            itemView.setOnClickListener { showCategorySelectionDialog(alarm) }
            bindAlarmItem(itemView, alarm)
            binding.alarmList.addView(itemView)
        }
    }

    private fun registerAlarmTimeEditResultListener() {
        parentFragmentManager.setFragmentResultListener(
            AlarmTimeEditDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val alarmId = result.getString(AlarmTimeEditDialogFragment.RESULT_ALARM_ID)
                ?: return@setFragmentResultListener
            updateAlarmTimes(
                alarmId = alarmId,
                sleepStartHour = result.getInt(AlarmTimeEditDialogFragment.RESULT_SLEEP_START_HOUR),
                sleepStartMinute = result.getInt(AlarmTimeEditDialogFragment.RESULT_SLEEP_START_MINUTE),
                hour = result.getInt(AlarmTimeEditDialogFragment.RESULT_WAKE_HOUR),
                minute = result.getInt(AlarmTimeEditDialogFragment.RESULT_WAKE_MINUTE)
            )
        }
    }

    private fun bindAlarmItem(itemView: View, alarm: AlarmSchedule) {
        itemView.alpha = if (alarm.isEnabled) ENABLED_ALARM_ALPHA else DISABLED_ALARM_ALPHA
        val wakeTimeClickListener = View.OnClickListener {
            showAlarmTimeEditDialog(alarm, AlarmTimeEditDialogFragment.TimeEditMode.WAKE_ALARM)
        }
        val alarmTimePeriod = itemView.findViewById<TextView>(R.id.alarm_time_period)
        alarmTimePeriod.text =
            getString(if (alarm.hour < NOON_HOUR) R.string.time_period_am else R.string.time_period_pm)
        configureTimeEditClickTarget(
            view = alarmTimePeriod,
            contentDescription = getString(R.string.alarm_wake_time_edit_content_description),
            listener = wakeTimeClickListener
        )
        val alarmTime = itemView.findViewById<TextView>(R.id.alarm_time)
        alarmTime.text =
            getString(R.string.alarm_time_display_format, displayHour(alarm.hour), alarm.minute)
        configureTimeEditClickTarget(
            view = alarmTime,
            contentDescription = getString(R.string.alarm_wake_time_edit_content_description),
            listener = wakeTimeClickListener
        )
        itemView.findViewById<TextView>(R.id.alarm_category).text = alarm.categoryName
        val sleepStartTime = itemView.findViewById<TextView>(R.id.alarm_sleep_start_time)
        sleepStartTime.text =
            getString(R.string.sleep_start_time_format, alarm.sleepStartHour, alarm.sleepStartMinute)
        configureTimeEditClickTarget(
            view = sleepStartTime,
            contentDescription = getString(R.string.alarm_sleep_start_time_edit_content_description),
            listener = View.OnClickListener {
                showAlarmTimeEditDialog(alarm, AlarmTimeEditDialogFragment.TimeEditMode.SLEEP_START)
            }
        )
        itemView.findViewById<TextView>(R.id.alarm_sound_title).text = alarm.soundTitle
        itemView.findViewById<TextView>(R.id.alarm_repeat_days).text =
            AlarmWeekdayFormatter.summary(requireContext(), alarm.weekdays)
        itemView.findViewById<TextView>(R.id.alarm_status).text = getStatusText(alarm.isEnabled)

        val enabledSwitch = itemView.findViewById<SwitchMaterial>(R.id.alarm_enabled_switch)
        // View 재사용 여부와 무관하게 초기 checked 세팅이 저장소 갱신 이벤트로 오인되지 않게 분리한다.
        enabledSwitch.setOnCheckedChangeListener(null)
        enabledSwitch.isChecked = alarm.isEnabled
        enabledSwitch.setOnCheckedChangeListener(createAlarmToggleListener(alarm))

        itemView.findViewById<MaterialButton>(R.id.delete_alarm_button).setOnClickListener {
            showDeleteAlarmDialog(alarm)
        }
    }

    private fun configureTimeEditClickTarget(
        view: View,
        contentDescription: String,
        listener: View.OnClickListener
    ) {
        view.isClickable = true
        view.isFocusable = true
        view.contentDescription = contentDescription
        val outValue = TypedValue()
        if (
            view.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless,
                outValue,
                true
            )
        ) {
            view.foreground = ContextCompat.getDrawable(view.context, outValue.resourceId)
        }
        view.setOnClickListener(listener)
    }

    private fun showAlarmTimeEditDialog(
        alarm: AlarmSchedule,
        initialMode: AlarmTimeEditDialogFragment.TimeEditMode
    ) {
        AlarmTimeEditDialogFragment.newInstance(alarm, initialMode)
            .show(parentFragmentManager, AlarmTimeEditDialogFragment.TAG)
    }

    private fun renderNextAlarmSummary(alarms: List<AlarmSchedule>) {
        val nowEpochMillis = System.currentTimeMillis()
        val nextAlarmEpochMillis = alarms.asSequence()
            .filter { it.isEnabled && it.weekdays.isNotEmpty() }
            .mapNotNull { alarm ->
                runCatching {
                    alarmTargetTimeCalculator.nextTargetEpochMillis(
                        hour = alarm.hour,
                        minute = alarm.minute,
                        weekdays = alarm.weekdays,
                        nowEpochMillis = nowEpochMillis
                    )
                }.getOrNull()
            }
            .minOrNull()

        if (nextAlarmEpochMillis == null) {
            binding.nextAlarmTitle.text = getString(R.string.next_alarm_none_title)
            binding.nextAlarmDatetime.text = getString(R.string.next_alarm_none_subtitle)
            return
        }

        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(
            (nextAlarmEpochMillis - nowEpochMillis).coerceAtLeast(MINUTE_IN_MILLIS - 1) +
                (MINUTE_IN_MILLIS - 1)
        )
        val hours = remainingMinutes / MINUTES_PER_HOUR
        val minutes = remainingMinutes % MINUTES_PER_HOUR
        binding.nextAlarmTitle.text =
            if (hours > 0) {
                getString(R.string.next_alarm_countdown_format, hours, minutes)
            } else {
                getString(R.string.next_alarm_countdown_minutes_format, minutes)
            }
        binding.nextAlarmDatetime.text = nextAlarmDateFormat.format(Date(nextAlarmEpochMillis))
    }

    private fun displayHour(hour: Int): Int {
        val hourInTwelveHourClock = hour % NOON_HOUR
        return if (hourInTwelveHourClock == 0) NOON_HOUR else hourInTwelveHourClock
    }

    private fun createAlarmToggleListener(alarm: AlarmSchedule): CompoundButton.OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestAlarmActivation(alarm)
            } else {
                AlarmScheduler.cancel(requireContext(), alarm.id)
                val cancelledActivePlayback = disableAlarmAndCancelSleepSession.execute(alarm.id)
                if (cancelledActivePlayback) {
                    SleepPlaybackController.stop(requireContext())
                }
                renderAlarms(alarmRepository.getAlarms())
            }
        }

    private fun getStatusText(isEnabled: Boolean): String =
        getString(if (isEnabled) R.string.alarm_status_on else R.string.alarm_status_off)

    private fun showCategorySelectionDialog(alarm: AlarmSchedule) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                soundRepository.getCategories()
            }.onSuccess { categories ->
                val categoryNames = categories.map { it.name }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.category_selection_dialog_title)
                    .setItems(categoryNames) { _, which ->
                        updateAlarmCategory(alarm.id, categories[which])
                    }
                    .show()
            }.onFailure {
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAlarmCategory(alarmId: String, category: SoundCategory) {
        viewLifecycleOwner.lifecycleScope.launch {
            val recommendedSound = runCatching {
                soundRepository.getRecommendedSound(category.id)
            }.getOrElse {
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            } ?: return@launch
            val playbackSource = runCatching {
                soundRepository.getPlaybackSource(recommendedSound.id)
            }.getOrElse {
                Log.e(TAG, "Failed to load playback source while updating alarm category.", it)
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val fixedSound = recommendedSound.copy(objectKey = playbackSource.objectKey)

            withContext(Dispatchers.IO) {
                alarmRepository.updateAlarmSound(
                    alarmId = alarmId,
                    category = category,
                    sound = fixedSound
                )
                alarmRepository.getAlarms()
            }.also(::renderAlarms)
        }
    }

    private fun updateAlarmTimes(
        alarmId: String,
        sleepStartHour: Int,
        sleepStartMinute: Int,
        hour: Int,
        minute: Int
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedAlarm = withContext(Dispatchers.IO) {
                alarmRepository.updateAlarmTimes(
                    alarmId = alarmId,
                    sleepStartHour = sleepStartHour,
                    sleepStartMinute = sleepStartMinute,
                    hour = hour,
                    minute = minute
                )
            }
            if (updatedAlarm == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.alarm_time_edit_failed_notice,
                    Toast.LENGTH_SHORT
                ).show()
                renderAlarms(loadAlarms())
                return@launch
            }

            if (updatedAlarm.isEnabled) {
                rescheduleEnabledAlarmAfterTimeChange(updatedAlarm)
            } else {
                Toast.makeText(
                    requireContext(),
                    R.string.alarm_time_edit_saved_notice,
                    Toast.LENGTH_SHORT
                ).show()
                renderAlarms(loadAlarms())
            }
        }
    }

    private suspend fun rescheduleEnabledAlarmAfterTimeChange(alarm: AlarmSchedule) {
        if (alarm.soundId == UnselectedAlarmSound.SOUND_ID) {
            disableAlarmAfterTimeEditFailure(alarm.id)
            Toast.makeText(
                requireContext(),
                R.string.alarm_sound_required_to_enable,
                Toast.LENGTH_SHORT
            ).show()
            renderAlarms(loadAlarms())
            return
        }

        AlarmScheduler.cancelAll(requireContext(), loadAlarms())
        SleepPlaybackController.stop(requireContext())

        val result = withContext(Dispatchers.IO) {
            startSleepSessionForAlarm.execute(alarm.id)
        }
        if (result == null) {
            disableAlarmAfterTimeEditFailure(alarm.id)
            Toast.makeText(
                requireContext(),
                R.string.alarm_sound_required_to_enable,
                Toast.LENGTH_SHORT
            ).show()
            renderAlarms(loadAlarms())
            return
        }

        val playbackSource = if (result.sleepSession != null) {
            runCatching {
                soundRepository.getPlaybackSource(
                    soundId = result.alarmSchedule.soundId,
                    objectKey = result.alarmSchedule.soundObjectKey.ifBlank { null }
                )
            }.getOrElse {
                Log.e(TAG, "Failed to load playback source while updating alarm times.", it)
                disableAlarmAfterTimeEditFailure(result.alarmSchedule.id)
                Toast.makeText(
                    requireContext(),
                    R.string.alarm_playback_source_load_failed,
                    Toast.LENGTH_LONG
                ).show()
                renderAlarms(loadAlarms())
                return
            }
        } else {
            null
        }

        if (
            AlarmScheduler.schedule(
                context = requireContext(),
                alarm = result.alarmSchedule,
                skipSleepStart = result.sleepSession != null
            ) == null
        ) {
            disableAlarmAfterTimeEditFailure(result.alarmSchedule.id)
            Toast.makeText(requireContext(), R.string.exact_alarm_schedule_failed, Toast.LENGTH_LONG).show()
            renderAlarms(loadAlarms())
            return
        }

        if (
            playbackSource != null &&
            playbackSource.objectKey.isNotBlank() &&
            playbackSource.objectKey != result.alarmSchedule.soundObjectKey
        ) {
            withContext(Dispatchers.IO) {
                alarmRepository.updateAlarmSoundObjectKey(result.alarmSchedule.id, playbackSource.objectKey)
            }
        }

        val sleepSession = result.sleepSession
        if (sleepSession != null && playbackSource != null) {
            SleepPlaybackController.start(
                requireContext(),
                SleepPlaybackRequest.from(
                    alarmSchedule = result.alarmSchedule,
                    sleepSession = sleepSession,
                    sourceUri = playbackSource.sourceUri
                )
            )
        }

        Toast.makeText(
            requireContext(),
            R.string.alarm_time_edit_saved_notice,
            Toast.LENGTH_SHORT
        ).show()
        renderAlarms(loadAlarms())
    }

    private suspend fun disableAlarmAfterTimeEditFailure(alarmId: String) {
        AlarmScheduler.cancel(requireContext(), alarmId)
        val cancelledActivePlayback = withContext(Dispatchers.IO) {
            disableAlarmAndCancelSleepSession.execute(alarmId)
        }
        if (cancelledActivePlayback) {
            SleepPlaybackController.stop(requireContext())
        }
    }

    private suspend fun loadAlarms(): List<AlarmSchedule> =
        withContext(Dispatchers.IO) {
            alarmRepository.getAlarms()
        }

    private fun requestAlarmActivation(alarm: AlarmSchedule) {
        if (alarm.soundId == UnselectedAlarmSound.SOUND_ID) {
            Toast.makeText(
                requireContext(),
                R.string.alarm_sound_required_to_enable,
                Toast.LENGTH_SHORT
            ).show()
            renderAlarms(alarmRepository.getAlarms())
            return
        }

        val hasAnotherEnabledAlarm = alarmRepository.getAlarms().any {
            it.id != alarm.id && it.isEnabled
        }
        if (hasAnotherEnabledAlarm) {
            showSwitchActiveAlarmDialog(alarm)
        } else {
            enableAlarmAndStartPlayback(alarm)
        }
    }

    private fun enableAlarmAndStartPlayback(alarm: AlarmSchedule) {
        if (alarm.soundId == UnselectedAlarmSound.SOUND_ID) {
            Toast.makeText(
                requireContext(),
                R.string.alarm_sound_required_to_enable,
                Toast.LENGTH_SHORT
            ).show()
            renderAlarms(alarmRepository.getAlarms())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val playbackSource = runCatching {
                soundRepository.getPlaybackSource(
                    soundId = alarm.soundId,
                    objectKey = alarm.soundObjectKey.ifBlank { null }
                )
            }.getOrElse {
                Log.e(TAG, "Failed to load playback source while enabling alarm.", it)
                Toast.makeText(requireContext(), R.string.alarm_setup_load_failed, Toast.LENGTH_SHORT).show()
                renderAlarms(alarmRepository.getAlarms())
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                startSleepSessionForAlarm.execute(alarm.id)
            }
            if (result == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.alarm_sound_required_to_enable,
                    Toast.LENGTH_SHORT
                ).show()
                renderAlarms(alarmRepository.getAlarms())
                return@launch
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
                renderAlarms(alarmRepository.getAlarms())
                return@launch
            }
            if (
                playbackSource.objectKey.isNotBlank() &&
                playbackSource.objectKey != alarm.soundObjectKey
            ) {
                withContext(Dispatchers.IO) {
                    alarmRepository.updateAlarmSoundObjectKey(alarm.id, playbackSource.objectKey)
                }
            }

            if (result.sleepSession != null) {
                SleepPlaybackController.start(
                    requireContext(),
                    SleepPlaybackRequest.from(
                        alarmSchedule = result.alarmSchedule,
                        sleepSession = result.sleepSession,
                        sourceUri = playbackSource.sourceUri
                    )
                )
            }
            Toast.makeText(
                requireContext(),
                if (result.sleepSession != null) {
                    getString(R.string.sleep_playback_started_notice)
                } else {
                    getString(R.string.sleep_start_scheduled_notice, result.alarmSchedule.categoryName)
                },
                Toast.LENGTH_SHORT
            ).show()
            renderAlarms(alarmRepository.getAlarms())
        }
    }

    private fun showSwitchActiveAlarmDialog(alarm: AlarmSchedule) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.switch_active_alarm_dialog_title)
            .setMessage(R.string.switch_active_alarm_dialog_message)
            .setPositiveButton(R.string.switch_active_alarm_confirm) { _, _ ->
                enableAlarmAndStartPlayback(alarm)
            }
            .setNegativeButton(R.string.switch_active_alarm_cancel) { _, _ ->
                renderAlarms(alarmRepository.getAlarms())
            }
            .setOnCancelListener {
                renderAlarms(alarmRepository.getAlarms())
            }
            .show()
    }

    private fun showNoticeIfPresent() {
        val message = arguments?.getString(ARG_NOTICE_MESSAGE) ?: return
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        arguments?.remove(ARG_NOTICE_MESSAGE)
    }

    private fun showDeleteAlarmDialog(alarm: AlarmSchedule) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_alarm_dialog_title)
            .setMessage(R.string.delete_alarm_dialog_message)
            .setPositiveButton(R.string.delete_alarm_confirm) { _, _ ->
                AlarmScheduler.cancel(requireContext(), alarm.id)
                val deleteResult = alarmRepository.deleteAlarm(alarm.id)
                if (alarm.isEnabled || deleteResult.cancelledActivePlayback) {
                    SleepPlaybackController.stop(requireContext())
                }
                renderAlarms(alarmRepository.getAlarms())
            }
            .setNegativeButton(R.string.delete_alarm_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_NOTICE_MESSAGE = "noticeMessage"
        private const val TAG = "AlarmHistoryFragment"
        private const val NOON_HOUR = 12
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTE_IN_MILLIS = 60_000L
        private const val ENABLED_ALARM_ALPHA = 1f
        private const val DISABLED_ALARM_ALPHA = 0.48f
    }
}
