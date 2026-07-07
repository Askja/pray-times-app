package ru.admiral.praytimes.domain

import ru.admiral.praytimes.adhan.CalculationMethod
import ru.admiral.praytimes.adhan.Coordinates
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalculationMethodSelectorTest {
    @Test
    fun automaticSelectionUsesRegionalMethod() {
        val selection = CalculationMethodSelector.select(
            automatic = true,
            manualMethod = CalculationMethod.TURKEY_DIYANET,
            coordinates = Coordinates(55.7558, 37.6173),
            zoneId = ZoneId.of("Europe/Moscow"),
            locationLabel = "Moscow, Russia",
        )

        assertEquals(CalculationMethod.RUSSIA, selection.method)
        assertTrue(selection.isAutomatic)
    }

    @Test
    fun manualSelectionKeepsSelectedMethod() {
        val selection = CalculationMethodSelector.select(
            automatic = false,
            manualMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE,
            coordinates = Coordinates(55.7558, 37.6173),
            zoneId = ZoneId.of("Europe/Moscow"),
            locationLabel = "Moscow, Russia",
        )

        assertEquals(CalculationMethod.MUSLIM_WORLD_LEAGUE, selection.method)
        assertFalse(selection.isAutomatic)
    }
}
