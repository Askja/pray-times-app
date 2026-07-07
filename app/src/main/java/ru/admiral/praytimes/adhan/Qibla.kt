package ru.admiral.praytimes.adhan

data class Qibla(val direction: Double) {
    constructor(coordinates: Coordinates) : this(QiblaUtil.calculateQiblaDirection(coordinates))
}
