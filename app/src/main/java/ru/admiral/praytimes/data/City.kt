package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId

data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
) {
    val coordinates: Coordinates
        get() = Coordinates(latitude, longitude)

    val zoneId: ZoneId
        get() = ZoneId.of(timeZone)

    val displayName: String
        get() = "$name, $country"
}
