package ru.admiral.praytimes.notification

import android.content.Context
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.settings.AppSettings
import java.time.LocalDate
import java.time.ZoneId

class PrayerNotificationSyncController {
    private var lastScheduleKey = ""

    fun syncIfNeeded(
        context: Context,
        coordinates: Coordinates,
        zoneId: ZoneId,
        method: CalculationMethod,
        asrMethod: AsrMethod,
        adjustments: PrayerAdjustments,
    ) {
        val key = listOf(
            LocalDate.now(zoneId),
            coordinates.latitude,
            coordinates.longitude,
            zoneId.id,
            method.name,
            asrMethod.name,
            adjustments,
            AppSettings.hijriDayAdjustment(context),
            AppSettings.notifyAtPrayerStart(context),
            AppSettings.notifyBeforePrayerStart(context),
            AppSettings.notifyBeforePrayerEnd(context),
            AppSettings.playAdhanAtPrayerStart(context),
            AppSettings.notifySunnahPrayers(context),
            AppSettings.notifyRamadanImsak(context),
            AppSettings.vibratePrayerNotifications(context),
        ).joinToString(separator = "|")

        if (key == lastScheduleKey) {
            return
        }

        PrayerNotificationScheduler.sync(
            context = context,
            coordinates = coordinates,
            zoneId = zoneId,
            method = method,
            asrMethod = asrMethod,
            adjustments = adjustments,
        )
        lastScheduleKey = key
    }

    fun invalidate() {
        lastScheduleKey = ""
    }
}
