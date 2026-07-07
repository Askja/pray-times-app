package ru.admiral.praytimes.ui

import android.graphics.Color
import ru.admiral.praytimes.R
import ru.admiral.praytimes.data.LocationVisual

data class LocationColorStyle(
    val key: String,
    val color: Int,
)

data class LocationIconStyle(
    val key: String,
    val drawableRes: Int,
)

object LocationVisualStyle {
    val colors = listOf(
        LocationColorStyle("emerald", Color.parseColor("#0E6A58")),
        LocationColorStyle("teal", Color.parseColor("#127A8A")),
        LocationColorStyle("amber", Color.parseColor("#B77812")),
        LocationColorStyle("rose", Color.parseColor("#B0445E")),
        LocationColorStyle("blue", Color.parseColor("#2867A8")),
        LocationColorStyle("violet", Color.parseColor("#6E55A8")),
    )

    val icons = listOf(
        LocationIconStyle("location", R.drawable.ic_location),
        LocationIconStyle("city", R.drawable.ic_city),
        LocationIconStyle("mosque", R.drawable.ic_mosque),
        LocationIconStyle("moon", R.drawable.ic_moon),
        LocationIconStyle("star", R.drawable.ic_star),
        LocationIconStyle("qibla", R.drawable.ic_qibla),
    )

    fun color(key: String): LocationColorStyle =
        colors.firstOrNull { it.key == LocationVisual.safeColorKey(key) } ?: colors.first()

    fun icon(key: String): LocationIconStyle =
        icons.firstOrNull { it.key == LocationVisual.safeIconKey(key) } ?: icons.first()
}
