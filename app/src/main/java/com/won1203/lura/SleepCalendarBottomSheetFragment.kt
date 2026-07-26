package com.won1203.lura

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.won1203.lura.data.DefaultSoundCatalog
import com.won1203.lura.data.LocalSoundCatalog
import com.won1203.lura.data.local.AlarmEntity
import com.won1203.lura.data.local.LuraDatabase
import com.won1203.lura.data.local.SleepSessionEntity
import com.won1203.lura.databinding.BottomSheetSleepCalendarBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SleepCalendarBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSleepCalendarBinding? = null
    private val binding get() = _binding!!
    private var visibleMonthStart: Calendar = startOfMonth(Calendar.getInstance())
    private var selectedDayOfMonth: Int = visibleMonthStart.get(Calendar.DAY_OF_MONTH)
    private var dayReports: Map<Int, DaySleepReport> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetSleepCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeButton.setOnClickListener {
            dismiss()
        }
        binding.previousMonthButton.setOnClickListener {
            moveMonth(-1)
        }
        binding.nextMonthButton.setOnClickListener {
            moveMonth(1)
        }
        selectedDayOfMonth = currentDayInMonthOrFirst()
        loadCalendar()
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * BOTTOM_SHEET_HEIGHT_RATIO).toInt()
        }
        bottomSheet.requestLayout()
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = bottomSheet.layoutParams.height
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadCalendar() {
        val appContext = requireContext().applicationContext
        renderMonthTitle()
        viewLifecycleOwner.lifecycleScope.launch {
            dayReports = withContext(Dispatchers.IO) {
                val database = LuraDatabase.getInstance(appContext)
                val monthEnd = (visibleMonthStart.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1)
                }
                val sessions = database.sleepSessionDao()
                    .getCompletedSessionsInTargetRange(
                        visibleMonthStart.timeInMillis,
                        monthEnd.timeInMillis
                    )
                val alarmsById = database.alarmDao().getAlarms().associateBy { it.id }
                buildDayReports(sessions, alarmsById)
            }
            if (!dayReports.containsKey(selectedDayOfMonth)) {
                selectedDayOfMonth = dayReports.keys.minOrNull() ?: selectedDayOfMonth
            }
            renderCalendar()
            renderSelectedDayDetail()
        }
    }

    private fun renderCalendar() {
        binding.calendarGrid.removeAllViews()

        val firstDay = visibleMonthStart.get(Calendar.DAY_OF_WEEK)
        val leadingBlankDays = firstDay - Calendar.SUNDAY
        val daysInMonth = visibleMonthStart.getActualMaximum(Calendar.DAY_OF_MONTH)
        val totalCalendarCells = calendarCellCount(leadingBlankDays + daysInMonth)

        repeat(leadingBlankDays) {
            binding.calendarGrid.addView(createBlankCell())
        }

        for (day in 1..daysInMonth) {
            binding.calendarGrid.addView(createDayCell(day, dayReports[day]))
        }

        repeat(totalCalendarCells - leadingBlankDays - daysInMonth) {
            binding.calendarGrid.addView(createBlankCell())
        }
    }

    private fun createBlankCell(): View =
        Space(requireContext()).apply {
            layoutParams = calendarCellLayoutParams()
        }

    private fun createDayCell(dayOfMonth: Int, report: DaySleepReport?): View {
        val context = requireContext()
        val primaryColor = ContextCompat.getColor(context, R.color.lura_text_primary)
        val secondaryColor = ContextCompat.getColor(context, R.color.lura_text_secondary)
        val accentColor = ContextCompat.getColor(context, R.color.lura_accent)
        val isSelected = dayOfMonth == selectedDayOfMonth

        return LinearLayout(context).apply {
            layoutParams = calendarCellLayoutParams()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(2.dpToPx(), 6.dpToPx(), 2.dpToPx(), 6.dpToPx())
            setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_report_calendar_day_selected
                } else {
                    R.drawable.bg_report_calendar_day_empty
                }
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                selectedDayOfMonth = dayOfMonth
                renderCalendar()
                renderSelectedDayDetail()
            }

            addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = dayOfMonth.toString()
                    setTextColor(if (report == null && !isSelected) secondaryColor else primaryColor)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
            )

            addView(
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 6.dpToPx()
                    }
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    text = report?.let { formatCalendarCellDuration(it.totalDurationMillis) }
                        ?: EMPTY_DURATION_TEXT
                    setTextColor(if (report == null) secondaryColor else accentColor)
                    textSize = 12f
                }
            )
        }
    }

    private fun calendarCellLayoutParams(): GridLayout.LayoutParams =
        GridLayout.LayoutParams().apply {
            width = 0
            height = 64.dpToPx()
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
        }

    private fun renderSelectedDayDetail() {
        val report = dayReports[selectedDayOfMonth]
        binding.selectedDate.text = formatSelectedDate(selectedDayOfMonth)
        binding.selectedDayEmptyMessage.isVisible = report == null
        binding.selectedDayDetailRows.isVisible = report != null

        if (report == null) return

        binding.detailSleepDuration.text = formatDuration(report.totalDurationMillis)
        binding.detailSleepStart.text = formatTime(report.sleepStartEpochMillis)
        binding.detailWakeAlarm.text = formatTime(report.wakeAlarmEpochMillis)
        binding.detailCategory.text = report.categoryNames.joinToString(", ")
    }

    private fun buildDayReports(
        sessions: List<SleepSessionEntity>,
        alarmsById: Map<String, AlarmEntity>
    ): Map<Int, DaySleepReport> =
        sessions
            .groupBy { dayOfMonth(it.targetAlarmAtEpochMillis) }
            .mapValues { (dayOfMonth, daySessions) ->
                val validSessions = daySessions.filter {
                    SleepReportTime.displayedMinuteDurationMillis(
                        startedAtEpochMillis = it.startedAtEpochMillis,
                        targetAlarmAtEpochMillis = it.targetAlarmAtEpochMillis
                    ) > 0L
                }
                val categories = validSessions
                    .map { session ->
                        session.categoryName.takeIf(String::isNotBlank)
                            ?: alarmsById[session.alarmId]?.categoryName
                                ?.takeIf(String::isNotBlank)
                            ?: categoryNameForSound(session.sleepSoundId)
                            ?: getString(R.string.report_unknown_category)
                    }
                    .distinct()

                DaySleepReport(
                    dayOfMonth = dayOfMonth,
                    totalDurationMillis = validSessions.sumOf {
                        SleepReportTime.displayedMinuteDurationMillis(
                            startedAtEpochMillis = it.startedAtEpochMillis,
                            targetAlarmAtEpochMillis = it.targetAlarmAtEpochMillis
                        )
                    },
                    sleepStartEpochMillis = validSessions.minOfOrNull {
                        it.startedAtEpochMillis
                    } ?: 0L,
                    wakeAlarmEpochMillis = validSessions.maxOfOrNull {
                        it.targetAlarmAtEpochMillis
                    } ?: 0L,
                    categoryNames = categories.ifEmpty {
                        listOf(getString(R.string.report_unknown_category))
                    }
                )
            }
            .filterValues { it.totalDurationMillis > 0L }

    private fun categoryNameForSound(soundId: String): String? {
        val categoryId = LocalSoundCatalog.findCategoryId(soundId) ?: return null
        return DefaultSoundCatalog.categories
            .firstOrNull { category -> category.id == categoryId }
            ?.name
    }

    private fun currentDayInMonthOrFirst(): Int {
        val now = Calendar.getInstance()
        return if (
            now.get(Calendar.YEAR) == visibleMonthStart.get(Calendar.YEAR) &&
            now.get(Calendar.MONTH) == visibleMonthStart.get(Calendar.MONTH)
        ) {
            now.get(Calendar.DAY_OF_MONTH)
        } else {
            1
        }
    }

    private fun moveMonth(monthOffset: Int) {
        visibleMonthStart = (visibleMonthStart.clone() as Calendar).apply {
            add(Calendar.MONTH, monthOffset)
        }
        selectedDayOfMonth = currentDayInMonthOrFirst()
        loadCalendar()
    }

    private fun renderMonthTitle() {
        binding.calendarMonthTitle.text = getString(
            R.string.report_calendar_month_title,
            visibleMonthStart.get(Calendar.YEAR),
            visibleMonthStart.get(Calendar.MONTH) + 1
        )
    }

    private fun dayOfMonth(epochMillis: Long): Int =
        Calendar.getInstance().apply {
            timeInMillis = epochMillis
        }.get(Calendar.DAY_OF_MONTH)

    private fun calendarCellCount(usedCells: Int): Int =
        if (usedCells <= FIVE_WEEK_CELL_COUNT) {
            FIVE_WEEK_CELL_COUNT
        } else {
            SIX_WEEK_CELL_COUNT
        }

    private fun formatSelectedDate(dayOfMonth: Int): String {
        val calendar = (visibleMonthStart.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
        }
        val weekday = SimpleDateFormat("EEEE", Locale.KOREAN).format(calendar.time)
        return getString(
            R.string.report_selected_date_format,
            calendar.get(Calendar.MONTH) + 1,
            dayOfMonth,
            weekday
        )
    }

    private fun formatTime(epochMillis: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = epochMillis
        }
        return "%02d:%02d".format(
            Locale.KOREA,
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
    }

    private fun formatDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return getString(R.string.report_duration_hours_minutes, hours, minutes)
    }

    private fun formatCalendarCellDuration(durationMillis: Long): String {
        val totalMinutes = durationMillis / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        if (hours == 0L) {
            return "${minutes}m"
        }
        return if (minutes == 0L) {
            "${hours}h"
        } else {
            "${hours}h ${minutes}m"
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    private data class DaySleepReport(
        val dayOfMonth: Int,
        val totalDurationMillis: Long,
        val sleepStartEpochMillis: Long,
        val wakeAlarmEpochMillis: Long,
        val categoryNames: List<String>
    )

    companion object {
        const val TAG = "SleepCalendarBottomSheet"
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MINUTES_PER_HOUR = 60L
        private const val FIVE_WEEK_CELL_COUNT = 35
        private const val SIX_WEEK_CELL_COUNT = 42
        private const val EMPTY_DURATION_TEXT = "-"
        private const val BOTTOM_SHEET_HEIGHT_RATIO = 0.82f

        private fun startOfMonth(calendar: Calendar): Calendar =
            (calendar.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
    }
}
