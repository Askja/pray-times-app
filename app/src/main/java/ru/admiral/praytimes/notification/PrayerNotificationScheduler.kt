package ru.admiral.praytimes.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.settings.AppSettings
import ru.admiral.praytimes.sunnah.SunnahPrayer
import ru.admiral.praytimes.sunnah.SunnahPrayerTimes
import ru.admiral.praytimes.sunnah.SunnahPrayerWindow
import ru.admiral.praytimes.ui.UiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.absoluteValue

object PrayerNotificationScheduler {
    private val prayers = listOf(
        Prayer.FAJR,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    fun sync(
        context: Context,
        coordinates: Coordinates,
        zoneId: ZoneId,
        method: CalculationMethod,
        asrMethod: AsrMethod,
        adjustments: PrayerAdjustments,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val today = LocalDate.now(zoneId)
        cancelKnownAlarms(context, alarmManager, today)

        val settings = NotificationSettings.from(context)
        if (!settings.enabled) {
            cancelMaintenance(context, alarmManager)
            return
        }

        PrayerNotificationChannels.ensure(context)
        val parameters = method.parameters().withAsrMethod(asrMethod).copy(adjustments = adjustments)
        val days = (0..SCHEDULE_WINDOW_DAYS).map { offset ->
            val date = today.plusDays(offset.toLong())
            date to PrayerTimes.calculate(coordinates, date, zoneId, parameters)
        }.filter { it.second != null }

        days.zipWithNext().forEach { (currentDay, nextDay) ->
            val date = currentDay.first
            val prayerTimes = currentDay.second ?: return@forEach
            val nextPrayerTimes = nextDay.second ?: return@forEach
            prayers.forEach { prayer ->
                schedulePrayer(context, alarmManager, date, prayer, prayerTimes, nextPrayerTimes, settings)
            }
            if (settings.notifySunnahPrayers) {
                scheduleSunnahPrayers(
                    context = context,
                    alarmManager = alarmManager,
                    date = date,
                    prayerTimes = prayerTimes,
                    nextPrayerTimes = nextPrayerTimes,
                )
            }
        }
        scheduleMaintenance(context, alarmManager, ZonedDateTime.now(zoneId).plusDays(1).toLocalDate().atStartOfDay(zoneId))
    }

    fun cancelUpcoming(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        cancelKnownAlarms(
            context = context,
            alarmManager = alarmManager,
            today = LocalDate.now(zoneId),
        )
        cancelMaintenance(context, alarmManager)
    }

    private fun schedulePrayer(
        context: Context,
        alarmManager: AlarmManager,
        date: LocalDate,
        prayer: Prayer,
        prayerTimes: PrayerTimes,
        nextPrayerTimes: PrayerTimes,
        settings: NotificationSettings,
    ) {
        val notifyPrayer = settings.prayerNotificationEnabled(prayer)
        val adhanPrayer = settings.prayerAdhanEnabled(prayer)
        if (!notifyPrayer && !(settings.playAdhanAtStart && adhanPrayer)) {
            return
        }
        val start = prayerTimes.timeForPrayer(prayer) ?: return
        val end = endTime(prayer, prayerTimes, nextPrayerTimes) ?: return
        val isRamadan = IslamicCalendar.hijriDate(date, settings.hijriDayAdjustment).month == RAMADAN_MONTH
        val isJumuah = prayer == Prayer.DHUHR &&
            date.dayOfWeek == DayOfWeek.FRIDAY &&
            AppSettings.showJumuahPrayer(context)
        val prayerLabel = UiText.prayerLabel(context, prayer, isRamadan, isJumuah)

        val notifyBeforeStart = notifyPrayer && settings.notifyBeforeStart
        val notifyBeforeRamadanImsak = settings.notifyRamadanImsak && isRamadan && prayer == Prayer.FAJR
        if (notifyBeforeStart || notifyBeforeRamadanImsak) {
            schedule(
                context = context,
                alarmManager = alarmManager,
                event = PrayerNotificationEvent.BEFORE_START,
                prayer = prayer,
                date = date,
                triggerAt = start.minusMinutes(LEAD_MINUTES),
                prayerLabel = prayerLabel,
                playAdhan = false,
                vibrate = settings.vibrate,
            )
        }

        if ((notifyPrayer && settings.notifyAtStart) || (settings.playAdhanAtStart && adhanPrayer)) {
            schedule(
                context = context,
                alarmManager = alarmManager,
                event = PrayerNotificationEvent.START,
                prayer = prayer,
                date = date,
                triggerAt = start,
                prayerLabel = prayerLabel,
                playAdhan = settings.playAdhanAtStart && adhanPrayer,
                vibrate = settings.vibrate,
            )
        }

        if (notifyPrayer && settings.notifyBeforeEnd) {
            schedule(
                context = context,
                alarmManager = alarmManager,
                event = PrayerNotificationEvent.BEFORE_END,
                prayer = prayer,
                date = date,
                triggerAt = end.minusMinutes(LEAD_MINUTES),
                prayerLabel = prayerLabel,
                playAdhan = false,
                vibrate = settings.vibrate,
            )
        }
    }

    private fun scheduleSunnahPrayers(
        context: Context,
        alarmManager: AlarmManager,
        date: LocalDate,
        prayerTimes: PrayerTimes,
        nextPrayerTimes: PrayerTimes,
    ) {
        val hijriDate = IslamicCalendar.hijriDate(date, AppSettings.hijriDayAdjustment(context))
        SunnahPrayerTimes.calculate(
            prayerTimes = prayerTimes,
            previousPrayerTimes = null,
            nextPrayerTimes = nextPrayerTimes,
            hijriDate = hijriDate,
            isToday = false,
            now = prayerTimes.fajr,
        ).forEach { window ->
            scheduleSunnahPrayer(context, alarmManager, date, window)
        }
    }

    private fun scheduleSunnahPrayer(
        context: Context,
        alarmManager: AlarmManager,
        date: LocalDate,
        window: SunnahPrayerWindow,
    ) {
        schedule(
            context = context,
            alarmManager = alarmManager,
            event = PrayerNotificationEvent.START,
            requestCode = sunnahRequestCode(date, window.prayer, PrayerNotificationEvent.START),
            triggerAt = window.start,
            prayerLabel = context.getString(window.prayer.titleRes),
            playAdhan = false,
            vibrate = AppSettings.vibratePrayerNotifications(context),
        )
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        event: PrayerNotificationEvent,
        prayer: Prayer,
        date: LocalDate,
        triggerAt: ZonedDateTime,
        prayerLabel: String,
        playAdhan: Boolean,
        vibrate: Boolean,
    ) {
        val requestCode = requestCode(date, prayer, event)
        schedule(
            context = context,
            alarmManager = alarmManager,
            event = event,
            requestCode = requestCode,
            triggerAt = triggerAt,
            prayerLabel = prayerLabel,
            playAdhan = playAdhan,
            vibrate = vibrate,
        )
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        event: PrayerNotificationEvent,
        requestCode: Int,
        triggerAt: ZonedDateTime,
        prayerLabel: String,
        playAdhan: Boolean,
        vibrate: Boolean,
    ) {
        val triggerMillis = triggerAt.toInstant().toEpochMilli()
        if (triggerMillis <= System.currentTimeMillis() + MIN_TRIGGER_DELAY_MS) {
            return
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            receiverIntent(context, event, prayerLabel, playAdhan, vibrate, requestCode),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms() -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            Build.VERSION.SDK_INT in Build.VERSION_CODES.M until Build.VERSION_CODES.S -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            else -> {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    private fun scheduleMaintenance(context: Context, alarmManager: AlarmManager, triggerAt: ZonedDateTime) {
        val pendingIntent = maintenancePendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val triggerMillis = triggerAt.plusMinutes(MAINTENANCE_DELAY_MINUTES).toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun cancelMaintenance(context: Context, alarmManager: AlarmManager) {
        val pendingIntent = maintenancePendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun maintenancePendingIntent(context: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            MAINTENANCE_REQUEST_CODE,
            Intent(context, PrayerScheduleReceiver::class.java).apply {
                action = PrayerScheduleReceiver.ACTION_RESCHEDULE
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancelKnownAlarms(context: Context, alarmManager: AlarmManager, today: LocalDate) {
        (-1..(SCHEDULE_WINDOW_DAYS + 1)).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            prayers.forEach { prayer ->
                PrayerNotificationEvent.entries.forEach { event ->
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode(date, prayer, event),
                        Intent(context, PrayerAlarmReceiver::class.java).apply {
                            action = PrayerAlarmReceiver.ACTION_PRAYER_NOTIFICATION
                        },
                        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                    )
                    if (pendingIntent != null) {
                        alarmManager.cancel(pendingIntent)
                        pendingIntent.cancel()
                    }
                }
            }
            SunnahPrayer.entries.forEach { prayer ->
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sunnahRequestCode(date, prayer, PrayerNotificationEvent.START),
                    Intent(context, PrayerAlarmReceiver::class.java).apply {
                        action = PrayerAlarmReceiver.ACTION_PRAYER_NOTIFICATION
                    },
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    private fun receiverIntent(
        context: Context,
        event: PrayerNotificationEvent,
        prayerLabel: String,
        playAdhan: Boolean,
        vibrate: Boolean,
        notificationId: Int,
    ): Intent =
        Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_PRAYER_NOTIFICATION
            putExtra(PrayerAlarmReceiver.EXTRA_EVENT, event.name)
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_LABEL, prayerLabel)
            putExtra(PrayerAlarmReceiver.EXTRA_PLAY_ADHAN, playAdhan)
            putExtra(PrayerAlarmReceiver.EXTRA_VIBRATE, vibrate)
            putExtra(PrayerAlarmReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

    private fun endTime(prayer: Prayer, prayerTimes: PrayerTimes, nextPrayerTimes: PrayerTimes): ZonedDateTime? =
        when (prayer) {
            Prayer.FAJR -> prayerTimes.sunrise
            Prayer.DHUHR -> prayerTimes.asr
            Prayer.ASR -> prayerTimes.maghrib
            Prayer.MAGHRIB -> prayerTimes.isha
            Prayer.ISHA -> nextPrayerTimes.fajr
            Prayer.NONE,
            Prayer.SUNRISE,
            -> null
        }

    private fun requestCode(date: LocalDate, prayer: Prayer, event: PrayerNotificationEvent): Int =
        (date.toEpochDay().toInt().absoluteValue % 100_000) * 100 +
            prayer.ordinal * 10 +
            event.ordinal

    private fun sunnahRequestCode(date: LocalDate, prayer: SunnahPrayer, event: PrayerNotificationEvent): Int =
        SUNNAH_REQUEST_CODE_OFFSET +
            (date.toEpochDay().toInt().absoluteValue % 10_000) * 100 +
            prayer.ordinal * 10 +
            event.ordinal

    private data class NotificationSettings(
        val notifyAtStart: Boolean,
        val notifyBeforeStart: Boolean,
        val notifyBeforeEnd: Boolean,
        val playAdhanAtStart: Boolean,
        val notifySunnahPrayers: Boolean,
        val notifyRamadanImsak: Boolean,
        val vibrate: Boolean,
        val hijriDayAdjustment: Int,
        private val prayerEnabled: Map<Prayer, Boolean>,
        private val adhanEnabled: Map<Prayer, Boolean>,
    ) {
        val enabled: Boolean
            get() = notifyAtStart ||
                notifyBeforeStart ||
                notifyBeforeEnd ||
                playAdhanAtStart ||
                notifySunnahPrayers ||
                notifyRamadanImsak

        fun prayerNotificationEnabled(prayer: Prayer): Boolean =
            prayerEnabled[prayer] ?: true

        fun prayerAdhanEnabled(prayer: Prayer): Boolean =
            adhanEnabled[prayer] ?: true

        companion object {
            fun from(context: Context): NotificationSettings {
                val prayerEnabled = AppSettings.notificationProfilePrayers.associateWith { prayer ->
                    AppSettings.prayerNotificationEnabled(context, prayer)
                }
                val adhanEnabled = AppSettings.notificationProfilePrayers.associateWith { prayer ->
                    AppSettings.prayerAdhanEnabled(context, prayer)
                }
                return NotificationSettings(
                    notifyAtStart = AppSettings.notifyAtPrayerStart(context),
                    notifyBeforeStart = AppSettings.notifyBeforePrayerStart(context),
                    notifyBeforeEnd = AppSettings.notifyBeforePrayerEnd(context),
                    playAdhanAtStart = AppSettings.playAdhanAtPrayerStart(context),
                    notifySunnahPrayers = AppSettings.notifySunnahPrayers(context),
                    notifyRamadanImsak = AppSettings.notifyRamadanImsak(context),
                    vibrate = AppSettings.vibratePrayerNotifications(context),
                    hijriDayAdjustment = AppSettings.hijriDayAdjustment(context),
                    prayerEnabled = prayerEnabled,
                    adhanEnabled = adhanEnabled,
                )
            }
        }
    }

    private const val LEAD_MINUTES = 10L
    private const val MAINTENANCE_DELAY_MINUTES = 20L
    private const val RAMADAN_MONTH = 9
    private const val MIN_TRIGGER_DELAY_MS = 1_000L
    private const val SCHEDULE_WINDOW_DAYS = 5
    private const val MAINTENANCE_REQUEST_CODE = 20_900_000
    private const val SUNNAH_REQUEST_CODE_OFFSET = 20_000_000
}
