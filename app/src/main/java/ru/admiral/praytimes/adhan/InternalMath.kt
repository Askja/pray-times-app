package ru.admiral.praytimes.adhan

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

internal object MathHelper {
    fun toDegrees(radians: Double): Double = radians * 180.0 / PI

    fun toRadians(degrees: Double): Double = degrees * PI / 180.0
}

internal object DoubleUtil {
    fun normalizeWithBound(value: Double, max: Double): Double = value - max * floor(value / max)

    fun unwindAngle(value: Double): Double = normalizeWithBound(value, 360.0)

    fun closestAngle(angle: Double): Double {
        if (angle in -180.0..180.0) {
            return angle
        }

        return angle - 360.0 * round(angle / 360.0)
    }
}

internal object CalendarUtil {
    fun isLeapYear(year: Int): Boolean = year % 4 == 0 && !(year % 100 == 0 && year % 400 != 0)

    fun resolveTime(components: DateComponents): LocalDateTime =
        LocalDateTime.of(components.year, components.month, components.day, 0, 0)

    fun roundedMinute(whenUtc: LocalDateTime): LocalDateTime =
        whenUtc.plusSeconds(30).withSecond(0).withNano(0)
}

internal object CalendricalHelper {
    fun julianDay(dateUtc: LocalDateTime): Double {
        val hour = dateUtc.hour + dateUtc.minute / 60.0
        return julianDay(dateUtc.year, dateUtc.monthValue, dateUtc.dayOfMonth, hour)
    }

    fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        val y = if (month > 2) year else year - 1
        val m = if (month > 2) month else month + 12
        val d = day + hours / 24.0
        val a = y / 100
        val b = 2 - a + a / 4
        val i0 = floor(365.25 * (y + 4716)).toInt()
        val i1 = floor(30.6001 * (m + 1)).toInt()
        return i0 + i1 + d + b - 1524.5
    }

    fun julianCentury(julianDay: Double): Double = (julianDay - 2451545.0) / 36525.0
}

internal data class TimeComponents(
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
) {
    fun dateTime(dateUtc: LocalDateTime): LocalDateTime =
        dateUtc.toLocalDate()
            .atStartOfDay()
            .plusHours(hours)
            .plusMinutes(minutes)
            .plusSeconds(seconds)

    companion object {
        fun from(value: Double): TimeComponents? {
            if (value == Double.MAX_VALUE || value.isNaN()) {
                return null
            }

            val hours = floor(value)
            val minutes = floor((value - hours) * 60.0)
            val seconds = floor((value - (hours + minutes / 60.0)) * 3600.0)
            return TimeComponents(hours.toLong(), minutes.toLong(), seconds.toLong())
        }
    }
}

internal object Astronomical {
    fun meanSolarLongitude(julianCentury: Double): Double {
        val longitude = 280.4664567 +
            36000.76983 * julianCentury +
            0.0003032 * julianCentury.pow(2)
        return DoubleUtil.unwindAngle(longitude)
    }

    fun meanLunarLongitude(julianCentury: Double): Double {
        val longitude = 218.3165 + 481267.8813 * julianCentury
        return DoubleUtil.unwindAngle(longitude)
    }

    fun apparentSolarLongitude(julianCentury: Double, meanLongitude: Double): Double {
        val longitude = meanLongitude + solarEquationOfTheCenter(
            julianCentury,
            meanSolarAnomaly(julianCentury),
        )
        val omega = 125.04 - 1934.136 * julianCentury
        val lambda = longitude - 0.00569 - 0.00478 * sin(MathHelper.toRadians(omega))
        return DoubleUtil.unwindAngle(lambda)
    }

    fun ascendingLunarNodeLongitude(julianCentury: Double): Double {
        val omega = 125.04452 -
            1934.136261 * julianCentury +
            0.0020708 * julianCentury.pow(2) +
            julianCentury.pow(3) / 450000.0
        return DoubleUtil.unwindAngle(omega)
    }

    fun meanSolarAnomaly(julianCentury: Double): Double {
        val anomaly = 357.52911 +
            35999.05029 * julianCentury -
            0.0001537 * julianCentury.pow(2)
        return DoubleUtil.unwindAngle(anomaly)
    }

