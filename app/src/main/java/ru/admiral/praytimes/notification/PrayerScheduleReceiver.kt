package ru.admiral.praytimes.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.admiral.praytimes.PrayerWidgetProvider
import ru.admiral.praytimes.data.ActiveLocationResolver
import ru.admiral.praytimes.data.LocationDatabase
import ru.admiral.praytimes.settings.AppSettings

class PrayerScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in supportedActions) {
            return
        }

        reschedule(context)
    }

    companion object {
        const val ACTION_RESCHEDULE = "ru.admiral.praytimes.action.RESCHEDULE_PRAYERS"

        fun reschedule(context: Context) {
            val localized = AppSettings.localizedContext(context)
            val database = LocationDatabase(localized)
            val location = ActiveLocationResolver.resolve(localized, database)
            PrayerNotificationScheduler.sync(
                context = localized,
                coordinates = location.coordinates,
                zoneId = location.zoneId,
                method = ActiveLocationResolver.calculationMethod(localized, location),
                asrMethod = AppSettings.asrMethod(localized),
                adjustments = ActiveLocationResolver.adjustments(localized, database, location),
            )
            PrayerWidgetProvider.updateAll(localized)
        }

        private val supportedActions = setOf(
            ACTION_RESCHEDULE,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
