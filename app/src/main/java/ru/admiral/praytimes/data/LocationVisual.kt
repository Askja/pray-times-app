package ru.admiral.praytimes.data

object LocationVisual {
    const val DEFAULT_COLOR_KEY = "emerald"
    const val DEFAULT_ICON_KEY = "location"

    val colorKeys = setOf("emerald", "teal", "amber", "rose", "blue", "violet")
    val iconKeys = setOf("location", "city", "mosque", "moon", "star", "qibla")

    fun safeColorKey(value: String): String =
        value.takeIf(colorKeys::contains) ?: DEFAULT_COLOR_KEY

    fun safeIconKey(value: String): String =
        value.takeIf(iconKeys::contains) ?: DEFAULT_ICON_KEY
}
