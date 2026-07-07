package ru.admiral.praytimes.ui

import android.content.Context
import ru.admiral.praytimes.R
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Madhab
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.holiday.HijriDate
import ru.admiral.praytimes.holiday.HolidayOccurrence

object UiText {
    fun holidayTitle(context: Context, holiday: HolidayOccurrence): String =
        context.getString(holiday.holiday.titleRes)

    fun formatHijri(context: Context, hijriDate: HijriDate): String {
        val months = context.resources.getStringArray(R.array.hijri_months)
        val monthName = months.getOrElse(hijriDate.month - 1) { hijriDate.month.toString() }
        return context.getString(R.string.hijri_date, hijriDate.day, monthName, hijriDate.year)
    }

    fun prayerLabel(context: Context, prayer: Prayer, isRamadan: Boolean, isJumuah: Boolean = false): String = when (prayer) {
        Prayer.NONE -> context.getString(R.string.prayer_none)
        Prayer.FAJR -> context.getString(if (isRamadan) R.string.prayer_fajr_ramadan else R.string.prayer_fajr)
        Prayer.SUNRISE -> context.getString(R.string.prayer_sunrise)
        Prayer.DHUHR -> context.getString(if (isJumuah) R.string.prayer_dhuhr_jumuah else R.string.prayer_dhuhr)
        Prayer.ASR -> context.getString(R.string.prayer_asr)
        Prayer.MAGHRIB -> context.getString(if (isRamadan) R.string.prayer_maghrib_ramadan else R.string.prayer_maghrib)
        Prayer.ISHA -> context.getString(if (isRamadan) R.string.prayer_isha_ramadan else R.string.prayer_isha)
    }

    fun methodLabel(context: Context, method: CalculationMethod): String = when (method) {
        CalculationMethod.MUSLIM_WORLD_LEAGUE -> context.getString(R.string.method_muslim_world_league)
        CalculationMethod.EGYPTIAN -> context.getString(R.string.method_egyptian)
        CalculationMethod.KARACHI -> context.getString(R.string.method_karachi)
        CalculationMethod.UMM_AL_QURA -> context.getString(R.string.method_umm_al_qura)
        CalculationMethod.DUBAI -> context.getString(R.string.method_dubai)
        CalculationMethod.MOON_SIGHTING_COMMITTEE -> context.getString(R.string.method_moon_sighting)
        CalculationMethod.NORTH_AMERICA -> context.getString(R.string.method_north_america)
        CalculationMethod.KUWAIT -> context.getString(R.string.method_kuwait)
        CalculationMethod.QATAR -> context.getString(R.string.method_qatar)
        CalculationMethod.SINGAPORE -> context.getString(R.string.method_singapore)
        CalculationMethod.TURKEY_DIYANET -> context.getString(R.string.method_turkey_diyanet)
        CalculationMethod.RUSSIA -> context.getString(R.string.method_russia)
        CalculationMethod.FRANCE_UOIF -> context.getString(R.string.method_france_uoif)
        CalculationMethod.JAFARI -> context.getString(R.string.method_jafari)
        CalculationMethod.TEHRAN -> context.getString(R.string.method_tehran)
        CalculationMethod.INDONESIA -> context.getString(R.string.method_indonesia)
        CalculationMethod.MALAYSIA -> context.getString(R.string.method_malaysia)
        CalculationMethod.MOROCCO -> context.getString(R.string.method_morocco)
        CalculationMethod.ALGERIA -> context.getString(R.string.method_algeria)
        CalculationMethod.TUNISIA -> context.getString(R.string.method_tunisia)
        CalculationMethod.OTHER -> method.name
    }

    fun madhabLabel(context: Context, madhab: Madhab): String = when (madhab) {
        Madhab.SHAFI -> context.getString(R.string.madhab_shafi)
        Madhab.HANAFI -> context.getString(R.string.madhab_hanafi)
    }

    fun asrMethodLabel(context: Context, asrMethod: AsrMethod): String = when (asrMethod) {
        AsrMethod.STANDARD -> context.getString(R.string.asr_method_standard)
        AsrMethod.HANAFI -> context.getString(R.string.asr_method_hanafi)
        AsrMethod.SHADOW_1_25 -> context.getString(R.string.asr_method_shadow_125)
        AsrMethod.SHADOW_1_50 -> context.getString(R.string.asr_method_shadow_150)
        AsrMethod.SHADOW_1_75 -> context.getString(R.string.asr_method_shadow_175)
        AsrMethod.SHADOW_2_50 -> context.getString(R.string.asr_method_shadow_250)
        AsrMethod.SHADOW_3_00 -> context.getString(R.string.asr_method_shadow_300)
    }
}
