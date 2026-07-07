package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId

data class CalculationMethodSelection(
    val method: CalculationMethod,
    val isAutomatic: Boolean,
)

object CalculationMethodSelector {
    fun select(
        automatic: Boolean,
        manualMethod: CalculationMethod,
        coordinates: Coordinates,
        zoneId: ZoneId,
        locationLabel: String,
    ): CalculationMethodSelection {
        val method = if (automatic) {
            LocationCalculationMethodResolver.resolve(coordinates, zoneId, locationLabel)
        } else {
            manualMethod
        }
        return CalculationMethodSelection(method, automatic)
    }
}
