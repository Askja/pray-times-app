package ru.admiral.praytimes.sunnah

import ru.admiral.praytimes.R
import ru.admiral.praytimes.adhan.PrayerTimes
import ru.admiral.praytimes.holiday.HijriDate
import java.time.Duration
import java.time.ZonedDateTime

enum class SunnahPrayer(
    val titleRes: Int,
    val noteRes: Int,
    val iconRes: Int,
) {
    FAJR_SUNNAH(R.string.sunnah_prayer_fajr_title, R.string.sunnah_prayer_fajr_note, R.drawable.ic_star),
    ISHRAQ(R.string.sunnah_prayer_ishraq_title, R.string.sunnah_prayer_ishraq_note, R.drawable.ic_sun),
    DUHA(R.string.sunnah_prayer_duha_title, R.string.sunnah_prayer_duha_note, R.drawable.ic_sun),
    AWWABIN(R.string.sunnah_prayer_awwabin_title, R.string.sunnah_prayer_awwabin_note, R.drawable.ic_moon),
    WITR(R.string.sunnah_prayer_witr_title, R.string.sunnah_prayer_witr_note, R.drawable.ic_moon),
    TAHAJJUD(R.string.sunnah_prayer_tahajjud_title, R.string.sunnah_prayer_tahajjud_note, R.drawable.ic_moon),
    TARAWIH(R.string.sunnah_prayer_tarawih_title, R.string.sunnah_prayer_tarawih_note, R.drawable.ic_moon),
    EID(R.string.sunnah_prayer_eid_title, R.string.sunnah_prayer_eid_note, R.drawable.ic_star),
}

data class SunnahPrayerWindow(
    val prayer: SunnahPrayer,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
) {
    val isValid: Boolean
        get() = start.isBefore(end)
}

object SunnahPrayerTimes {
    fun calculate(
        prayerTimes: PrayerTimes,
        previousPrayerTimes: PrayerTimes?,
        nextPrayerTimes: PrayerTimes?,
        hijriDate: HijriDate,
        isToday: Boolean,
        now: ZonedDateTime,
    ): List<SunnahPrayerWindow> {
        val safeSunrise = prayerTimes.sunrise.plusMinutes(SUNRISE_GUARD_MINUTES)
        val duhaEnd = prayerTimes.dhuhr.minusMinutes(ZAWAL_GUARD_MINUTES)
        val currentNightBeforeFajr = isToday && now.isBefore(prayerTimes.fajr) && previousPrayerTimes != null
        val nightTimes = if (currentNightBeforeFajr) {
            val previous = requireNotNull(previousPrayerTimes)
            NightTimes(
                maghrib = previous.maghrib,
                isha = previous.isha,
                fajr = prayerTimes.fajr,
            )
        } else {
            nextPrayerTimes?.let {
                NightTimes(
                    maghrib = prayerTimes.maghrib,
                    isha = prayerTimes.isha,
                    fajr = it.fajr,
                )
            }
        }

        return buildList {
            addWindow(SunnahPrayer.FAJR_SUNNAH, prayerTimes.fajr, prayerTimes.sunrise)
            addWindow(SunnahPrayer.ISHRAQ, safeSunrise, minOfTime(safeSunrise.plusMinutes(ISHRAQ_WINDOW_MINUTES), duhaEnd))
            addWindow(SunnahPrayer.DUHA, safeSunrise, duhaEnd)
            addWindow(SunnahPrayer.AWWABIN, prayerTimes.maghrib, prayerTimes.isha)
            addEidWindowIfNeeded(hijriDate, safeSunrise, duhaEnd)

            if (nightTimes != null) {
                addWindow(SunnahPrayer.WITR, nightTimes.isha, nightTimes.fajr)
                addWindow(SunnahPrayer.TAHAJJUD, lastThirdStart(nightTimes.maghrib, nightTimes.fajr), nightTimes.fajr)
                if (hijriDate.month == RAMADAN_MONTH) {
                    addWindow(SunnahPrayer.TARAWIH, nightTimes.isha, nightTimes.fajr)
                }
            }
        }
            .filter(SunnahPrayerWindow::isValid)
            .distinctBy { it.prayer }
            .sortedBy { it.start.toInstant() }
    }

    private fun MutableList<SunnahPrayerWindow>.addWindow(
        prayer: SunnahPrayer,
        start: ZonedDateTime,
        end: ZonedDateTime,
    ) {
        add(SunnahPrayerWindow(prayer, start, end))
    }

    private fun MutableList<SunnahPrayerWindow>.addEidWindowIfNeeded(
        hijriDate: HijriDate,
        start: ZonedDateTime,
        end: ZonedDateTime,
    ) {
        if (
            (hijriDate.month == SHAWWAL_MONTH && hijriDate.day == EID_AL_FITR_DAY) ||
            (hijriDate.month == DHU_AL_HIJJAH_MONTH && hijriDate.day == EID_AL_ADHA_DAY)
        ) {
            addWindow(SunnahPrayer.EID, start, end)
        }
    }

    private fun lastThirdStart(nightStart: ZonedDateTime, fajr: ZonedDateTime): ZonedDateTime {
        val night = Duration.between(nightStart, fajr)
        return nightStart.plus(night.multipliedBy(2).dividedBy(3))
    }

    private fun minOfTime(left: ZonedDateTime, right: ZonedDateTime): ZonedDateTime =
        if (left.isBefore(right)) left else right

    private data class NightTimes(
        val maghrib: ZonedDateTime,
        val isha: ZonedDateTime,
        val fajr: ZonedDateTime,
    )

    private const val SUNRISE_GUARD_MINUTES = 20L
    private const val ZAWAL_GUARD_MINUTES = 10L
    private const val ISHRAQ_WINDOW_MINUTES = 45L
    private const val RAMADAN_MONTH = 9
    private const val SHAWWAL_MONTH = 10
    private const val DHU_AL_HIJJAH_MONTH = 12
    private const val EID_AL_FITR_DAY = 1
    private const val EID_AL_ADHA_DAY = 10
}
