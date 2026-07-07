package ru.admiral.praytimes.adhan

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

data class PrayerTimes(
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun currentPrayer(time: ZonedDateTime = ZonedDateTime.now(fajr.zone)): Prayer {
        val whenMillis = time.toInstant().toEpochMilli()
        return when {
            isha.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.ISHA
            maghrib.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.MAGHRIB
            asr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.ASR
            dhuhr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.DHUHR
            sunrise.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.SUNRISE
            fajr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.FAJR
            else -> Prayer.NONE
        }
    }

    fun nextPrayer(time: ZonedDateTime = ZonedDateTime.now(fajr.zone)): Prayer {
        val whenMillis = time.toInstant().toEpochMilli()
        return when {
            isha.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.NONE
            maghrib.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.ISHA
            asr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.MAGHRIB
            dhuhr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.ASR
            sunrise.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.DHUHR
            fajr.toInstant().toEpochMilli() - whenMillis <= 0 -> Prayer.SUNRISE
            else -> Prayer.FAJR
        }
    }

    fun timeForPrayer(prayer: Prayer): ZonedDateTime? = when (prayer) {
        Prayer.FAJR -> fajr
        Prayer.SUNRISE -> sunrise
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
        Prayer.NONE -> null
    }

    companion object {
        fun calculate(
            coordinates: Coordinates,
            date: LocalDate,
            zoneId: ZoneId,
            parameters: CalculationParameters,
        ): PrayerTimes? {
            return calculateUtc(
                coordinates = coordinates,
                dateUtc = CalendarUtil.resolveTime(DateComponents.from(date)),
                parameters = parameters,
            )?.toZoned(zoneId)
        }

        fun daysSinceSolstice(dayOfYear: Int, year: Int, latitude: Double): Int {
            val northernOffset = 10
            val isLeapYear = CalendarUtil.isLeapYear(year)
            val southernOffset = if (isLeapYear) 173 else 172
            val daysInYear = if (isLeapYear) 366 else 365

            return if (latitude >= 0.0) {
                val days = dayOfYear + northernOffset
                if (days >= daysInYear) days - daysInYear else days
            } else {
                val days = dayOfYear - southernOffset
                if (days < 0) days + daysInYear else days
            }
        }

        private fun calculateUtc(
            coordinates: Coordinates,
            dateUtc: LocalDateTime,
            parameters: CalculationParameters,
        ): PrayerTimesUtc? {
            val year = dateUtc.year
            val dayOfYear = dateUtc.dayOfYear
            val solarTime = SolarTime(dateUtc, coordinates)

            val transit = TimeComponents.from(solarTime.transit)?.dateTime(dateUtc)
            val sunrise = TimeComponents.from(solarTime.sunrise)?.dateTime(dateUtc)
            val sunset = TimeComponents.from(solarTime.sunset)?.dateTime(dateUtc)

            if (transit == null || sunrise == null || sunset == null) {
                return null
            }

            val asr = TimeComponents.from(solarTime.afternoon(parameters.asrMethod.shadowFactor))?.dateTime(dateUtc)
                ?: return null
            val maghrib = parameters.maghribAngle?.let { angle ->
                TimeComponents.from(solarTime.hourAngle(-angle, true))?.dateTime(dateUtc)
            } ?: sunset

            val tomorrowSunrise = sunrise.plusDays(1)
            val night = tomorrowSunrise.epochMillisUtc() - maghrib.epochMillisUtc()
            val nightPortions = parameters.nightPortions()
            var fajr = TimeComponents.from(solarTime.hourAngle(-parameters.fajrAngle, false))?.dateTime(dateUtc)

            if (
                parameters.method == CalculationMethod.MOON_SIGHTING_COMMITTEE &&
                coordinates.latitude >= 55.0
            ) {
                fajr = sunrise.minusSeconds(night / 7000L)
            }

            val safeFajr = if (parameters.method == CalculationMethod.MOON_SIGHTING_COMMITTEE) {
                seasonAdjustedMorningTwilight(coordinates.latitude, dayOfYear, year, sunrise)
            } else {
                val nightFraction = (nightPortions.fajr * night / 1000.0).toLong()
                sunrise.minusSeconds(nightFraction)
            }

            if (fajr == null || fajr.isBefore(safeFajr)) {
                fajr = safeFajr
            }

            var isha: LocalDateTime? = if (parameters.ishaInterval > 0) {
                maghrib.plusMinutes(parameters.ishaInterval.toLong())
            } else {
                TimeComponents.from(solarTime.hourAngle(-parameters.ishaAngle, true))?.dateTime(dateUtc)
            }

            if (parameters.ishaInterval <= 0) {
                if (
                    parameters.method == CalculationMethod.MOON_SIGHTING_COMMITTEE &&
                    coordinates.latitude >= 55.0
                ) {
                    isha = maghrib.plusSeconds(night / 7000L)
                }

                val safeIsha = if (parameters.method == CalculationMethod.MOON_SIGHTING_COMMITTEE) {
                    seasonAdjustedEveningTwilight(coordinates.latitude, dayOfYear, year, maghrib)
                } else {
                    val nightFraction = (nightPortions.isha * night / 1000.0).toLong()
                    maghrib.plusSeconds(nightFraction)
                }

                if (isha == null || isha.isAfter(safeIsha)) {
                    isha = safeIsha
                }
            }

            if (isha == null) {
                return null
            }

            val adjustments = parameters.adjustments
            val methodAdjustments = parameters.methodAdjustments
            return PrayerTimesUtc(
                fajr = roundedAdjusted(fajr, adjustments.fajr + methodAdjustments.fajr),
                sunrise = roundedAdjusted(sunrise, adjustments.sunrise + methodAdjustments.sunrise),
                dhuhr = roundedAdjusted(transit, adjustments.dhuhr + methodAdjustments.dhuhr),
                asr = roundedAdjusted(asr, adjustments.asr + methodAdjustments.asr),
                maghrib = roundedAdjusted(maghrib, adjustments.maghrib + methodAdjustments.maghrib),
                isha = roundedAdjusted(isha, adjustments.isha + methodAdjustments.isha),
            )
        }

        private fun roundedAdjusted(time: LocalDateTime, minutes: Int): LocalDateTime =
            CalendarUtil.roundedMinute(time.plusMinutes(minutes.toLong()))

        private fun seasonAdjustedMorningTwilight(
            latitude: Double,
            dayOfYear: Int,
            year: Int,
            sunrise: LocalDateTime,
        ): LocalDateTime {
            val a = 75.0 + 28.65 / 55.0 * abs(latitude)
            val b = 75.0 + 19.44 / 55.0 * abs(latitude)
            val c = 75.0 + 32.74 / 55.0 * abs(latitude)
            val d = 75.0 + 48.10 / 55.0 * abs(latitude)
            val adjustment = seasonalAdjustment(a, b, c, d, dayOfYear, year, latitude)
            return sunrise.minusSeconds((adjustment * 60.0).roundToInt().toLong())
        }

        private fun seasonAdjustedEveningTwilight(
            latitude: Double,
            dayOfYear: Int,
            year: Int,
            sunset: LocalDateTime,
        ): LocalDateTime {
            val a = 75.0 + 25.60 / 55.0 * abs(latitude)
            val b = 75.0 + 2.050 / 55.0 * abs(latitude)
            val c = 75.0 - 9.210 / 55.0 * abs(latitude)
            val d = 75.0 + 6.140 / 55.0 * abs(latitude)
            val adjustment = seasonalAdjustment(a, b, c, d, dayOfYear, year, latitude)
            return sunset.plusSeconds((adjustment * 60.0).roundToInt().toLong())
        }

        private fun seasonalAdjustment(
            a: Double,
            b: Double,
            c: Double,
            d: Double,
            dayOfYear: Int,
            year: Int,
            latitude: Double,
        ): Double {
            val daysSinceSolstice = daysSinceSolstice(dayOfYear, year, latitude)
            return when {
                daysSinceSolstice < 91 -> a + (b - a) / 91.0 * daysSinceSolstice
                daysSinceSolstice < 137 -> b + (c - b) / 46.0 * (daysSinceSolstice - 91)
                daysSinceSolstice < 183 -> c + (d - c) / 46.0 * (daysSinceSolstice - 137)
                daysSinceSolstice < 229 -> d + (c - d) / 46.0 * (daysSinceSolstice - 183)
                daysSinceSolstice < 275 -> c + (b - c) / 46.0 * (daysSinceSolstice - 229)
                else -> b + (a - b) / 91.0 * (daysSinceSolstice - 275)
            }
        }

        private fun PrayerTimesUtc.toZoned(zoneId: ZoneId): PrayerTimes =
            PrayerTimes(
                fajr = fajr.asZoned(zoneId),
                sunrise = sunrise.asZoned(zoneId),
                dhuhr = dhuhr.asZoned(zoneId),
                asr = asr.asZoned(zoneId),
                maghrib = maghrib.asZoned(zoneId),
                isha = isha.asZoned(zoneId),
            )

        private fun LocalDateTime.asZoned(zoneId: ZoneId): ZonedDateTime =
            atOffset(ZoneOffset.UTC).atZoneSameInstant(zoneId)
    }
}

private data class PrayerTimesUtc(
    val fajr: LocalDateTime,
    val sunrise: LocalDateTime,
    val dhuhr: LocalDateTime,
    val asr: LocalDateTime,
    val maghrib: LocalDateTime,
    val isha: LocalDateTime,
)
