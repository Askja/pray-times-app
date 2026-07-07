package ru.admiral.praytimes.holiday

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

object IslamicCalendar {
    private const val ISLAMIC_EPOCH = 1948439.5

    fun hijriDate(gregorian: LocalDate, dayAdjustment: Int = 0): HijriDate {
        val adjustedGregorian = gregorian.plusDays(dayAdjustment.toLong())
        val julianDay = floor(gregorianToJulianDay(adjustedGregorian)) + 0.5
        val year = floor((30.0 * (julianDay - ISLAMIC_EPOCH) + 10646.0) / 10631.0).toInt()
        val month = min(
            12,
            ceil((julianDay - (29.0 + islamicToJulianDay(year, 1, 1))) / 29.5).toInt() + 1,
        )
        val day = (julianDay - islamicToJulianDay(year, month, 1) + 1.0).toInt()
        return HijriDate(year, month, day)
    }

    fun islamicToJulianDay(year: Int, month: Int, day: Int): Double =
        day +
            ceil(29.5 * (month - 1)) +
            (year - 1) * 354 +
            floor((3 + 11 * year) / 30.0) +
            ISLAMIC_EPOCH -
            1

    private fun gregorianToJulianDay(date: LocalDate): Double {
        val y = if (date.monthValue > 2) date.year else date.year - 1
        val m = if (date.monthValue > 2) date.monthValue else date.monthValue + 12
        val a = y / 100
        val b = 2 - a + a / 4
        val i0 = floor(365.25 * (y + 4716)).toInt()
        val i1 = floor(30.6001 * (m + 1)).toInt()
        return i0 + i1 + date.dayOfMonth + b - 1524.5
    }
}
