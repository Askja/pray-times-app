package ru.admiral.praytimes.sunnah

import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.holiday.HijriDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SunnahPrayerTimesTest {
    @Test
    fun duhaUsesSunriseAndZawalGuards() {
        val today = prayerTimes(LocalDate.of(2026, 4, 28))
        val tomorrow = prayerTimes(LocalDate.of(2026, 4, 29))

        val windows = SunnahPrayerTimes.calculate(
            prayerTimes = today,
            previousPrayerTimes = null,
            nextPrayerTimes = tomorrow,
            hijriDate = HijriDate(1447, 11, 11),
            isToday = true,
            now = today.fajr.plusHours(1),
        )

        val duha = windows.first { it.prayer == SunnahPrayer.DUHA }

        assertEquals(LocalTime.of(6, 20), duha.start.toLocalTime())
        assertEquals(LocalTime.of(11, 50), duha.end.toLocalTime())
    }

    @Test
    fun tahajjudUsesLastThirdOfTheNightBeforeNextFajr() {
        val today = prayerTimes(LocalDate.of(2026, 4, 28))
        val tomorrow = prayerTimes(LocalDate.of(2026, 4, 29))

        val windows = SunnahPrayerTimes.calculate(
            prayerTimes = today,
            previousPrayerTimes = null,
            nextPrayerTimes = tomorrow,
            hijriDate = HijriDate(1447, 11, 11),
            isToday = true,
            now = today.dhuhr,
        )

        val tahajjud = windows.first { it.prayer == SunnahPrayer.TAHAJJUD }

        assertEquals(LocalTime.of(1, 40), tahajjud.start.toLocalTime())
        assertEquals(tomorrow.fajr, tahajjud.end)
    }

    @Test
    fun preFajrTodayUsesPreviousNightForTahajjud() {
        val yesterday = prayerTimes(LocalDate.of(2026, 4, 27))
        val today = prayerTimes(LocalDate.of(2026, 4, 28))
        val tomorrow = prayerTimes(LocalDate.of(2026, 4, 29))

        val windows = SunnahPrayerTimes.calculate(
            prayerTimes = today,
            previousPrayerTimes = yesterday,
            nextPrayerTimes = tomorrow,
            hijriDate = HijriDate(1447, 11, 11),
            isToday = true,
            now = today.fajr.minusMinutes(30),
        )

        val tahajjud = windows.first { it.prayer == SunnahPrayer.TAHAJJUD }

        assertEquals(LocalDate.of(2026, 4, 28), tahajjud.end.toLocalDate())
        assertTrue(tahajjud.start.toLocalDate() == LocalDate.of(2026, 4, 28))
        assertTrue(tahajjud.end == today.fajr)
    }

    @Test
    fun ramadanAndEidWindowsAreDateSpecific() {
        val today = prayerTimes(LocalDate.of(2026, 4, 28))
        val tomorrow = prayerTimes(LocalDate.of(2026, 4, 29))

        val ramadanWindows = SunnahPrayerTimes.calculate(
            prayerTimes = today,
            previousPrayerTimes = null,
            nextPrayerTimes = tomorrow,
            hijriDate = HijriDate(1447, 9, 10),
            isToday = true,
            now = today.dhuhr,
        )
        val eidWindows = SunnahPrayerTimes.calculate(
            prayerTimes = today,
            previousPrayerTimes = null,
            nextPrayerTimes = tomorrow,
            hijriDate = HijriDate(1447, 10, 1),
            isToday = true,
            now = today.dhuhr,
        )

        assertTrue(ramadanWindows.any { it.prayer == SunnahPrayer.TARAWIH })
        assertTrue(eidWindows.any { it.prayer == SunnahPrayer.EID })
    }

    @Test
    fun sunnahFastsIncludeArafahForMoscowCurrentDate() {
        val fasts = SunnahFasts.calculate(
            date = LocalDate.of(2026, 5, 26),
            hijriDate = HijriDate(1447, 12, 9),
        )

        assertTrue(fasts.any { it.fast == SunnahFast.ARAFAH })
    }

    @Test
    fun sunnahFastsIncludeWeeklyAndWhiteDays() {
        val monday = assertDayOfWeek(LocalDate.of(2026, 4, 27), DayOfWeek.MONDAY)
        val weeklyFasts = SunnahFasts.calculate(monday, HijriDate(1447, 11, 10))
        val whiteDayFasts = SunnahFasts.calculate(LocalDate.of(2026, 4, 30), HijriDate(1447, 11, 13))

        assertTrue(weeklyFasts.any { it.fast == SunnahFast.MONDAY_THURSDAY })
        assertTrue(whiteDayFasts.any { it.fast == SunnahFast.WHITE_DAYS })
    }

    @Test
    fun sunnahFastsSkipRamadanAndForbiddenDays() {
        val ramadan = SunnahFasts.calculate(LocalDate.of(2026, 2, 20), HijriDate(1447, 9, 2))
        val eid = SunnahFasts.calculate(LocalDate.of(2026, 3, 20), HijriDate(1447, 10, 1))
        val tashreeq = SunnahFasts.calculate(LocalDate.of(2026, 5, 30), HijriDate(1447, 12, 13))

        assertTrue(ramadan.isEmpty())
        assertTrue(eid.isEmpty())
        assertTrue(tashreeq.isEmpty())
    }

    private fun assertDayOfWeek(date: LocalDate, dayOfWeek: DayOfWeek): LocalDate {
        assertEquals(dayOfWeek, date.dayOfWeek)
        return date
    }

    private fun prayerTimes(date: LocalDate): PrayerTimes {
        fun time(hour: Int, minute: Int): ZonedDateTime =
            ZonedDateTime.of(date, LocalTime.of(hour, minute), ZONE_ID)

        return PrayerTimes(
            fajr = time(5, 0),
            sunrise = time(6, 0),
            dhuhr = time(12, 0),
            asr = time(16, 0),
            maghrib = time(19, 0),
            isha = time(21, 0),
        )
    }

    private companion object {
        val ZONE_ID: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
