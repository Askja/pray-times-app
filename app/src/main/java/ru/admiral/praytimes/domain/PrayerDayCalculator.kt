package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.CalculationParameters
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.holiday.HijriDate
import ru.admiral.praytimes.holiday.IslamicCalendar
import ru.admiral.praytimes.sunnah.SunnahFastDay
import ru.admiral.praytimes.sunnah.SunnahFasts
import ru.admiral.praytimes.sunnah.SunnahPrayerTimes
import ru.admiral.praytimes.sunnah.SunnahPrayerWindow
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class PrayerDay(
    val date: LocalDate,
    val hijriDate: HijriDate,
    val isRamadan: Boolean,
    val prayerTimes: PrayerTimes?,
    val previousPrayerTimes: PrayerTimes?,
    val nextPrayerTimes: PrayerTimes?,
    val timeline: List<PrayerTimePoint>,
    val sunnahPrayerWindows: List<SunnahPrayerWindow>,
    val sunnahFastDays: List<SunnahFastDay>,
    val activeWindow: ActivePrayerWindow?,
)

data class PrayerTimePoint(
    val prayer: Prayer,
    val time: ZonedDateTime,
)

data class ActivePrayerWindow(
    val currentPrayer: Prayer,
    val nextPrayer: Prayer,
    val currentStartedAt: ZonedDateTime?,
    val currentLeft: Duration,
    val untilNext: Duration,
)

object PrayerDayCalculator {
    fun calculate(
        coordinates: Coordinates,
        date: LocalDate,
        zoneId: ZoneId,
        parameters: CalculationParameters,
        hijriDayAdjustment: Int,
        now: ZonedDateTime,
    ): PrayerDay {
        val hijriDate = IslamicCalendar.hijriDate(date, hijriDayAdjustment)
        val prayerTimes = PrayerTimes.calculate(coordinates, date, zoneId, parameters)
        val previousPrayerTimes = prayerTimes?.let {
            PrayerTimes.calculate(coordinates, date.minusDays(1), zoneId, parameters)
        }
        val nextPrayerTimes = prayerTimes?.let {
            PrayerTimes.calculate(coordinates, date.plusDays(1), zoneId, parameters)
        }
        val isToday = date == now.toLocalDate()
        val timeline = prayerTimes?.let { buildTimeline(it, nextPrayerTimes) }.orEmpty()
        val sunnahPrayerWindows = prayerTimes?.let {
            SunnahPrayerTimes.calculate(
                prayerTimes = it,
                previousPrayerTimes = previousPrayerTimes,
                nextPrayerTimes = nextPrayerTimes,
                hijriDate = hijriDate,
                isToday = isToday,
                now = now,
            )
        }.orEmpty()

        return PrayerDay(
            date = date,
            hijriDate = hijriDate,
            isRamadan = hijriDate.month == RAMADAN_MONTH,
            prayerTimes = prayerTimes,
            previousPrayerTimes = previousPrayerTimes,
            nextPrayerTimes = nextPrayerTimes,
            timeline = timeline,
            sunnahPrayerWindows = sunnahPrayerWindows,
            sunnahFastDays = SunnahFasts.calculate(date, hijriDate),
            activeWindow = if (isToday && prayerTimes != null) {
                activePrayerWindow(
                    prayerTimes = prayerTimes,
                    previousPrayerTimes = previousPrayerTimes,
                    nextPrayerTimes = nextPrayerTimes,
                    now = now,
                )
            } else {
                null
            },
        )
    }

    private fun buildTimeline(prayerTimes: PrayerTimes, nextPrayerTimes: PrayerTimes?): List<PrayerTimePoint> =
        buildList {
            add(PrayerTimePoint(Prayer.FAJR, prayerTimes.fajr))
            add(PrayerTimePoint(Prayer.SUNRISE, prayerTimes.sunrise))
            add(PrayerTimePoint(Prayer.DHUHR, prayerTimes.dhuhr))
            add(PrayerTimePoint(Prayer.ASR, prayerTimes.asr))
            add(PrayerTimePoint(Prayer.MAGHRIB, prayerTimes.maghrib))
            add(PrayerTimePoint(Prayer.ISHA, prayerTimes.isha))
            if (nextPrayerTimes != null) {
                add(PrayerTimePoint(Prayer.FAJR, nextPrayerTimes.fajr))
            }
        }

    fun activePrayerWindow(
        prayerTimes: PrayerTimes,
        previousPrayerTimes: PrayerTimes?,
        nextPrayerTimes: PrayerTimes?,
        now: ZonedDateTime,
    ): ActivePrayerWindow {
        val timeline = buildList {
            if (previousPrayerTimes != null) {
                add(PrayerTimePoint(Prayer.ISHA, previousPrayerTimes.isha))
            }
            addAll(buildTimeline(prayerTimes, nextPrayerTimes))
        }.sortedBy { it.time.toInstant() }
        val current = timeline.lastOrNull { !it.time.isAfter(now) }
        val next = timeline.firstOrNull { it.time.isAfter(now) } ?: timeline.last()
        val currentPrayer = current?.prayer ?: Prayer.NONE
        val currentLeft = current?.let { Duration.between(now, next.time).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
        return ActivePrayerWindow(
            currentPrayer = currentPrayer,
            nextPrayer = next.prayer,
            currentStartedAt = current?.time,
            currentLeft = currentLeft,
            untilNext = Duration.between(now, next.time).coerceAtLeast(Duration.ZERO),
        )
    }

    private const val RAMADAN_MONTH = 9
}
