package ru.admiral.praytimes.adhan

import ru.admiral.praytimes.holiday.HolidayCalendar
import ru.admiral.praytimes.domain.LocationCalculationMethodResolver
import ru.admiral.praytimes.data.TimeZoneResolver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrayerTimesTest {
    @Test
    fun prayerTimesAreOrderedForMersin() {
        val times = assertNotNull(
            PrayerTimes.calculate(
                coordinates = Coordinates(36.8121, 34.6415),
                date = LocalDate.of(2025, 11, 25),
                zoneId = ZoneId.of("Europe/Istanbul"),
                parameters = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters(),
            ),
        )

        val instants = listOf(
            times.fajr,
            times.sunrise,
            times.dhuhr,
            times.asr,
            times.maghrib,
            times.isha,
        ).map { it.toInstant() }

        assertTrue(instants.zipWithNext().all { (left, right) -> left.isBefore(right) })
    }

    @Test
    fun turkeyDiyanetUsesOfficialTemkinAdjustmentsForMersin() {
        val times = assertNotNull(
            PrayerTimes.calculate(
                coordinates = Coordinates(36.8121, 34.6415),
                date = LocalDate.of(2026, 4, 28),
                zoneId = ZoneId.of("Europe/Istanbul"),
                parameters = CalculationMethod.TURKEY_DIYANET.parameters(),
            ),
        )

        assertEquals(LocalTime.of(5, 44), times.sunrise.toLocalTime())
        assertEquals(LocalTime.of(12, 44), times.dhuhr.toLocalTime())
        assertEquals(LocalTime.of(16, 29), times.asr.toLocalTime())
        assertEquals(LocalTime.of(19, 34), times.maghrib.toLocalTime())
    }

    @Test
    fun locationResolverPicksRegionalCalculationMethods() {
        assertEquals(
            CalculationMethod.TURKEY_DIYANET,
            LocationCalculationMethodResolver.resolve(
                Coordinates(36.8121, 34.6415),
                ZoneId.of("Europe/Istanbul"),
                "Mersin, Turkey",
            ),
        )
        assertEquals(
            CalculationMethod.RUSSIA,
            LocationCalculationMethodResolver.resolve(
                Coordinates(55.7558, 37.6173),
                ZoneId.of("Europe/Moscow"),
                "Moscow, Russia",
            ),
        )
        assertEquals(
            CalculationMethod.FRANCE_UOIF,
            LocationCalculationMethodResolver.resolve(
                Coordinates(48.8566, 2.3522),
                ZoneId.of("Europe/Paris"),
                "Paris, France",
            ),
        )
    }

    @Test
    fun russiaHighLatitudeRuleKeepsMoscowFajrAndIshaApart() {
        val times = assertNotNull(
            PrayerTimes.calculate(
                coordinates = Coordinates(55.7558, 37.6173),
                date = LocalDate.of(2026, 5, 26),
                zoneId = ZoneId.of("Europe/Moscow"),
                parameters = CalculationMethod.RUSSIA.parameters(),
            ),
        )

        assertEquals(LocalTime.of(2, 7), times.fajr.toLocalTime())
        assertEquals(LocalTime.of(22, 40), times.isha.toLocalTime())
        assertEquals(LocalDate.of(2026, 5, 26), times.isha.toLocalDate())
        assertTrue(times.fajr.toInstant().isBefore(times.sunrise.toInstant()))
        assertTrue(times.maghrib.toInstant().isBefore(times.isha.toInstant()))
    }

    @Test
    fun timeZoneResolverUsesKnownCoordinatesBeforeLongitudeFallback() {
        assertEquals(
            ZoneId.of("Europe/Istanbul"),
            TimeZoneResolver.byCountryOrCoordinates(null, Coordinates(36.8121, 34.6415)),
        )
        assertEquals(
            ZoneId.of("Europe/Moscow"),
            TimeZoneResolver.byCountryOrCoordinates("ru", Coordinates(55.7558, 37.6173)),
        )
        assertEquals(
            ZoneId.of("Asia/Vladivostok"),
            TimeZoneResolver.byCountryOrCoordinates("ru", Coordinates(43.1155, 131.8855)),
        )
        assertEquals(
            ZoneId.of("America/Los_Angeles"),
            TimeZoneResolver.byCountryOrCoordinates("us", Coordinates(37.7749, -122.4194)),
        )
    }

    @Test
    fun qiblaDirectionInIstanbulPointsSouthEast() {
        val direction = Qibla(Coordinates(41.0082, 28.9784)).direction

        assertTrue(direction in 145.0..155.0)
    }

    @Test
    fun solsticeRulesMatchPortedBranches() {
        assertTrue(PrayerTimes.daysSinceSolstice(365, 2025, 10.0) == 10)
        assertTrue(PrayerTimes.daysSinceSolstice(1, 2024, -10.0) == 194)
    }

    @Test
    fun holidayCalendarProducesYearlyOccurrences() {
        val holidays = HolidayCalendar.holidaysForGregorianYear(2026)

        assertTrue(holidays.size >= 8)
        assertTrue(holidays.all { it.date.year == 2026 })
    }
}
