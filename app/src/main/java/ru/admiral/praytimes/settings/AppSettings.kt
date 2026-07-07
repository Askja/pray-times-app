package ru.admiral.praytimes.settings

import android.content.Context
import android.content.res.Configuration
import ru.admiral.praytimes.adhan.AsrMethod
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Prayer
import ru.admiral.praytimes.adhan.PrayerAdjustments
import org.json.JSONObject
import java.util.Locale

enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class HomeBlock {
    RAMADAN,
    PRAYER,
    SUNNAH,
    QIBLA,
    MOSQUES,
    HOLIDAYS,
}

enum class AdhanSound {
    BUILT_IN,
    SYSTEM,
    SILENT,
}

object AppSettings {
    val supportedLanguageTags = listOf(
        "",
        "en",
        "ru",
        "ar",
        "az",
        "bn",
        "de",
        "es",
        "fa",
        "fr",
        "ha",
        "hi",
        "id",
        "it",
        "ja",
        "kk",
        "ko",
        "ms",
        "nl",
        "pt",
        "sq",
        "sw",
        "tr",
        "ur",
        "uz",
        "zh",
    )
    val uiScales = listOf(0.90f, 1.00f, 1.10f, 1.20f, 1.30f)

    fun languageTag(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "").orEmpty()

    fun colorMode(context: Context): ColorMode =
        enumValueOrDefault(prefs(context).getString(KEY_COLOR_MODE, null), ColorMode.SYSTEM)

    fun uiScale(context: Context): Float =
        prefs(context).getFloat(KEY_UI_SCALE, DEFAULT_UI_SCALE)

    fun asrMethod(context: Context): AsrMethod =
        enumValueOrDefault(prefs(context).getString(KEY_ASR_METHOD, null), AsrMethod.STANDARD)

    fun automaticCalculationMethod(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOMATIC_CALCULATION_METHOD, true)

    fun calculationMethod(context: Context): CalculationMethod =
        enumValueOrDefault(
            prefs(context).getString(KEY_CALCULATION_METHOD, null),
            CalculationMethod.TURKEY_DIYANET,
        )