    fun solarEquationOfTheCenter(julianCentury: Double, meanAnomaly: Double): Double {
        val anomalyRadians = MathHelper.toRadians(meanAnomaly)
        val term1 = (1.914602 - 0.004817 * julianCentury - 0.000014 * julianCentury.pow(2)) *
            sin(anomalyRadians)
        val term2 = (0.019993 - 0.000101 * julianCentury) * sin(2.0 * anomalyRadians)
        val term3 = 0.000289 * sin(3.0 * anomalyRadians)
        return term1 + term2 + term3
    }

    fun meanObliquityOfTheEcliptic(julianCentury: Double): Double =
        23.439291 -
            0.013004167 * julianCentury -
            0.0000001639 * julianCentury.pow(2) +
            0.0000005036 * julianCentury.pow(3)

    fun apparentObliquityOfTheEcliptic(julianCentury: Double, meanObliquity: Double): Double {
        val obliquity = 125.04 - 1934.136 * julianCentury
        return meanObliquity + 0.00256 * cos(MathHelper.toRadians(obliquity))
    }

    fun meanSiderealTime(julianCentury: Double): Double {
        val julianDay = julianCentury * 36525.0 + 2451545.0
        val theta = 280.46061837 +
            360.98564736629 * (julianDay - 2451545.0) +
            0.000387933 * julianCentury.pow(2) -
            julianCentury.pow(3) / 38710000.0
        return DoubleUtil.unwindAngle(theta)
    }

    fun nutationInLongitude(
        solarLongitude: Double,
        lunarLongitude: Double,
        ascendingNode: Double,
    ): Double {
        val term1 = (-17.2 / 3600.0) * sin(MathHelper.toRadians(ascendingNode))
        val term2 = (1.32 / 3600.0) * sin(2.0 * MathHelper.toRadians(solarLongitude))
        val term3 = (0.23 / 3600.0) * sin(2.0 * MathHelper.toRadians(lunarLongitude))
        val term4 = (0.21 / 3600.0) * sin(2.0 * MathHelper.toRadians(ascendingNode))
        return term1 - term2 - term3 + term4
    }

    fun nutationInObliquity(
        solarLongitude: Double,
        lunarLongitude: Double,
        ascendingNode: Double,
    ): Double {
        val term1 = (9.2 / 3600.0) * cos(MathHelper.toRadians(ascendingNode))
        val term2 = (0.57 / 3600.0) * cos(2.0 * MathHelper.toRadians(solarLongitude))
        val term3 = (0.10 / 3600.0) * cos(2.0 * MathHelper.toRadians(lunarLongitude))
        val term4 = (0.09 / 3600.0) * cos(2.0 * MathHelper.toRadians(ascendingNode))
        return term1 + term2 + term3 - term4
    }

    fun altitudeOfCelestialBody(observerLatitude: Double, declination: Double, localHourAngle: Double): Double {
        val term1 = sin(MathHelper.toRadians(observerLatitude)) * sin(MathHelper.toRadians(declination))
        val term2 = cos(MathHelper.toRadians(observerLatitude)) *
            cos(MathHelper.toRadians(declination)) *
            cos(MathHelper.toRadians(localHourAngle))
        return MathHelper.toDegrees(asin(term1 + term2))
    }

    fun approximateTransit(longitude: Double, siderealTime: Double, rightAscension: Double): Double {
        val longitudeWest = longitude * -1.0
        return DoubleUtil.normalizeWithBound((rightAscension + longitudeWest - siderealTime) / 360.0, 1.0)
    }

    fun correctedTransit(
        approximateTransit: Double,
        longitude: Double,
        siderealTime: Double,
        rightAscension: Double,
        previousRightAscension: Double,
        nextRightAscension: Double,
    ): Double {
        val longitudeWest = longitude * -1.0
        val theta = DoubleUtil.unwindAngle(siderealTime + 360.985647 * approximateTransit)
        val alpha = DoubleUtil.unwindAngle(
            interpolateAngles(rightAscension, previousRightAscension, nextRightAscension, approximateTransit),
        )
        val localHourAngle = DoubleUtil.closestAngle(theta - longitudeWest - alpha)
        val deltaTransit = localHourAngle / -360.0
        return (approximateTransit + deltaTransit) * 24.0
    }

