package ru.admiral.praytimes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.Qibla
import ru.admiral.praytimes.data.ActiveLocationResolver
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.domain.ActivePrayerWindow
import ru.admiral.praytimes.domain.PrayerDay
import ru.admiral.praytimes.domain.PrayerDayCalculator
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.ui.UiText
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
            update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) {
                return
            }
            val localized = AppSettings.localizedContext(context)
            val database = LocationDatabase(localized)
            val location = ActiveLocationResolver.resolve(localized, database)
            val method = ActiveLocationResolver.calculationMethod(localized, location)
            val asrMethod = AppSettings.asrMethod(localized)
            val adjustments = ActiveLocationResolver.adjustments(localized, database, location)
            val now = ZonedDateTime.now(location.zoneId)
            val date = LocalDate.now(location.zoneId)
            val parameters = method.parameters().withAsrMethod(asrMethod).copy(adjustments = adjustments)
            val prayerDay = PrayerDayCalculator.calculate(
                coordinates = location.coordinates,
                date = date,
                zoneId = location.zoneId,
                parameters = parameters,
                hijriDayAdjustment = AppSettings.hijriDayAdjustment(localized),
                now = now,
            )
            ids.forEach { id ->
                manager.updateAppWidget(
                    id,
                    views(
                        context = localized,
                        locationName = location.name,
                        qiblaDirection = Qibla(location.coordinates).direction.toFloat(),
                        prayerDay = prayerDay,
                        now = now,
                    ),
                )
            }
        }

        private fun views(
            context: Context,
            locationName: String,
            qiblaDirection: Float,
            prayerDay: PrayerDay,
            now: ZonedDateTime,
        ): RemoteViews =
            RemoteViews(context.packageName, R.layout.prayer_widget).apply {
                setTextViewText(R.id.widgetLocationText, locationName)
                setTextViewText(R.id.widgetQiblaText, context.getString(R.string.widget_qibla, qiblaDirection))
                val times = prayerDay.prayerTimes
                if (times == null) {
                    setTextViewText(R.id.widgetCurrentText, context.getString(R.string.calculation_unavailable))
                    setProgressBar(R.id.widgetProgressBar, WIDGET_PROGRESS_MAX, 0, false)
                    setTextViewText(R.id.widgetTimerText, "")
                    setTextViewText(R.id.widgetNextText, "--:--")
                } else {
                    val window = prayerDay.activeWindow ?: ActivePrayerWindow(
                        currentPrayer = Prayer.NONE,
                        nextPrayer = Prayer.FAJR,
                        currentStartedAt = null,
                        currentLeft = java.time.Duration.ZERO,
                        untilNext = java.time.Duration.ZERO,
                    )
                    val current = window.currentPrayer
                    val next = window.nextPrayer
                    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                    val currentLabel = UiText.prayerLabel(context, current, prayerDay.isRamadan)
                    val nextLabel = UiText.prayerLabel(context, next, prayerDay.isRamadan)
                    val nextTime = prayerDay.timeline
                        .firstOrNull { it.prayer == next && it.time.isAfter(now) }
                        ?.time
                        ?.let(formatter::format)
                        .orEmpty()
                    val progress = widgetProgress(window, now)
                    setTextViewText(R.id.widgetCurrentText, context.getString(R.string.widget_current_prayer, currentLabel))
                    setProgressBar(R.id.widgetProgressBar, WIDGET_PROGRESS_MAX, progress, false)
                    setTextViewText(
                        R.id.widgetTimerText,
                        if (window.currentPrayer == Prayer.NONE) {
                            context.getString(R.string.next_in, formatWidgetDuration(context, window.untilNext), nextLabel)
                        } else {
                            context.getString(R.string.active_left, formatWidgetDuration(context, window.currentLeft))
                        },
                    )
                    setTextViewText(R.id.widgetNextText, context.getString(R.string.widget_next_prayer, nextLabel, nextTime))
                }
                setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context))
                setOnClickPendingIntent(R.id.widgetLocationText, openLocationIntent(context))
            }

        private fun widgetProgress(window: ActivePrayerWindow, now: ZonedDateTime): Int {
            val startedAt = window.currentStartedAt ?: return 0
            val elapsedMillis = Duration.between(startedAt, now).toMillis().coerceAtLeast(0L)
            val leftMillis = window.currentLeft.toMillis().coerceAtLeast(0L)
            val totalMillis = elapsedMillis + leftMillis
            if (totalMillis <= 0L) {
                return 0
            }
            return (elapsedMillis * WIDGET_PROGRESS_MAX / totalMillis).toInt().coerceIn(0, WIDGET_PROGRESS_MAX)
        }

        private fun formatWidgetDuration(context: Context, duration: Duration): String {
            val seconds = duration.seconds.coerceAtLeast(0)
            return context.getString(R.string.duration_hours_minutes, seconds / 3600, seconds % 3600 / 60)
        }

        private fun openAppIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun openLocationIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                OPEN_LOCATION_REQUEST_CODE,
                Intent(context, LocationActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private const val OPEN_APP_REQUEST_CODE = 7011
        private const val OPEN_LOCATION_REQUEST_CODE = 7012
        private const val WIDGET_PROGRESS_MAX = 100
    }
}
