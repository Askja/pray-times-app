package ru.admiral.praytimes.holiday

import java.time.LocalDate

data class HolidayOccurrence(
    val date: LocalDate,
    val hijriDate: HijriDate,
    val holiday: IslamicHoliday,
)

object HolidayCalendar {
    fun holidaysOn(date: LocalDate, hijriDayAdjustment: Int = 0): List<HolidayOccurrence> {
        val hijri = IslamicCalendar.hijriDate(date, hijriDayAdjustment)
        return IslamicHoliday.entries
            .filter { it.month == hijri.month && it.day == hijri.day }
            .map { HolidayOccurrence(date, hijri, it) }
    }

    fun holidaysForGregorianYear(year: Int, hijriDayAdjustment: Int = 0): List<HolidayOccurrence> {
        val start = LocalDate.of(year, 1, 1)
        val days = if (start.isLeapYear) 366L else 365L
        return (0 until days)
            .asSequence()
            .map { start.plusDays(it) }
            .flatMap { holidaysOn(it, hijriDayAdjustment).asSequence() }
            .sortedBy { it.date }
            .toList()
    }

    fun nextHolidayAfter(date: LocalDate, hijriDayAdjustment: Int = 0): HolidayOccurrence? =
        (1L..LOOKAHEAD_DAYS)
            .asSequence()
            .map { date.plusDays(it) }
            .flatMap { holidaysOn(it, hijriDayAdjustment).asSequence() }
            .firstOrNull()

    private const val LOOKAHEAD_DAYS = 370L
}