    fun correctedHourAngle(
        approximateTransit: Double,
        angle: Double,
        coordinates: Coordinates,
        afterTransit: Boolean,
        siderealTime: Double,
        rightAscension: Double,
        previousRightAscension: Double,
        nextRightAscension: Double,
        declination: Double,
        previousDeclination: Double,
        nextDeclination: Double,
    ): Double {
        val longitudeWest = coordinates.longitude * -1.0
        val term1 = sin(MathHelper.toRadians(angle)) -
            sin(MathHelper.toRadians(coordinates.latitude)) * sin(MathHelper.toRadians(declination))
        val term2 = cos(MathHelper.toRadians(coordinates.latitude)) * cos(MathHelper.toRadians(declination))
        val hourAngle = MathHelper.toDegrees(acos(term1 / term2))
        val transit = if (afterTransit) {
            approximateTransit + hourAngle / 360.0
        } else {
            approximateTransit - hourAngle / 360.0
        }
        val theta = DoubleUtil.unwindAngle(siderealTime + 360.985647 * transit)
        val alpha = DoubleUtil.unwindAngle(
            interpolateAngles(rightAscension, previousRightAscension, nextRightAscension, transit),
        )
        val delta = interpolate(declination, previousDeclination, nextDeclination, transit)
        val localHourAngle = theta - longitudeWest - alpha
        val altitude = altitudeOfCelestialBody(coordinates.latitude, delta, localHourAngle)
        val term3 = altitude - angle
        val term4 = 360.0 *
            cos(MathHelper.toRadians(delta)) *
            cos(MathHelper.toRadians(coordinates.latitude)) *
            sin(MathHelper.toRadians(localHourAngle))
        val deltaTransit = term3 / term4
        return (transit + deltaTransit) * 24.0
    }

    fun interpolate(value: Double, previousValue: Double, nextValue: Double, factor: Double): Double {
        val a = value - previousValue
        val b = nextValue - value
        val c = b - a
        return value + factor / 2.0 * (a + b + factor * c)
    }

    fun interpolateAngles(value: Double, previousValue: Double, nextValue: Double, factor: Double): Double {
        val a = DoubleUtil.unwindAngle(value - previousValue)
        val b = DoubleUtil.unwindAngle(nextValue - value)
        val c = b - a
        return value + factor / 2.0 * (a + b + factor * c)
    }
}

internal data class SolarCoordinates(
    val declination: Double,
    val rightAscension: Double,
    val apparentSiderealTime: Double,
) {
    constructor(julianDay: Double) : this(calculate(julianDay))

    private constructor(values: SolarCoordinatesResult) : this(
        values.declination,
        values.rightAscension,
        values.apparentSiderealTime,
    )

    companion object {
        private fun calculate(julianDay: Double): SolarCoordinatesResult {
            val julianCentury = CalendricalHelper.julianCentury(julianDay)
            val meanSolarLongitude = Astronomical.meanSolarLongitude(julianCentury)
            val meanLunarLongitude = Astronomical.meanLunarLongitude(julianCentury)
            val ascendingNode = Astronomical.ascendingLunarNodeLongitude(julianCentury)
            val apparentSolarLongitude = MathHelper.toRadians(
                Astronomical.apparentSolarLongitude(julianCentury, meanSolarLongitude),
            )
            val meanSiderealTime = Astronomical.meanSiderealTime(julianCentury)
            val nutationLongitude = Astronomical.nutationInLongitude(
                meanSolarLongitude,
                meanLunarLongitude,
                ascendingNode,
            )
            val nutationObliquity = Astronomical.nutationInObliquity(
                meanSolarLongitude,
                meanLunarLongitude,
                ascendingNode,
            )
            val meanObliquity = Astronomical.meanObliquityOfTheEcliptic(julianCentury)
            val apparentObliquity = MathHelper.toRadians(
                Astronomical.apparentObliquityOfTheEcliptic(julianCentury, meanObliquity),
            )
            val values = SolarCoordinateValues(
                apparentSolarLongitude = apparentSolarLongitude,
                apparentObliquity = apparentObliquity,
                meanSiderealTime = meanSiderealTime,
                nutationLongitude = nutationLongitude,
                meanObliquity = meanObliquity,
                nutationObliquity = nutationObliquity,
            )

            val declination = MathHelper.toDegrees(
                asin(sin(values.apparentObliquity) * sin(values.apparentSolarLongitude)),
            )
            val rightAscension = DoubleUtil.unwindAngle(
                MathHelper.toDegrees(
                    atan2(
                        cos(values.apparentObliquity) * sin(values.apparentSolarLongitude),
                        cos(values.apparentSolarLongitude),
                    ),
                ),
            )
            val apparentSiderealTime = values.meanSiderealTime +
                values.nutationLongitude * cos(MathHelper.toRadians(values.meanObliquity + values.nutationObliquity))

            return SolarCoordinatesResult(declination, rightAscension, apparentSiderealTime)
        }
    }
}

