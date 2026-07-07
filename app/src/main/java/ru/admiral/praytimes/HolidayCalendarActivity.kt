package ru.admiral.praytimes

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import ru.admiral.praytimes.holiday.HolidayCalendar
import ru.admiral.praytimes.holiday.HolidayOccurrence
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.holiday.IslamicHoliday
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class HolidayCalendarActivity : Activity() {
    private lateinit var subtitleText: TextView
    private lateinit var todayText: TextView
    private lateinit var yearText: TextView
    private lateinit var holidayList: LinearLayout

    private var selectedYear: Int = LocalDate.now().year

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_holiday_calendar)
        SystemInsets.applyTo(findViewById(R.id.holidayRoot))

        selectedYear = intent.getIntExtra(EXTRA_YEAR, LocalDate.now().year)
        bindViews()
        setupButtons()
        render()
    }

    private fun bindViews() {
        subtitleText = findViewById(R.id.subtitleText)
        todayText = findViewById(R.id.todayText)
        yearText = findViewById(R.id.yearText)
        holidayList = findViewById(R.id.holidayList)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.previousYearButton).setOnClickListener {
            selectedYear -= 1
            render()
        }
        findViewById<Button>(R.id.nextYearButton).setOnClickListener {
            selectedYear += 1
            render()
        }
    }

    private fun render() {
        val today = LocalDate.now()
        val hijriAdjustment = AppSettings.hijriDayAdjustment(this)
        val todayHolidays = HolidayCalendar.holidaysOn(today, hijriAdjustment)
        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val holidays = HolidayCalendar.holidaysForGregorianYear(selectedYear, hijriAdjustment)

        subtitleText.text = getString(
            R.string.holiday_calendar_subtitle,
            UiText.formatHijri(this, IslamicCalendar.hijriDate(today, hijriAdjustment)),
        )
        yearText.text = selectedYear.toString()
        todayText.text = if (todayHolidays.isEmpty()) {
            val next = HolidayCalendar.nextHolidayAfter(today, hijriAdjustment)
            if (next == null) {
                getString(R.string.no_holiday_today)
            } else {
                getString(
                    R.string.today_calendar_status,
                    getString(R.string.no_holiday_today),
                    getString(R.string.next_holiday_value, UiText.holidayTitle(this, next), formatter.format(next.date)),
                )
            }
        } else {
            getString(
                R.string.today_calendar_status,
                todayHolidays.joinToString { UiText.holidayTitle(this, it) },
                getString(R.string.hijri_date_text, UiText.formatHijri(this, IslamicCalendar.hijriDate(today, hijriAdjustment))),
            )
        }

        holidayList.removeAllViews()
        if (holidays.isEmpty()) {
            holidayList.addView(emptyRow())
            return
        }

        holidays.forEach { holiday ->
            holidayList.addView(holidayRow(holiday, formatter, today))
        }
    }

    private fun emptyRow(): TextView = TextView(this).apply {
        text = getString(R.string.no_holidays_year)
        setTextColor(getColor(R.color.color_muted))
        textSize = 15f
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun holidayRow(
        holiday: HolidayOccurrence,
        formatter: DateTimeFormatter,
        today: LocalDate,
    ): LinearLayout {
        val isToday = holiday.date == today
        val isMajorHoliday = holiday.holiday == IslamicHoliday.EID_AL_FITR ||
            holiday.holiday == IslamicHoliday.EID_AL_ADHA ||
            holiday.holiday == IslamicHoliday.LAYLAT_AL_QADR
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = getDrawable(
                when {
                    isToday -> R.drawable.month_today_background
                    isMajorHoliday -> R.drawable.holiday_rose_background
                    else -> R.drawable.row_background
                },
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(10)
            }
        }

        row.addView(ImageView(this).apply {
            setImageResource(if (isMajorHoliday) R.drawable.ic_star else R.drawable.ic_moon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
            }
            addView(TextView(context).apply {
                text = UiText.holidayTitle(this@HolidayCalendarActivity, holiday)
                setTextColor(getColor(R.color.color_text))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(
                    R.string.holiday_calendar_row_subtitle,
                    formatter.format(holiday.date),
                    UiText.formatHijri(this@HolidayCalendarActivity, holiday.hijriDate),
                )
                setTextColor(getColor(R.color.color_muted))
                textSize = 13f
            })
        })
        if (isToday) {
            row.addView(TextView(this).apply {
                text = getString(R.string.today_marker)
                setTextColor(getColor(R.color.color_primary_dark))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }

        return row
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_YEAR = "ru.admiral.praytimes.extra.YEAR"
    }
}
