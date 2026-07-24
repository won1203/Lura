package com.won1203.lura

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.won1203.lura.data.local.LuraDatabase
import com.won1203.lura.data.local.SleepSessionEntity
import com.won1203.lura.databinding.FragmentReportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReportFragment : Fragment(R.layout.fragment_report) {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentReportBinding.bind(view)
        binding.monthSummaryCard.setOnClickListener {
            showSleepCalendar()
        }
        binding.monthCalendarIcon.setOnClickListener {
            showSleepCalendar()
        }
        loadReport()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadReport() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                val database = LuraDatabase.getInstance(appContext)
                val now = Calendar.getInstance()
                val recentStart = startOfDay(now).apply {
                    add(Calendar.DAY_OF_YEAR, -RECENT_DAY_COUNT + 1)
                }
                val recentEnd = startOfDay(now).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                val monthStart = startOfMonth(now)
                val monthEnd = startOfMonth(now).apply {
                    add(Calendar.MONTH, 1)
                }

                val recentSessions = database.sleepSessionDao()
                    .getCompletedSessionsInTargetRange(
                        recentStart.timeInMillis,
                        recentEnd.timeInMillis
                    )
                val monthSessions = database.sleepSessionDao()
                    .getCompletedSessionsInTargetRange(
                        monthStart.timeInMillis,
                        monthEnd.timeInMillis
                    )

                SleepReport(
                    recentSummary = summarizeDailySleep(recentSessions),
                    monthSummary = summarizeDailySleep(monthSessions)
                )
            }
            renderReport(report)
        }
    }

    private fun renderReport(report: SleepReport) {
        renderRecentSummary(report.recentSummary)
        renderMonthSummary(report.monthSummary)
    }

    private fun renderRecentSummary(summary: SleepSummary) {
        if (summary.recordedDays == 0) {
            binding.recentAverageDuration.text = EMPTY_DURATION_TEXT
            binding.recentAverageBasis.text = getString(R.string.report_no_sleep_records)
            return
        }

        binding.recentAverageDuration.text = formatDuration(summary.averageDurationMillis)
        binding.recentAverageBasis.text =
            getString(R.string.report_record_days_basis, summary.recordedDays)
    }

    private fun renderMonthSummary(summary: SleepSummary) {
        binding.monthAverageDuration.text =
            if (summary.recordedDays == 0) {
                EMPTY_DURATION_TEXT
            } else {
                formatDuration(summary.averageDurationMillis)
            }
        binding.monthRecordedDays.text = getString(R.string.report_days_count, summary.recordedDays)
        binding.monthTotalDuration.text =
            if (summary.totalDurationMillis == 0L) {
                EMPTY_DURATION_TEXT
            } else {
                formatDurationCompact(summary.totalDurationMillis)
            }
    }

    private fun showSleepCalendar() {
        SleepCalendarBottomSheetFragment()
            .show(parentFragmentManager, SleepCalendarBottomSheetFragment.TAG)
    }

    private fun summarizeDailySleep(sessions: List<SleepSessionEntity>): SleepSummary {
        val dailyDurations = sessions
            .groupBy { targetDayKey(it.targetAlarmAtEpochMillis) }
            .mapValues { (_, daySessions) ->
                daySessions.sumOf { session ->
                    SleepReportTime.displayedMinuteDurationMillis(
                        startedAtEpochMillis = session.startedAtEpochMillis,
                        targetAlarmAtEpochMillis = session.targetAlarmAtEpochMillis
                    )
                }
            }
            .values
            .filter { it > 0L }

        val totalDurationMillis = dailyDurations.sum()
        val recordedDays = dailyDurations.size
        return SleepSummary(
            recordedDays = recordedDays,
            totalDurationMillis = totalDurationMillis,
            averageDurationMillis = if (recordedDays == 0) 0L else totalDurationMillis / recordedDays
        )
    }

    private fun targetDayKey(epochMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = epochMillis
        }
        return "${calendar.get(Calendar.YEAR)}-" +
            "${calendar.get(Calendar.MONTH)}-" +
            calendar.get(Calendar.DAY_OF_MONTH)
    }

    private fun startOfDay(calendar: Calendar): Calendar =
        (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun startOfMonth(calendar: Calendar): Calendar =
        startOfDay(calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return getString(R.string.report_duration_hours_minutes, hours, minutes)
    }

    private fun formatDurationCompact(durationMillis: Long): String {
        val totalMinutes = durationMillis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return if (minutes == 0L) {
            getString(R.string.report_duration_hours, hours)
        } else {
            getString(R.string.report_duration_hours_minutes, hours, minutes)
        }
    }

    private data class SleepReport(
        val recentSummary: SleepSummary,
        val monthSummary: SleepSummary
    )

    private data class SleepSummary(
        val recordedDays: Int,
        val totalDurationMillis: Long,
        val averageDurationMillis: Long
    )

    private companion object {
        const val RECENT_DAY_COUNT = 7
        const val MILLIS_PER_MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60L
        const val EMPTY_DURATION_TEXT = "-"
    }
}
