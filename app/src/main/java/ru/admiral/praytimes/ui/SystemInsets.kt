package ru.admiral.praytimes.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

object SystemInsets {
    fun applyTo(view: View) {
        val initialPadding = Edges(
            left = view.paddingLeft,
            top = view.paddingTop,
            right = view.paddingRight,
            bottom = view.paddingBottom,
        )
        view.setOnApplyWindowInsetsListener { target, insets ->
            val systemInsets = systemInsets(insets)
            target.setPadding(
                initialPadding.left + systemInsets.left,
                initialPadding.top + systemInsets.top,
                initialPadding.right + systemInsets.right,
                initialPadding.bottom + systemInsets.bottom,
            )
            insets
        }
        view.requestApplyInsets()
    }

    private fun systemInsets(insets: WindowInsets): Edges =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val values = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            Edges(values.left, values.top, values.right, values.bottom)
        } else {
            legacyInsets(insets)
        }

    @Suppress("DEPRECATION")
    private fun legacyInsets(insets: WindowInsets): Edges {
        val bars = Edges(
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetTop,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom,
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return bars
        }
        val cutout = insets.displayCutout ?: return bars
        return Edges(
            max(bars.left, cutout.safeInsetLeft),
            max(bars.top, cutout.safeInsetTop),
            max(bars.right, cutout.safeInsetRight),
            max(bars.bottom, cutout.safeInsetBottom),
        )
    }

    private data class Edges(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