private data class SolarCoordinatesResult(
    val declination: Double,
    val rightAscension: Double,
    val apparentSiderealTime: Double,
)

private data class SolarCoordinateValues(
    val apparentSolarLongitude: Double,
    val apparentObliquity: Double,
    val meanSiderealTime: Double,
    val nutationLongitude: Double,
    val meanObliquity: Double,
    val nutationObliquity: Double,
)

internal class SolarTime(
    todayUtc: LocalDateTime,
    private val observer: Coordinates,
) {
    val transit: Double
    val sunrise: Double
    val sunset: Double

    private val solar: SolarCoordinates
    private val previousSolar: SolarCoordinates
    private val nextSolar: SolarCoordinates
    private val approximateTransit: Double

    init {
        val tomorrowUtc = todayUtc.plusDays(1)
        val yesterdayUtc = todayUtc.minusDays(1)
        previousSolar = SolarCoordinates(CalendricalHelper.julianDay(yesterdayUtc))
        solar = SolarCoordinates(CalendricalHelper.julianDay(todayUtc))
        nextSolar = SolarCoordinates(CalendricalHelper.julianDay(tomorrowUtc))
        approximateTransit = Astronomical.approximateTransit(
            observer.longitude,
            solar.apparentSiderealTime,
            solar.rightAscension,
        )

        val solarAltitude = -50.0 / 60.0
        transit = Astronomical.correctedTransit(
            approximateTransit,
            observer.longitude,
            solar.apparentSiderealTime,
            solar.rightAscension,
            previousSolar.rightAscension,
            nextSolar.rightAscension,
        )
        sunrise = Astronomical.correctedHourAngle(
            approximateTransit,
            solarAltitude,
            observer,
            false,
            solar.apparentSiderealTime,
            solar.rightAscension,
            previousSolar.rightAscension,
            nextSolar.rightAscension,
            solar.declination,
            previousSolar.declination,
            nextSolar.declination,
        )
        sunset = Astronomical.correctedHourAngle(
            approximateTransit,
            solarAltitude,
            observer,
            true,
            solar.apparentSiderealTime,
            solar.rightAscension,
            previousSolar.rightAscension,
            nextSolar.rightAscension,
            solar.declination,
            previousSolar.declination,
            nextSolar.declination,
        )
    }

    fun hourAngle(angle: Double, afterTransit: Boolean): Double =
        Astronomical.correctedHourAngle(
            approximateTransit,
            angle,
            observer,
            afterTransit,
            solar.apparentSiderealTime,
            solar.rightAscension,
            previousSolar.rightAscension,
            nextSolar.rightAscension,
            solar.declination,
            previousSolar.declination,
            nextSolar.declination,
        )

    fun afternoon(shadowFactor: Double): Double {
        val tangent = abs(observer.latitude - solar.declination)
        val inverse = shadowFactor + tan(MathHelper.toRadians(tangent))
        val angle = MathHelper.toDegrees(atan(1.0 / inverse))
        return hourAngle(angle, true)
    }
}

internal object QiblaUtil {
    private val makkah = Coordinates(21.4225241, 39.8261818)

    fun calculateQiblaDirection(coordinates: Coordinates): Double {
        val longitudeDelta = MathHelper.toRadians(makkah.longitude) - MathHelper.toRadians(coordinates.longitude)
        val latitudeRadians = MathHelper.toRadians(coordinates.latitude)
        val term1 = sin(longitudeDelta)
        val term2 = cos(latitudeRadians) * tan(MathHelper.toRadians(makkah.latitude))
        val term3 = sin(latitudeRadians) * cos(longitudeDelta)
        val angle = atan2(term1, term2 - term3)
        return DoubleUtil.unwindAngle(MathHelper.toDegrees(angle))
    }
}

internal fun LocalDateTime.epochMillisUtc(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()
