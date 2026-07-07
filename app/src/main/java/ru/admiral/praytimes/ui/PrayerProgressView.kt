package ru.admiral.praytimes.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import ru.admiral.praytimes.R
import kotlin.math.min

class PrayerProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var progressColor: Int = context.getColor(R.color.color_primary)
        set(value) {
            field = value
            progressPaint.color = value
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_divider)
        style = Paint.Style.STROKE
        strokeWidth = dp(STROKE_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = progressColor
        style = Paint.Style.STROKE
        strokeWidth = dp(STROKE_WIDTH_DP)
        strokeCap = Paint.Cap.ROUND
    }

    private val bounds = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom).toFloat()
        if (size <= 0f) {
            return
        }

        val strokeInset = progressPaint.strokeWidth / 2f + dp(2f)
        val left = (width - size) / 2f + strokeInset
        val top = (height - size) / 2f + strokeInset
        bounds.set(left, top, left + size - strokeInset * 2f, top + size - strokeInset * 2f)

        canvas.drawArc(bounds, START_DEGREES, FULL_SWEEP, false, trackPaint)
        canvas.drawArc(bounds, START_DEGREES, FULL_SWEEP * progress, false, progressPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val STROKE_WIDTH_DP = 12f
        const val START_DEGREES = -90f
        const val FULL_SWEEP = 360f
    }
}
