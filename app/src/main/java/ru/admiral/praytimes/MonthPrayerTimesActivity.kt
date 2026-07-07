package ru.admiral.praytimes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.holiday.HolidayCalendar
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.io.File
import java.util.Locale

class MonthPrayerTimesActivity : Activity() {
    private lateinit var locationText: TextView
    private lateinit var methodText: TextView
    private lateinit var monthText: TextView
    private lateinit var prayerMonthList: LinearLayout

    private lateinit var coordinates: Coordinates
    private lateinit var zoneId: ZoneId
    private lateinit var method: CalculationMethod
    private lateinit var asrMethod: AsrMethod
    private lateinit var adjustments: PrayerAdjustments
    private lateinit var locationLabel: String
    private lateinit var selectedMonth: YearMonth
    private var hijriDayAdjustment: Int = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_month_prayer_times)
        SystemInsets.applyTo(findViewById(R.id.monthRoot))

        readIntent()
        bindViews()
        setupButtons()
        render()
    }

    private fun readIntent() {
        coordinates = Coordinates(
            intent.getDoubleExtra(EXTRA_LATITUDE, DEFAULT_LATITUDE),
            intent.getDoubleExtra(EXTRA_LONGITUDE, DEFAULT_LONGITUDE),
        )
        zoneId = runCatching {
            ZoneId.of(intent.getStringExtra(EXTRA_ZONE_ID).orEmpty())
        }.getOrDefault(ZoneId.systemDefault())
        method = enumValueOrDefault(
            intent.getStringExtra(EXTRA_METHOD),
            CalculationMethod.MUSLIM_WORLD_LEAGUE,
        )
        asrMethod = enumValueOrDefault(intent.getStringExtra(EXTRA_ASR_METHOD), AsrMethod.STANDARD)
        adjustments = PrayerAdjustments(
            fajr = intent.getIntExtra(EXTRA_ADJUSTMENT_FAJR, 0),
            sunrise = intent.getIntExtra(EXTRA_ADJUSTMENT_SUNRISE, 0),
            dhuhr = intent.getIntExtra(EXTRA_ADJUSTMENT_DHUHR, 0),
            asr = intent.getIntExtra(EXTRA_ADJUSTMENT_ASR, 0),
            maghrib = intent.getIntExtra(EXTRA_ADJUSTMENT_MAGHRIB, 0),
            isha = intent.getIntExtra(EXTRA_ADJUSTMENT_ISHA, 0),
        )
        hijriDayAdjustment = intent.getIntExtra(EXTRA_HIJRI_DAY_ADJUSTMENT, AppSettings.hijriDayAdjustment(this))
        locationLabel = intent.getStringExtra(EXTRA_LOCATION_LABEL).orEmpty()
            .ifBlank { getString(R.string.default_location_name) }
        selectedMonth = YearMonth.of(
            intent.getIntExtra(EXTRA_YEAR, LocalDate.now(zoneId).year),
            intent.getIntExtra(EXTRA_MONTH, LocalDate.now(zoneId).monthValue),
        )
    }

    private fun bindViews() {
        locationText = findViewById(R.id.locationText)
        methodText = findViewById(R.id.methodText)
        monthText = findViewById(R.id.monthText)
        prayerMonthList = findViewById(R.id.prayerMonthList)
    }

    private fun setupButtons() {
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.previousMonthButton).setOnClickListener {
            selectedMonth = selectedMonth.minusMonths(1)
            render()
        }
        findViewById<Button>(R.id.nextMonthButton).setOnClickListener {
            selectedMonth = selectedMonth.plusMonths(1)
            render()
        }
        findViewById<Button>(R.id.exportPdfButton).setOnClickListener { exportPdf() }
        findViewById<Button>(R.id.exportIcsButton).setOnClickListener { exportIcs() }
    }

    private fun render() {
        val locale = Locale.getDefault()
        val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale)
        val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)

        locationText.text = locationLabel
        methodText.text = getString(
            R.string.prayer_month_method,
            UiText.methodLabel(this, method),
            UiText.asrMethodLabel(this, asrMethod),
        )
        monthText.text = monthFormatter.format(selectedMonth.atDay(1))
        prayerMonthList.removeAllViews()

        val today = LocalDate.now(zoneId)
        for (day in 1..selectedMonth.lengthOfMonth()) {
            val date = selectedMonth.atDay(day)
            prayerMonthList.addView(monthRow(date, dayFormatter, today))
        }
    }

    private fun monthRow(
        date: LocalDate,
        dayFormatter: DateTimeFormatter,
        today: LocalDate,
    ): LinearLayout {
        val hijriDate = IslamicCalendar.hijriDate(date, hijriDayAdjustment)
        val holidays = HolidayCalendar.holidaysOn(date, hijriDayAdjustment)
        val isToday = date == today
        val prayerTimes = calculatePrayerTimes(date)

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(
                when {
                    isToday -> R.drawable.month_today_background
                    holidays.isNotEmpty() -> R.drawable.holiday_accent_background
                    else -> R.drawable.row_background
                },
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(10)
            }
            addView(dayHeader(date, dayFormatter, hijriDateText = UiText.formatHijri(this@MonthPrayerTimesActivity, hijriDate), isToday, holidays))
            if (prayerTimes == null) {
                addView(unavailableText())
            } else {
                addView(timesRow(prayerTimes))
            }
        }
    }

    private fun dayHeader(
        date: LocalDate,
        formatter: DateTimeFormatter,
        hijriDateText: String,
        isToday: Boolean,
        holidays: List<ru.admiral.praytimes.holiday.HolidayOccurrence>,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = formatter.format(date)
                setTextColor(getColor(R.color.color_text))
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = if (holidays.isEmpty()) {
                    hijriDateText
                } else {
                    getString(
                        R.string.month_holiday_line,
                        hijriDateText,
                        holidays.joinToString { UiText.holidayTitle(this@MonthPrayerTimesActivity, it) },
                    )
                }
                setTextColor(getColor(if (holidays.isEmpty()) R.color.color_muted else R.color.color_accent))
                textSize = 12f
            })
        })
        if (isToday) {
            addView(TextView(context).apply {
                text = getString(R.string.today_marker)
                setTextColor(getColor(R.color.color_primary_dark))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
    }

    private fun unavailableText(): TextView = TextView(this).apply {
        text = getString(R.string.prayer_month_unavailable)
        setTextColor(getColor(R.color.color_muted))
        textSize = 13f
        setPadding(0, dp(8), 0, 0)
    }

    private fun timesRow(prayerTimes: PrayerTimes): LinearLayout {
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setPadding(0, dp(8), 0, 0)
            addTimeCell(this, getString(R.string.prayer_fajr_short), formatter.format(prayerTimes.fajr))
            addTimeCell(this, getString(R.string.prayer_sunrise_short), formatter.format(prayerTimes.sunrise))
            addTimeCell(this, getString(R.string.prayer_dhuhr_short), formatter.format(prayerTimes.dhuhr))
            addTimeCell(this, getString(R.string.prayer_asr_short), formatter.format(prayerTimes.asr))
            addTimeCell(this, getString(R.string.prayer_maghrib_short), formatter.format(prayerTimes.maghrib), accent = true)
            addTimeCell(this, getString(R.string.prayer_isha_short), formatter.format(prayerTimes.isha))
        }
    }

    private fun addTimeCell(parent: LinearLayout, label: String, time: String, accent: Boolean = false) {
        parent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = label
                setTextColor(getColor(R.color.color_muted))
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
            })
            addView(TextView(context).apply {
                text = time
                setTextColor(getColor(if (accent) R.color.color_accent else R.color.color_primary_dark))
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                includeFontPadding = false
            })
        })
    }

    private fun exportPdf() {
        runCatching {
            val file = exportFile("pdf")
            val document = PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f
            }
            var pageNumber = 1
            var y = PDF_MARGIN
            var page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())

            fun line(text: String, bold: Boolean = false) {
                if (y > PDF_HEIGHT - PDF_MARGIN) {
                    document.finishPage(page)
                    pageNumber += 1
                    page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, pageNumber).create())
                    y = PDF_MARGIN
                }
                paint.isFakeBoldText = bold
                page.canvas.drawText(text, PDF_MARGIN.toFloat(), y.toFloat(), paint)
                y += if (bold) 20 else 16
            }

            val titleFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
            val dayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            line(getString(R.string.prayer_month_title) + " - " + titleFormatter.format(selectedMonth.atDay(1)), bold = true)
            line(locationLabel)
            line(getString(R.string.prayer_month_method, UiText.methodLabel(this, method), UiText.asrMethodLabel(this, asrMethod)))
            line("")
            (1..selectedMonth.lengthOfMonth()).forEach { day ->
                val date = selectedMonth.atDay(day)
                val times = calculatePrayerTimes(date)
                if (times == null) {
                    line("${dayFormatter.format(date)}: ${getString(R.string.prayer_month_unavailable)}")
                } else {
                    line(
                        "${dayFormatter.format(date)}  " +
                            "${getString(R.string.prayer_fajr_short)} ${timeFormatter.format(times.fajr)}  " +
                            "${getString(R.string.prayer_dhuhr_short)} ${timeFormatter.format(times.dhuhr)}  " +
                            "${getString(R.string.prayer_asr_short)} ${timeFormatter.format(times.asr)}  " +
                            "${getString(R.string.prayer_maghrib_short)} ${timeFormatter.format(times.maghrib)}  " +
                            "${getString(R.string.prayer_isha_short)} ${timeFormatter.format(times.isha)}",
                    )
                }
            }
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
            document.close()
            showExportSaved(file)
        }.onFailure {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportIcs() {
        runCatching {
            val file = exportFile("ics")
            val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            val events = buildString {
                appendLine("BEGIN:VCALENDAR")
                appendLine("VERSION:2.0")
                appendLine("PRODID:-//Admiral//PrayTimes//RU")
                (1..selectedMonth.lengthOfMonth()).forEach { day ->
                    val date = selectedMonth.atDay(day)
                    val times = calculatePrayerTimes(date) ?: return@forEach
                    listOf(
                        getString(R.string.prayer_fajr) to times.fajr,
                        getString(R.string.prayer_dhuhr) to times.dhuhr,
                        getString(R.string.prayer_asr) to times.asr,
                        getString(R.string.prayer_maghrib) to times.maghrib,
                        getString(R.string.prayer_isha) to times.isha,
                    ).forEach { (label, start) ->
                        val startUtc = start.withZoneSameInstant(ZoneOffset.UTC).format(stampFormatter)
                        val endUtc = start.plusMinutes(20).withZoneSameInstant(ZoneOffset.UTC).format(stampFormatter)
                        appendLine("BEGIN:VEVENT")
                        appendLine("UID:${date}-$label@ru.admiral.praytimes")
                        appendLine("DTSTAMP:${java.time.ZonedDateTime.now(ZoneOffset.UTC).format(stampFormatter)}")
                        appendLine("DTSTART:$startUtc")
                        appendLine("DTEND:$endUtc")
                        appendLine("SUMMARY:${escapeIcs(label)}")
                        appendLine("LOCATION:${escapeIcs(locationLabel)}")
                        appendLine("END:VEVENT")
                    }
                }
                appendLine("END:VCALENDAR")
            }
            file.writeText(events)
            showExportSaved(file)
        }.onFailure {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculatePrayerTimes(date: LocalDate): PrayerTimes? =
        PrayerTimes.calculate(
            coordinates = coordinates,
            date = date,
            zoneId = zoneId,
            parameters = method.parameters().withAsrMethod(asrMethod).copy(adjustments = adjustments),
        )

    private fun exportFile(extension: String): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val safeLocation = locationLabel.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifBlank { "location" }
        return File(directory, "pray-times-${safeLocation}-${selectedMonth}.$extension")
    }

    private fun showExportSaved(file: File) {
        Toast.makeText(this, getString(R.string.export_saved, file.absolutePath), Toast.LENGTH_LONG).show()
    }

    private fun escapeIcs(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val DEFAULT_LATITUDE = 36.8121
        private const val DEFAULT_LONGITUDE = 34.6415
        const val EXTRA_LATITUDE = "ru.admiral.praytimes.extra.LATITUDE"
        const val EXTRA_LONGITUDE = "ru.admiral.praytimes.extra.LONGITUDE"
        const val EXTRA_ZONE_ID = "ru.admiral.praytimes.extra.ZONE_ID"
        const val EXTRA_METHOD = "ru.admiral.praytimes.extra.METHOD"
        const val EXTRA_ASR_METHOD = "ru.admiral.praytimes.extra.ASR_METHOD"
        const val EXTRA_ADJUSTMENT_FAJR = "ru.admiral.praytimes.extra.ADJUSTMENT_FAJR"
        const val EXTRA_ADJUSTMENT_SUNRISE = "ru.admiral.praytimes.extra.ADJUSTMENT_SUNRISE"
        const val EXTRA_ADJUSTMENT_DHUHR = "ru.admiral.praytimes.extra.ADJUSTMENT_DHUHR"
        const val EXTRA_ADJUSTMENT_ASR = "ru.admiral.praytimes.extra.ADJUSTMENT_ASR"
        const val EXTRA_ADJUSTMENT_MAGHRIB = "ru.admiral.praytimes.extra.ADJUSTMENT_MAGHRIB"
        const val EXTRA_ADJUSTMENT_ISHA = "ru.admiral.praytimes.extra.ADJUSTMENT_ISHA"
        const val EXTRA_HIJRI_DAY_ADJUSTMENT = "ru.admiral.praytimes.extra.HIJRI_DAY_ADJUSTMENT"
        const val EXTRA_LOCATION_LABEL = "ru.admiral.praytimes.extra.LOCATION_LABEL"
        const val EXTRA_YEAR = "ru.admiral.praytimes.extra.YEAR"
        const val EXTRA_MONTH = "ru.admiral.praytimes.extra.MONTH"
        private const val PDF_WIDTH = 595
        private const val PDF_HEIGHT = 842
        private const val PDF_MARGIN = 36

        fun intent(
            context: Context,
            coordinates: Coordinates,
            zoneId: ZoneId,
            method: CalculationMethod,
            asrMethod: AsrMethod,
            adjustments: PrayerAdjustments,
            hijriDayAdjustment: Int,
            locationLabel: String,
            date: LocalDate,
        ): Intent = Intent(context, MonthPrayerTimesActivity::class.java).apply {
            putExtra(EXTRA_LATITUDE, coordinates.latitude)
            putExtra(EXTRA_LONGITUDE, coordinates.longitude)
            putExtra(EXTRA_ZONE_ID, zoneId.id)
            putExtra(EXTRA_METHOD, method.name)
            putExtra(EXTRA_ASR_METHOD, asrMethod.name)
            putExtra(EXTRA_ADJUSTMENT_FAJR, adjustments.fajr)
            putExtra(EXTRA_ADJUSTMENT_SUNRISE, adjustments.sunrise)
            putExtra(EXTRA_ADJUSTMENT_DHUHR, adjustments.dhuhr)
            putExtra(EXTRA_ADJUSTMENT_ASR, adjustments.asr)
            putExtra(EXTRA_ADJUSTMENT_MAGHRIB, adjustments.maghrib)
            putExtra(EXTRA_ADJUSTMENT_ISHA, adjustments.isha)
            putExtra(EXTRA_HIJRI_DAY_ADJUSTMENT, hijriDayAdjustment)
            putExtra(EXTRA_LOCATION_LABEL, locationLabel)
            putExtra(EXTRA_YEAR, date.year)
            putExtra(EXTRA_MONTH, date.monthValue)
        }
    }
}
