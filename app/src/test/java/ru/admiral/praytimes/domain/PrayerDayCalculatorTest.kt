package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.sunnah.SunnahFast
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrayerDayCalculatorTest {
    @Test
    fun calculatesMoscowPrayerDayWithTimelineAndArafahFast() {
        val zoneId = ZoneId.of("Europe/Moscow")
        val day = PrayerDayCalculator.calculate(
            coordinates = Coordinates(55.7558, 37.6173),
            date = LocalDate.of(2026, 5, 26),
            zoneId = zoneId,
            parameters = CalculationMethod.RUSSIA.parameters(),
            hijriDayAdjustment = 0,
            now = ZonedDateTime.of(2026, 5, 26, 12, 0, 0, 0, zoneId),
        )

        val prayerTimes = assertNotNull(day.prayerTimes)

        assertEquals(7, day.timeline.size)
        assertEquals(Prayer.FAJR, day.timeline.first().prayer)
        assertEquals(prayerTimes.fajr, day.timeline.first().time)
        assertEquals(Prayer.FAJR, day.timeline.last().prayer)
        assertEquals(LocalDate.of(2026, 5, 27), day.timeline.last().time.toLocalDate())
        assertEquals(Prayer.SUNRISE, day.activeWindow?.currentPrayer)
        assertEquals(Prayer.DHUHR, day.activeWindow?.nextPrayer)
        assertTrue(day.sunnahFastDays.any { it.fast == SunnahFast.ARAFAH })
    }

    @Test
    fun preFajrUsesPreviousIshaAsCurrentPrayer() {
        val zoneId = ZoneId.of("Europe/Moscow")
        val day = PrayerDayCalculator.calculate(
            coordinates = Coordinates(55.7558, 37.6173),
            date = LocalDate.of(2026, 5, 26),
            zoneId = zoneId,
            parameters = CalculationMethod.RUSSIA.parameters(),
            hijriDayAdjustment = 0,
            now = ZonedDateTime.of(2026, 5, 26, 1, 0, 0, 0, zoneId),
        )

        assertEquals(Prayer.ISHA, day.activeWindow?.currentPrayer)
        assertEquals(Prayer.FAJR, day.activeWindow?.nextPrayer)
    }
}
