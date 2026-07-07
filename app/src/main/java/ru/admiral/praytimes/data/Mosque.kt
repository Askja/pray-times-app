package ru.admiral.praytimes.data

import ru.admiral.praytimes.adhan.Coordinates

data class Mosque(
    val id: String,
    val name: String,
    val address: String,
    val coordinates: Coordinates,
    val distanceMeters: Double,
)
