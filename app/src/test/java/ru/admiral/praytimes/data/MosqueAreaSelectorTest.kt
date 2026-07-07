package ru.admiral.praytimes.data

import kotlin.test.Test
import kotlin.test.assertEquals

class MosqueAreaSelectorTest {
    @Test
    fun selectsMoscowCityInsteadOfDistrict() {
        val selected = MosqueAreaSelector.select(
            listOf(
                candidate(id = 3600102269, name = "Москва", adminLevel = 4),
                candidate(id = 3601257786, name = "Тверской район", adminLevel = 8),
                candidate(id = 3602162196, name = "Центральный административный округ", adminLevel = 5),
            ),
            locationLabel = "Тверской район, Москва",
        )

        assertEquals(3600102269, selected?.id)
    }

    @Test
    fun selectsNewYorkCityInsteadOfState() {
        val selected = MosqueAreaSelector.select(
            listOf(
                candidate(id = 3600061320, name = "New York", adminLevel = 4, borderType = "state"),
                candidate(id = 3600175905, name = "New York", adminLevel = 5, borderType = "city"),
                candidate(id = 3602552485, name = "New York County", adminLevel = 6, borderType = "county"),
                candidate(id = 3608398124, name = "Manhattan", adminLevel = 7, borderType = "borough"),
            ),
            locationLabel = "New York, United States",
        )

        assertEquals(3600175905, selected?.id)
    }

    @Test
    fun prefersMunicipalAreaWithoutLabelMatch() {
        val selected = MosqueAreaSelector.select(
            listOf(
                candidate(id = 1, name = "Example Region", adminLevel = 4, borderType = "region"),
                candidate(id = 2, name = "Example City", adminLevel = 8),
            ),
            locationLabel = "Current location",
        )

        assertEquals(2, selected?.id)
    }

    private fun candidate(
        id: Long,
        name: String,
        adminLevel: Int,
        place: String = "",
        borderType: String = "",
    ) = MosqueAreaCandidate(
        id = id,
        names = listOf(name),
        adminLevel = adminLevel,
        place = place,
        borderType = borderType,
    )
}
