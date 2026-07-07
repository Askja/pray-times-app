package ru.admiral.praytimes

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.data.ActiveLocationResolver
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.SystemInsets
import ru.admiral.praytimes.ui.UiText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class MethodComparisonActivity : Activity() {
    private lateinit var subtitleText: TextView
    private lateinit var comparisonList: LinearLayout
    private lateinit var database: LocationDatabase

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppSettings.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_method_comparison)
        SystemInsets.applyTo(findViewById(R.id.methodComparisonRoot))
        database = LocationDatabase(this)
        subtitleText = findViewById(R.id.subtitleText)
        comparisonList = findViewById(R.id.methodComparisonList)
        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        render()
    }

    private fun render() {
        val location = ActiveLocationResolver.resolve(this, database)
        val adjustments = ActiveLocationResolver.adjustments(this, database, location)
        val asrMethod = AppSettings.asrMethod(this)
        val date = LocalDate.now(location.zoneId)
        val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

        subtitleText.text = "${location.name} - ${dateFormatter.format(date)}"
        comparisonList.removeAllViews()
        CalculationMethod.entries
            .filter { it != CalculationMethod.OTHER }
            .forEach { method ->
                val times = PrayerTimes.calculate(
                    coordinates = location.coordinates,
                    date = date,
                    zoneId = location.zoneId,
                    parameters = method.parameters().withAsrMethod(asrMethod).copy(adjustments = adjustments),
                )
                comparisonList.addView(methodRow(method, asrMethod, times, timeFormatter))
            }
    }

    private fun methodRow(
        method: CalculationMethod,
        asrMethod: AsrMethod,
        times: PrayerTimes?,
        formatter: DateTimeFormatter,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.row_background)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(10)
            }

            addView(TextView(context).apply {
                text = UiText.methodLabel(this@MethodComparisonActivity, method)
                setTextColor(getColor(R.color.color_text))
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = UiText.asrMethodLabel(this@MethodComparisonActivity, asrMethod)
                setTextColor(getColor(R.color.color_muted))
                textSize = 12f
                setPadding(0, dp(2), 0, dp(8))
            })
            if (times == null) {
                addView(TextView(context).apply {
                    text = getString(R.string.prayer_month_unavailable)
                    setTextColor(getColor(R.color.color_muted))
                    textSize = 13f
                })
            } else {
                addView(timesLine(times, formatter))
            }
        }

    private fun timesLine(times: PrayerTimes, formatter: DateTimeFormatter): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addCell(getString(R.string.prayer_fajr_short), formatter.format(times.fajr))
            addCell(getString(R.string.prayer_dhuhr_short), formatter.format(times.dhuhr))
            addCell(getString(R.string.prayer_asr_short), formatter.format(times.asr))
            addCell(getString(R.string.prayer_maghrib_short), formatter.format(times.maghrib))
            addCell(getString(R.string.prayer_isha_short), formatter.format(times.isha))
        }

    private fun LinearLayout.addCell(label: String, value: String) {
        addView(TextView(context).apply {
            text = "$label\n$value"
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.color_primary_dark))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
