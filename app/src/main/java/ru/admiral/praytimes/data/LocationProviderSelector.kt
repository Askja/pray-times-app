package ru.admiral.praytimes.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager

object LocationProviderSelector {
    fun enabledProviders(context: Context, manager: LocationManager): List<String> =
        enabledProviders(
            providers = manager.getProviders(true),
            hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
            hasCoarseLocation = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )

    fun preferredCurrentProvider(providers: List<String>): String? =
        providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
            ?: providers.firstOrNull { it == LocationManager.GPS_PROVIDER }
            ?: providers.firstOrNull()

    internal fun enabledProviders(
        providers: List<String>,
        hasFineLocation: Boolean,
        hasCoarseLocation: Boolean,
    ): List<String> = when {
        hasFineLocation -> providers
        hasCoarseLocation -> providers.filterNot { it == LocationManager.GPS_PROVIDER }
        else -> emptyList()
    }
}
