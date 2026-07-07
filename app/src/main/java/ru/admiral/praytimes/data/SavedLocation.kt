package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId

data class SavedLocation(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val source: String,
    val colorKey: String = LocationVisual.DEFAULT_COLOR_KEY,
    val iconKey: String = LocationVisual.DEFAULT_ICON_KEY,
    val note: String = "",
) {
    val coordinates: Coordinates
        get() = Coordinates(latitude, longitude)

    val zoneId: ZoneId
        get() = ZoneId.of(timeZone)

    val displayName: String
        get() = name
}
