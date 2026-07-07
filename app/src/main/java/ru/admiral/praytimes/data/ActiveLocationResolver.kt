package ru.admiral.praytimes.data

import android.content.Context
import ru.admiral.praytimes.R
import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.adhan.PrayerAdjustments
import ru.admiral.praytimes.domain.CalculationMethodSelector
import ru.admiral.praytimes.settings.AppSettings
import java.time.ZoneId

data class ActivePrayerLocation(
    val id: Long?,
    val name: String,
    val coordinates: Coordinates,
    val zoneId: ZoneId,
    val source: String,
)

object ActiveLocationResolver {
    fun resolve(context: Context, database: LocationDatabase = LocationDatabase(context)): ActivePrayerLocation {
        val selected = AppSettings.selectedLocationId(context)?.let(database::locationById)
        val saved = selected ?: database.listLocations().firstOrNull()
        if (saved != null) {
            return ActivePrayerLocation(
                id = saved.id,
                name = if (saved.source == SEED_LOCATION_SOURCE) context.getString(R.string.default_location_name) else saved.name,
                coordinates = saved.coordinates,
                zoneId = saved.zoneId,
                source = saved.source,
            )
        }

        return ActivePrayerLocation(
            id = null,
            name = context.getString(R.string.default_location_name),
            coordinates = Coordinates(DEFAULT_LATITUDE, DEFAULT_LONGITUDE),
            zoneId = ZoneId.of(DEFAULT_ZONE_ID),
            source = FALLBACK_LOCATION_SOURCE,
        )
    }

    fun adjustments(
        context: Context,
        database: LocationDatabase,
        location: ActivePrayerLocation,
    ): PrayerAdjustments =
        location.id?.let(database::prayerAdjustments) ?: AppSettings.globalPrayerAdjustments(context)

    fun calculationMethod(context: Context, location: ActivePrayerLocation): CalculationMethod =
        CalculationMethodSelector.select(
            automatic = AppSettings.automaticCalculationMethod(context),
            manualMethod = AppSettings.calculationMethod(context),
            coordinates = location.coordinates,
            zoneId = location.zoneId,
            locationLabel = location.name,
        ).method

    private const val DEFAULT_LATITUDE = 36.8121
    private const val DEFAULT_LONGITUDE = 34.6415
    private const val DEFAULT_ZONE_ID = "Europe/Istanbul"
    private const val SEED_LOCATION_SOURCE = "seed"
    private const val FALLBACK_LOCATION_SOURCE = "fallback"
}
