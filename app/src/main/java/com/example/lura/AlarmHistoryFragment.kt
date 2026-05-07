package com.example.lura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.lura.data.AlarmRepository
import com.example.lura.data.AlarmRepositoryProvider
import com.example.lura.data.AlarmSchedule
import com.example.lura.databinding.FragmentAlarmHistoryBinding
import com.google.android.material.switchmaterial.SwitchMaterial

class AlarmHistoryFragment : Fragment() {

    private var _binding: FragmentAlarmHistoryBinding? = null
    private val binding get() = _binding!!
    private val alarmRepository: AlarmRepository by lazy {
        AlarmRepositoryProvider.get(requireContext().applicationContext)
    }

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
        enabledSwitch.setOnCheckedChangeListener(createAlarmToggleListener(alarm.id))
    }

    private fun createAlarmToggleListener(alarmId: String): CompoundButton.OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            alarmRepository.setAlarmEnabled(alarmId, isChecked)
            renderAlarms(alarmRepository.getAlarms())
        }

    private fun getStatusText(isEnabled: Boolean): String =
        getString(if (isEnabled) R.string.alarm_status_on else R.string.alarm_status_off)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