    fun notifyAtPrayerStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_AT_PRAYER_START, false)

    fun notifyBeforePrayerStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_BEFORE_PRAYER_START, false)

    fun notifyBeforePrayerEnd(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_BEFORE_PRAYER_END, false)

    fun playAdhanAtPrayerStart(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PLAY_ADHAN_AT_PRAYER_START, false)

    fun adhanSound(context: Context): AdhanSound =
        enumValueOrDefault(prefs(context).getString(KEY_ADHAN_SOUND, null), AdhanSound.BUILT_IN)

    fun notifySunnahPrayers(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_SUNNAH_PRAYERS, false)

    fun showJumuahPrayer(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_JUMUAH_PRAYER, true)

    fun ramadanMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RAMADAN_MODE, true)

    fun holidayBackgrounds(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HOLIDAY_BACKGROUNDS, false)

    fun notifyRamadanImsak(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_RAMADAN_IMSAK, false)

    fun hijriDayAdjustment(context: Context): Int =
        prefs(context).getInt(KEY_HIJRI_DAY_ADJUSTMENT, 0).coerceIn(MIN_HIJRI_ADJUSTMENT, MAX_HIJRI_ADJUSTMENT)

    fun autoSwitchLocation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_SWITCH_LOCATION, false)

    fun suggestLocationSwitch(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUGGEST_LOCATION_SWITCH, true)

    fun homeBlockOrder(context: Context): List<HomeBlock> {
        val saved = prefs(context).getString(KEY_HOME_BLOCK_ORDER, null)
            ?.split(',')
            ?.mapNotNull { value -> runCatching { HomeBlock.valueOf(value) }.getOrNull() }
            .orEmpty()
        return (saved + defaultHomeBlockOrder).distinct()
    }

    fun vibratePrayerNotifications(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE_PRAYER_NOTIFICATIONS, true)

    fun prayerNotificationEnabled(context: Context, prayer: Prayer): Boolean =
        prefs(context).getBoolean(notificationKey(prayer), true)

    fun prayerAdhanEnabled(context: Context, prayer: Prayer): Boolean =
        prefs(context).getBoolean(adhanKey(prayer), true)

    fun globalPrayerAdjustments(context: Context): PrayerAdjustments {
        val prefs = prefs(context)
        return PrayerAdjustments(
            fajr = prefs.getInt(KEY_ADJUSTMENT_FAJR, 0),
            sunrise = prefs.getInt(KEY_ADJUSTMENT_SUNRISE, 0),
            dhuhr = prefs.getInt(KEY_ADJUSTMENT_DHUHR, 0),
            asr = prefs.getInt(KEY_ADJUSTMENT_ASR, 0),
            maghrib = prefs.getInt(KEY_ADJUSTMENT_MAGHRIB, 0),
            isha = prefs.getInt(KEY_ADJUSTMENT_ISHA, 0),
        )
    }

    fun notificationsEnabled(context: Context): Boolean =
        notifyAtPrayerStart(context) ||
            notifyBeforePrayerStart(context) ||
            notifyBeforePrayerEnd(context) ||
            playAdhanAtPrayerStart(context) ||
            notifySunnahPrayers(context) ||
            notifyRamadanImsak(context)

    fun revision(context: Context): Int =
        prefs(context).getInt(KEY_REVISION, 0)

    fun selectedLocationId(context: Context): Long? =
        prefs(context).getLong(KEY_SELECTED_LOCATION_ID, NO_LOCATION_ID).takeIf { it != NO_LOCATION_ID }

    fun saveSelectedLocationId(context: Context, locationId: Long?): Boolean {
        val prefs = prefs(context)
        val editor = prefs.edit()
        if (locationId == null) {
            editor.remove(KEY_SELECTED_LOCATION_ID)
        } else {
            editor.putLong(KEY_SELECTED_LOCATION_ID, locationId)
        }
        return editor
            .putInt(KEY_REVISION, prefs.getInt(KEY_REVISION, 0) + 1)
            .commit()
    }

    fun save(
        context: Context,
        languageTag: String,
        colorMode: ColorMode,
        uiScale: Float,
        automaticCalculationMethod: Boolean,
        calculationMethod: CalculationMethod,
        asrMethod: AsrMethod,
        notifyAtPrayerStart: Boolean,
        notifyBeforePrayerStart: Boolean,
        notifyBeforePrayerEnd: Boolean,
        playAdhanAtPrayerStart: Boolean,
        adhanSound: AdhanSound,
        notifySunnahPrayers: Boolean,
        showJumuahPrayer: Boolean,
        ramadanMode: Boolean,
        holidayBackgrounds: Boolean,
        notifyRamadanImsak: Boolean,
        hijriDayAdjustment: Int,
        autoSwitchLocation: Boolean,
        suggestLocationSwitch: Boolean,
        vibratePrayerNotifications: Boolean,
        homeBlockOrder: List<HomeBlock>,
        notificationEnabledPrayers: Set<Prayer>,
        adhanEnabledPrayers: Set<Prayer>,
        globalPrayerAdjustments: PrayerAdjustments,
    ): Boolean {
        val prefs = prefs(context)
        val editor = prefs.edit()
            .putString(KEY_LANGUAGE, languageTag)
            .putString(KEY_COLOR_MODE, colorMode.name)
            .putFloat(KEY_UI_SCALE, uiScale)
            .putBoolean(KEY_AUTOMATIC_CALCULATION_METHOD, automaticCalculationMethod)
            .putString(KEY_CALCULATION_METHOD, calculationMethod.name)
            .putString(KEY_ASR_METHOD, asrMethod.name)
            .putBoolean(KEY_NOTIFY_AT_PRAYER_START, notifyAtPrayerStart)
            .putBoolean(KEY_NOTIFY_BEFORE_PRAYER_START, notifyBeforePrayerStart)
            .putBoolean(KEY_NOTIFY_BEFORE_PRAYER_END, notifyBeforePrayerEnd)
            .putBoolean(KEY_PLAY_ADHAN_AT_PRAYER_START, playAdhanAtPrayerStart)
            .putString(KEY_ADHAN_SOUND, adhanSound.name)
            .putBoolean(KEY_NOTIFY_SUNNAH_PRAYERS, notifySunnahPrayers)
            .putBoolean(KEY_SHOW_JUMUAH_PRAYER, showJumuahPrayer)
            .putBoolean(KEY_RAMADAN_MODE, ramadanMode)
            .putBoolean(KEY_HOLIDAY_BACKGROUNDS, holidayBackgrounds)
            .putBoolean(KEY_NOTIFY_RAMADAN_IMSAK, notifyRamadanImsak)
            .putInt(KEY_HIJRI_DAY_ADJUSTMENT, hijriDayAdjustment.coerceIn(MIN_HIJRI_ADJUSTMENT, MAX_HIJRI_ADJUSTMENT))
            .putBoolean(KEY_AUTO_SWITCH_LOCATION, autoSwitchLocation)
            .putBoolean(KEY_SUGGEST_LOCATION_SWITCH, suggestLocationSwitch)
            .putBoolean(KEY_VIBRATE_PRAYER_NOTIFICATIONS, vibratePrayerNotifications)
            .putString(KEY_HOME_BLOCK_ORDER, homeBlockOrder.distinct().joinToString(",") { it.name })
            .putInt(KEY_ADJUSTMENT_FAJR, globalPrayerAdjustments.fajr)
            .putInt(KEY_ADJUSTMENT_SUNRISE, globalPrayerAdjustments.sunrise)
            .putInt(KEY_ADJUSTMENT_DHUHR, globalPrayerAdjustments.dhuhr)
            .putInt(KEY_ADJUSTMENT_ASR, globalPrayerAdjustments.asr)
            .putInt(KEY_ADJUSTMENT_MAGHRIB, globalPrayerAdjustments.maghrib)
            .putInt(KEY_ADJUSTMENT_ISHA, globalPrayerAdjustments.isha)
            .putInt(KEY_REVISION, prefs.getInt(KEY_REVISION, 0) + 1)
        notificationProfilePrayers.forEach { prayer ->
            editor.putBoolean(notificationKey(prayer), prayer in notificationEnabledPrayers)
            editor.putBoolean(adhanKey(prayer), prayer in adhanEnabledPrayers)
        }
        return editor.commit()
    }

    fun exportPreferences(context: Context): JSONObject =
        JSONObject().also { output ->
            prefs(context).all.forEach { (key, value) ->
                val item = JSONObject()
                when (value) {
                    is Boolean -> item.put("type", "boolean").put("value", value)
                    is Float -> item.put("type", "float").put("value", value.toDouble())
                    is Int -> item.put("type", "int").put("value", value)
                    is Long -> item.put("type", "long").put("value", value)
                    is String -> item.put("type", "string").put("value", value)
                    else -> null
                }?.let { output.put(key, it) }
            }
        }

    fun importPreferences(context: Context, input: JSONObject): Boolean {
        val editor = prefs(context).edit().clear()
        input.keys().forEach { key ->
            val item = input.optJSONObject(key) ?: return@forEach
            when (item.optString("type")) {
                "boolean" -> editor.putBoolean(key, item.optBoolean("value"))
                "float" -> editor.putFloat(key, item.optDouble("value", DEFAULT_UI_SCALE.toDouble()).toFloat())
                "int" -> editor.putInt(key, item.optInt("value"))
                "long" -> editor.putLong(key, item.optLong("value"))
                "string" -> editor.putString(key, item.optString("value"))
            }
        }
        return editor
            .putInt(KEY_REVISION, input.optJSONObject(KEY_REVISION)?.optInt("value", 0)?.plus(1) ?: 1)
            .commit()
    }

    fun localizedContext(base: Context): Context {
        val configuration = Configuration(base.resources.configuration)
        val tag = languageTag(base)
        val locale = if (tag.isNotBlank()) {
            Locale.forLanguageTag(tag).also { locale ->
                configuration.setLocale(locale)
            }
        } else {
            configuration.locales[0] ?: Locale.getDefault()
        }
        Locale.setDefault(locale)
        configuration.setLayoutDirection(locale)
        configuration.fontScale = uiScale(base)
        configuration.uiMode = when (colorMode(base)) {
            ColorMode.SYSTEM -> configuration.uiMode
            ColorMode.LIGHT -> configuration.uiMode.withNightMode(Configuration.UI_MODE_NIGHT_NO)
            ColorMode.DARK -> configuration.uiMode.withNightMode(Configuration.UI_MODE_NIGHT_YES)
        }
        return base.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun Int.withNightMode(mode: Int): Int =
        (this and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode

    private fun notificationKey(prayer: Prayer): String = "${KEY_PRAYER_NOTIFICATION_PREFIX}${prayer.name}"

    private fun adhanKey(prayer: Prayer): String = "${KEY_PRAYER_ADHAN_PREFIX}${prayer.name}"

    val notificationProfilePrayers = listOf(
        Prayer.FAJR,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA,
    )

    val defaultHomeBlockOrder = listOf(
        HomeBlock.RAMADAN,
        HomeBlock.PRAYER,
        HomeBlock.SUNNAH,
        HomeBlock.QIBLA,
        HomeBlock.MOSQUES,
        HomeBlock.HOLIDAYS,
    )

    private const val PREFS_NAME = "pray_times_settings"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_COLOR_MODE = "color_mode"
    private const val KEY_UI_SCALE = "ui_scale"
    private const val KEY_AUTOMATIC_CALCULATION_METHOD = "automatic_calculation_method"
    private const val KEY_CALCULATION_METHOD = "calculation_method"
    private const val KEY_ASR_METHOD = "asr_method"
    private const val KEY_NOTIFY_AT_PRAYER_START = "notify_at_prayer_start"
    private const val KEY_NOTIFY_BEFORE_PRAYER_START = "notify_before_prayer_start"
    private const val KEY_NOTIFY_BEFORE_PRAYER_END = "notify_before_prayer_end"
    private const val KEY_PLAY_ADHAN_AT_PRAYER_START = "play_adhan_at_prayer_start"
    private const val KEY_ADHAN_SOUND = "adhan_sound"
    private const val KEY_NOTIFY_SUNNAH_PRAYERS = "notify_sunnah_prayers"
    private const val KEY_SHOW_JUMUAH_PRAYER = "show_jumuah_prayer"
    private const val KEY_RAMADAN_MODE = "ramadan_mode"
    private const val KEY_HOLIDAY_BACKGROUNDS = "holiday_backgrounds"
    private const val KEY_NOTIFY_RAMADAN_IMSAK = "notify_ramadan_imsak"
    private const val KEY_HIJRI_DAY_ADJUSTMENT = "hijri_day_adjustment"
    private const val KEY_AUTO_SWITCH_LOCATION = "auto_switch_location"
    private const val KEY_SUGGEST_LOCATION_SWITCH = "suggest_location_switch"
    private const val KEY_VIBRATE_PRAYER_NOTIFICATIONS = "vibrate_prayer_notifications"
    private const val KEY_HOME_BLOCK_ORDER = "home_block_order"
    private const val KEY_PRAYER_NOTIFICATION_PREFIX = "prayer_notification_"
    private const val KEY_PRAYER_ADHAN_PREFIX = "prayer_adhan_"
    private const val KEY_ADJUSTMENT_FAJR = "adjustment_fajr"
    private const val KEY_ADJUSTMENT_SUNRISE = "adjustment_sunrise"
    private const val KEY_ADJUSTMENT_DHUHR = "adjustment_dhuhr"
    private const val KEY_ADJUSTMENT_ASR = "adjustment_asr"
    private const val KEY_ADJUSTMENT_MAGHRIB = "adjustment_maghrib"
    private const val KEY_ADJUSTMENT_ISHA = "adjustment_isha"
    private const val KEY_SELECTED_LOCATION_ID = "selected_location_id"
    private const val KEY_REVISION = "revision"
    private const val DEFAULT_UI_SCALE = 1.0f
    private const val NO_LOCATION_ID = -1L
    private const val MIN_HIJRI_ADJUSTMENT = -2
    private const val MAX_HIJRI_ADJUSTMENT = 2
}
