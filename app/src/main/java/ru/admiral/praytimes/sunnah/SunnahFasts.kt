package ru.admiral.praytimes.sunnah

import ru.admiral.praytimes.R
import ru.admiral.praytimes.holiday.HijriDate
import java.time.DayOfWeek
import java.time.LocalDate

enum class SunnahFast(
    val titleRes: Int,
    val noteRes: Int,
    val iconRes: Int,
) {
    MONDAY_THURSDAY(R.string.sunnah_fast_monday_thursday_title, R.string.sunnah_fast_monday_thursday_note, R.drawable.ic_moon),
    WHITE_DAYS(R.string.sunnah_fast_white_days_title, R.string.sunnah_fast_white_days_note, R.drawable.ic_moon),
    ARAFAH(R.string.sunnah_fast_arafah_title, R.string.sunnah_fast_arafah_note, R.drawable.ic_star),
    TASUA(R.string.sunnah_fast_tasua_title, R.string.sunnah_fast_tasua_note, R.drawable.ic_moon),
    ASHURA(R.string.sunnah_fast_ashura_title, R.string.sunnah_fast_ashura_note, R.drawable.ic_star),
    SHAWWAL_SIX(R.string.sunnah_fast_shawwal_six_title, R.string.sunnah_fast_shawwal_six_note, R.drawable.ic_moon),
}

data class SunnahFastDay(
    val fast: SunnahFast,
)

object SunnahFasts {
    fun calculate(date: LocalDate, hijriDate: HijriDate): List<SunnahFastDay> {
        if (hijriDate.month == RAMADAN_MONTH || isForbiddenFastDay(hijriDate)) {
            return emptyList()
        }

        return buildList {
            if (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) {
                add(SunnahFastDay(SunnahFast.MONDAY_THURSDAY))
            }
            if (hijriDate.day in WHITE_DAYS) {
                add(SunnahFastDay(SunnahFast.WHITE_DAYS))
            }
            if (hijriDate.month == DHU_AL_HIJJAH_MONTH && hijriDate.day == ARAFAH_DAY) {
                add(SunnahFastDay(SunnahFast.ARAFAH))
            }
            if (hijriDate.month == MUHARRAM_MONTH && hijriDate.day == TASUA_DAY) {
                add(SunnahFastDay(SunnahFast.TASUA))
            }
            if (hijriDate.month == MUHARRAM_MONTH && hijriDate.day == ASHURA_DAY) {
                add(SunnahFastDay(SunnahFast.ASHURA))
            }
            if (hijriDate.month == SHAWWAL_MONTH && hijriDate.day > EID_AL_FITR_DAY) {
                add(SunnahFastDay(SunnahFast.SHAWWAL_SIX))
            }
        }.distinctBy { it.fast }
    }

    private fun isForbiddenFastDay(hijriDate: HijriDate): Boolean =
        (hijriDate.month == SHAWWAL_MONTH && hijriDate.day == EID_AL_FITR_DAY) ||
            (hijriDate.month == DHU_AL_HIJJAH_MONTH && hijriDate.day in EID_AL_ADHA_DAY..TASHREEQ_END_DAY)

    private const val MUHARRAM_MONTH = 1
    private const val RAMADAN_MONTH = 9
    private const val SHAWWAL_MONTH = 10
    private const val DHU_AL_HIJJAH_MONTH = 12
    private const val TASUA_DAY = 9
    private const val ASHURA_DAY = 10
    private const val EID_AL_FITR_DAY = 1
    private const val ARAFAH_DAY = 9
    private const val EID_AL_ADHA_DAY = 10
    private const val TASHREEQ_END_DAY = 13
    private val WHITE_DAYS = 13..15
}
