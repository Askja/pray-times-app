package ru.admiral.praytimes.data

import android.location.LocationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocationProviderSelectorTest {
    @Test
    fun coarseLocationDoesNotUseGpsProvider() {
        val providers = LocationProviderSelector.enabledProviders(
            providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            hasFineLocation = false,
            hasCoarseLocation = true,
        )

        assertEquals(listOf(LocationManager.NETWORK_PROVIDER), providers)
    }

    @Test
    fun fineLocationKeepsAllEnabledProviders() {
        val providers = LocationProviderSelector.enabledProviders(
            providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            hasFineLocation = true,
            hasCoarseLocation = true,
        )

        assertEquals(listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER), providers)
    }

    @Test
    fun missingLocationPermissionReturnsNoProviders() {
        val providers = LocationProviderSelector.enabledProviders(
            providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER),
            hasFineLocation = false,
            hasCoarseLocation = false,
        )

        assertTrue(providers.isEmpty())
    }
}
