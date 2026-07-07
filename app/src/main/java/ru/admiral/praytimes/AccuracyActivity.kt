package ru.admiral.praytimes

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.adhan.Qibla
import ru.admiral.praytimes.data.ActiveLocationResolver
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AccuracyActivity : Activity() {
    private lateinit var accuracyList: LinearLayout
    private lateinit var database: LocationDatabase

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accuracy)
        SystemInsets.applyTo(findViewById(R.id.accuracyRoot))
        database = LocationDatabase(this)
        accuracyList = findViewById(R.id.accuracyList)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val location = ActiveLocationResolver.resolve(this, database)
        val method = ActiveLocationResolver.calculationMethod(this, location)
        val asrMethod = AppSettings.asrMethod(this)
        val adjustments = ActiveLocationResolver.adjustments(this, database, location)
        val date = LocalDate.now(location.zoneId)
        val rawParameters = method.parameters().withAsrMethod(asrMethod)
        val adjustedParameters = rawParameters.copy(adjustments = adjustments)
        val rawTimes = PrayerTimes.calculate(location.coordinates, date, location.zoneId, rawParameters)
        val adjustedTimes = PrayerTimes.calculate(location.coordinates, date, location.zoneId, adjustedParameters)
        val hijri = IslamicCalendar.hijriDate(date, AppSettings.hijriDayAdjustment(this))

        accuracyList.removeAllViews()
        addSection(getString(R.string.selected_location))
        addParameter(getString(R.string.location_panel_title), location.name)
        addParameter(
            getString(R.string.coordinates),
            String.format(Locale.US, "%.5f, %.5f", location.coordinates.latitude, location.coordinates.longitude),
        )
        addParameter(getString(R.string.time_zone), location.zoneId.id)
        addParameter(getString(R.string.calculation_method), UiText.methodLabel(this, method))
        addParameter(getString(R.string.asr_calculation_method), UiText.asrMethodLabel(this, asrMethod))
        addParameter(getString(R.string.qibla), getString(R.string.qibla_degrees, Qibla(location.coordinates).direction.toFloat()))
        addParameter(getString(R.string.hijri_day_adjustment), AppSettings.hijriDayAdjustment(this).toString())
        addParameter(getString(R.string.hijri_date_text, ""), UiText.formatHijri(this, hijri))
        addParameter(getString(R.string.prayer_adjustments_title), adjustmentsText(adjustments))
        addTimes(getString(R.string.accuracy_raw_times), rawTimes)
        addTimes(getString(R.string.accuracy_adjusted_times), adjustedTimes)
    }

    private fun addSection(title: String) {
        accuracyList.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.color_text))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(8))
        })
    }

    private fun addParameter(label: String, value: String) {
        accuracyList.addView(TextView(this).apply {
            text = getString(R.string.accuracy_parameter_line, label.trimEnd(':'), value)
            setTextColor(getColor(R.color.color_muted))
            textSize = 14f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = getDrawable(R.drawable.row_background)
        })
    }

    private fun addTimes(title: String, times: PrayerTimes?) {
        addSection(title)
        if (times == null) {
            addParameter(title, getString(R.string.prayer_month_unavailable))
            return
        }
        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        accuracyList.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.row_background)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            addCell(getString(R.string.prayer_fajr_short), formatter.format(times.fajr))
            addCell(getString(R.string.prayer_sunrise_short), formatter.format(times.sunrise))
            addCell(getString(R.string.prayer_dhuhr_short), formatter.format(times.dhuhr))
            addCell(getString(R.string.prayer_asr_short), formatter.format(times.asr))
            addCell(getString(R.string.prayer_maghrib_short), formatter.format(times.maghrib))
            addCell(getString(R.string.prayer_isha_short), formatter.format(times.isha))
        })
    }

    private fun LinearLayout.addCell(label: String, value: String) {
        addView(TextView(context).apply {
            text = "$label\n$value"
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.color_primary_dark))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun adjustmentsText(adjustments: PrayerAdjustments): String =
        listOf(
            getString(R.string.prayer_fajr_short) to adjustments.fajr,
            getString(R.string.prayer_sunrise_short) to adjustments.sunrise,
            getString(R.string.prayer_dhuhr_short) to adjustments.dhuhr,
            getString(R.string.prayer_asr_short) to adjustments.asr,
            getString(R.string.prayer_maghrib_short) to adjustments.maghrib,
            getString(R.string.prayer_isha_short) to adjustments.isha,
        ).joinToString { (label, value) -> "$label $value" }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
