package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.Coordinates
import ru.admiral.praytimes.data.SavedLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NearbyLocationSwitchResolverTest {
    @Test
    fun deviceLocationDoesNotSuggestSavedLocation() {
        val switch = NearbyLocationSwitchResolver.resolve(
            savedLocations = listOf(location(id = 1L, latitude = 55.7560, longitude = 37.6176)),
            activeLocationId = null,
            activeCoordinates = Coordinates(55.7558, 37.6173),
            activeLocationSource = "device",
            deviceCoordinates = Coordinates(55.7558, 37.6173),
        )

        assertNull(switch)
    }

    @Test
    fun sameActiveCoordinatesDoNotSuggestSavedLocation() {
        val switch = NearbyLocationSwitchResolver.resolve(
            savedLocations = listOf(location(id = 7L, latitude = 55.7560, longitude = 37.6176)),
            activeLocationId = null,
            activeCoordinates = Coordinates(55.7558, 37.6173),
            activeLocationSource = "geocoder",
            deviceCoordinates = Coordinates(55.7558, 37.6173),
        )

        assertNull(switch)
    }

    @Test
    fun nearestSavedLocationIsSuggestedWithinRadius() {
        val switch = NearbyLocationSwitchResolver.resolve(
            savedLocations = listOf(
                location(id = 1L, latitude = 55.7512, longitude = 37.6184),
                location(id = 2L, latitude = 59.9343, longitude = 30.3351),
            ),
            activeLocationId = null,
            activeCoordinates = Coordinates(55.0, 37.0),
            activeLocationSource = "fallback",
            deviceCoordinates = Coordinates(55.7558, 37.6173),
        )

        assertEquals(1L, switch?.location?.id)
    }

    @Test
    fun activeSavedLocationIsNotSuggestedAgain() {
        val switch = NearbyLocationSwitchResolver.resolve(
            savedLocations = listOf(location(id = 3L, latitude = 55.7558, longitude = 37.6173)),
            activeLocationId = 3L,
            activeCoordinates = Coordinates(55.7558, 37.6173),
            activeLocationSource = "geocoder",
            deviceCoordinates = Coordinates(55.7560, 37.6176),
        )

        assertNull(switch)
    }

    private fun location(id: Long, latitude: Double, longitude: Double): SavedLocation =
        SavedLocation(
            id = id,
            name = "Location $id",
            latitude = latitude,
            longitude = longitude,
            timeZone = "Europe/Moscow",
            source = "geocoder",
        )
}
