package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.data.SavedLocation

data class NearbyLocationSwitch(
    val location: SavedLocation,
    val distanceMeters: Double,
)

object NearbyLocationSwitchResolver {
    fun resolve(
        savedLocations: List<SavedLocation>,
        activeLocationId: Long?,
        activeCoordinates: Coordinates,
        activeLocationSource: String,
        deviceCoordinates: Coordinates,
        maxDistanceMeters: Double = DEFAULT_SWITCH_DISTANCE_METERS,
    ): NearbyLocationSwitch? {
        if (savedLocations.isEmpty() || activeLocationSource == DEVICE_LOCATION_SOURCE) {
            return null
        }

        val nearest = savedLocations
            .asSequence()
            .map { location -> location to GeoDistance.meters(deviceCoordinates, location.coordinates) }
            .minByOrNull { it.second }
            ?: return null

        val location = nearest.first
        val distanceToActive = GeoDistance.meters(deviceCoordinates, activeCoordinates)
        if (location.id == activeLocationId || distanceToActive <= SAME_ACTIVE_LOCATION_DISTANCE_METERS) {
            return null
        }
        if (nearest.second > maxDistanceMeters) {
            return null
        }

        return NearbyLocationSwitch(location, nearest.second)
    }

    private const val DEVICE_LOCATION_SOURCE = "device"
    private const val DEFAULT_SWITCH_DISTANCE_METERS = 25_000.0
    private const val SAME_ACTIVE_LOCATION_DISTANCE_METERS = 1_000.0
}
