package ru.admiral.praytimes.data

import android.content.Context

object CityRepository {
    fun load(context: Context): List<City> =
        context.assets.open("cities.csv").bufferedReader().useLines { lines ->
            lines.drop(1)
                .mapNotNull(::parseCity)
                .sortedWith(compareBy<City> { it.country }.thenBy { it.name })
                .toList()
        }

    fun findBestMatch(cities: List<City>, query: String): City? {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) {
            return null
        }

        return cities.firstOrNull { city ->
            city.displayName.lowercase() == normalized || city.name.lowercase() == normalized
        } ?: cities.firstOrNull { city ->
            city.displayName.lowercase().contains(normalized)
        }
    }

    private fun parseCity(line: String): City? {
        val parts = line.split(',')
        if (parts.size != 5) {
            return null
        }

        return City(
            name = parts[0].trim(),
            country = parts[1].trim(),
            latitude = parts[2].trim().toDoubleOrNull() ?: return null,
            longitude = parts[3].trim().toDoubleOrNull() ?: return null,
            timeZone = parts[4].trim(),
        )
    }
}
