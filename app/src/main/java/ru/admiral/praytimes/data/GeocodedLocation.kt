package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId

data class GeocodedLocation(
    val name: String,
    val coordinates: Coordinates,
    val zoneId: ZoneId,
    val provider: String,
) {
    val displayName: String
        get() = "$name ($provider)"
}
