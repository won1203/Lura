package com.example.lura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lura.data.AlarmRepository
import com.example.lura.data.AlarmRepositoryProvider
import com.example.lura.data.AlarmSchedule
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
        renderAlarms(alarmRepository.getAlarms())
        showNoticeIfPresent()
    }

    private fun renderAlarms(alarms: List<AlarmSchedule>) {
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

    private fun bindAlarmItem(itemView: View, alarm: AlarmSchedule) {
        itemView.findViewById<TextView>(R.id.alarm_time).text =
            getString(R.string.alarm_time_format, alarm.hour, alarm.minute)
        itemView.findViewById<TextView>(R.id.alarm_category).text = alarm.categoryName
        itemView.findViewById<TextView>(R.id.alarm_sound_title).text = alarm.soundTitle
        itemView.findViewById<TextView>(R.id.alarm_repeat_days).text =
            getString(
                R.string.alarm_repeat_days_format,
                AlarmWeekdayFormatter.summary(requireContext(), alarm.weekdays)
            )
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

    private fun createAlarmToggleListener(alarm: AlarmSchedule): CompoundButton.OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestAlarmActivation(alarm)
            } else {
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

            withContext(Dispatchers.IO) {
                alarmRepository.updateAlarmSound(
                    alarmId = alarmId,
                    category = category,
                    sound = recommendedSound
                )
                alarmRepository.getAlarms()
            }.also(::renderAlarms)
        }
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
            val sourceUri = runCatching {
                soundRepository.getPlaybackSourceUri(alarm.soundId)
            }.getOrElse {
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

            SleepPlaybackController.start(
                requireContext(),
                SleepPlaybackRequest.from(
                    alarmSchedule = result.alarmSchedule,
                    sleepSession = result.sleepSession,
                    sourceUri = sourceUri
                )
            )
            Toast.makeText(
                requireContext(),
                R.string.sleep_playback_started_notice,
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
                alarmRepository.deleteAlarm(alarm.id)
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
    }
}
