package ru.admiral.praytimes.adhan

import java.time.LocalDate

data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
    }
}

enum class Prayer {
    NONE,
    FAJR,
    SUNRISE,
    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
}

enum class Madhab(internal val shadowLength: ShadowLength) {
    SHAFI(ShadowLength.SINGLE),
    HANAFI(ShadowLength.DOUBLE),
}

enum class AsrMethod(val shadowFactor: Double) {
    STANDARD(1.0),
    HANAFI(2.0),
    SHADOW_1_25(1.25),
    SHADOW_1_50(1.5),
    SHADOW_1_75(1.75),
    SHADOW_2_50(2.5),
    SHADOW_3_00(3.0),
}

enum class HighLatitudeRule {
    MIDDLE_OF_THE_NIGHT,
    SEVENTH_OF_THE_NIGHT,
    TWILIGHT_ANGLE,
}

internal enum class ShadowLength(val value: Double) {
    SINGLE(1.0),
    DOUBLE(2.0),
}

data class PrayerAdjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
)

data class NightPortions(
    val fajr: Double,
    val isha: Double,
)

data class DateComponents(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    companion object {
        fun from(date: LocalDate): DateComponents = DateComponents(date.year, date.monthValue, date.dayOfMonth)
    }
}

data class CalculationParameters(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val method: CalculationMethod = CalculationMethod.OTHER,
    val ishaInterval: Int = 0,
    val maghribAngle: Double? = null,
    val madhab: Madhab = Madhab.SHAFI,
    val asrMethod: AsrMethod = AsrMethod.STANDARD,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
    val adjustments: PrayerAdjustments = PrayerAdjustments(),
    val methodAdjustments: PrayerAdjustments = PrayerAdjustments(),
) {
    fun withMethodAdjustments(adjustments: PrayerAdjustments): CalculationParameters =
        copy(methodAdjustments = adjustments)

    fun withMadhab(madhab: Madhab): CalculationParameters = copy(
        madhab = madhab,
        asrMethod = when (madhab) {
            Madhab.SHAFI -> AsrMethod.STANDARD
            Madhab.HANAFI -> AsrMethod.HANAFI
        },
    )

    fun withAsrMethod(asrMethod: AsrMethod): CalculationParameters = copy(
        asrMethod = asrMethod,
        madhab = if (asrMethod == AsrMethod.HANAFI) Madhab.HANAFI else Madhab.SHAFI,
    )

    fun nightPortions(): NightPortions = when (highLatitudeRule) {
        HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> NightPortions(1.0 / 2.0, 1.0 / 2.0)
        HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> NightPortions(1.0 / 7.0, 1.0 / 7.0)
        HighLatitudeRule.TWILIGHT_ANGLE -> NightPortions(fajrAngle / 60.0, ishaAngle / 60.0)
    }
}

enum class CalculationMethod {
    MUSLIM_WORLD_LEAGUE,
    EGYPTIAN,
    KARACHI,
    UMM_AL_QURA,
    DUBAI,
    MOON_SIGHTING_COMMITTEE,
    NORTH_AMERICA,
    KUWAIT,
    QATAR,
    SINGAPORE,
    TURKEY_DIYANET,
    RUSSIA,
    FRANCE_UOIF,
    JAFARI,
    TEHRAN,
    INDONESIA,
    MALAYSIA,
    MOROCCO,
    ALGERIA,
    TUNISIA,
    OTHER;

    fun parameters(): CalculationParameters = when (this) {
        MUSLIM_WORLD_LEAGUE -> CalculationParameters(18.0, 17.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 1))

        EGYPTIAN -> CalculationParameters(20.0, 18.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 1))

        KARACHI -> CalculationParameters(18.0, 18.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 1))

        UMM_AL_QURA -> CalculationParameters(
            fajrAngle = 18.5,
            ishaAngle = 0.0,
            method = this,
            ishaInterval = 90,
        )

        DUBAI -> CalculationParameters(18.2, 18.2, this)
            .withMethodAdjustments(
                PrayerAdjustments(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3),
            )

        MOON_SIGHTING_COMMITTEE -> CalculationParameters(18.0, 18.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 5, maghrib = 3))

        NORTH_AMERICA -> CalculationParameters(15.0, 15.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 1))

        KUWAIT -> CalculationParameters(18.0, 17.5, this)

        QATAR -> CalculationParameters(
            fajrAngle = 18.0,
            ishaAngle = 0.0,
            method = this,
            ishaInterval = 90,
        )

        SINGAPORE -> CalculationParameters(20.0, 18.0, this)
            .withMethodAdjustments(PrayerAdjustments(dhuhr = 1))

        TURKEY_DIYANET -> CalculationParameters(18.0, 17.0, this)
            .withMethodAdjustments(
                PrayerAdjustments(
                    sunrise = -7,
                    dhuhr = 5,
                    asr = 4,
                    maghrib = 7,
                ),
            )

        RUSSIA -> CalculationParameters(
            fajrAngle = 16.0,
            ishaAngle = 15.0,
            method = this,
            highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE,
        )

        FRANCE_UOIF -> CalculationParameters(12.0, 12.0, this)

        JAFARI -> CalculationParameters(
            fajrAngle = 16.0,
            ishaAngle = 14.0,
            method = this,
            maghribAngle = 4.0,
        )

        TEHRAN -> CalculationParameters(
            fajrAngle = 17.7,
            ishaAngle = 14.0,
            method = this,
            maghribAngle = 4.5,
        )

        INDONESIA -> CalculationParameters(20.0, 18.0, this)

        MALAYSIA -> CalculationParameters(20.0, 18.0, this)

        MOROCCO -> CalculationParameters(19.0, 17.0, this)

        ALGERIA -> CalculationParameters(18.0, 17.0, this)

        TUNISIA -> CalculationParameters(18.0, 18.0, this)

        OTHER -> CalculationParameters(0.0, 0.0, this)
    }
}
