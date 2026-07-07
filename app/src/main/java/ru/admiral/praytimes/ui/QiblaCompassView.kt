package ru.admiral.praytimes.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import ru.admiral.praytimes.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class QiblaCompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var qiblaDirection: Float = 0f
        set(value) {
            field = normalizeDegrees(value)
            updateDescription()
            invalidate()
        }

    var deviceAzimuth: Float = 0f
        set(value) {
            field = normalizeDegrees(value)
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_divider)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_muted)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(1.2f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_accent)
        style = Paint.Style.FILL
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_primary)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_text)
        textAlign = Paint.Align.CENTER
        textSize = dp(14f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val degreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.color_muted)
        textAlign = Paint.Align.CENTER
        textSize = dp(12f)
    }
    private val hubLabelPaint = Paint(labelPaint).apply {
        color = context.getColor(android.R.color.white)
    }
    private val hubDegreePaint = Paint(degreePaint).apply {
        color = context.getColor(android.R.color.white)
    }
    private val northPaint = Paint(labelPaint).apply {
        color = context.getColor(R.color.color_accent)
    }

    init {
        updateDescription()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val size = min(availableWidth, availableHeight).toFloat()
        if (size <= 0f) {
            return
        }

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (size / 2f - dp(16f)).coerceAtLeast(dp(48f))

        canvas.drawCircle(centerX, centerY, radius, ringPaint)
        drawTicks(canvas, centerX, centerY, radius)
        drawCardinals(canvas, centerX, centerY, radius)
        drawQiblaArrow(canvas, centerX, centerY, radius)
        drawHub(canvas, centerX, centerY)
    }

    private fun drawTicks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        for (index in 0 until COMPASS_TICKS) {
            val angle = index * TICK_DEGREES - deviceAzimuth
            val length = if (index % MAJOR_TICK_STEP == 0) dp(10f) else dp(5f)
            val stroke = tickPaint.strokeWidth
            tickPaint.strokeWidth = if (index % MAJOR_TICK_STEP == 0) dp(1.8f) else dp(1f)
            val outer = pointOnCircle(centerX, centerY, radius, angle)
            val inner = pointOnCircle(centerX, centerY, radius - length, angle)
            canvas.drawLine(inner.x, inner.y, outer.x, outer.y, tickPaint)
            tickPaint.strokeWidth = stroke
        }
    }

    private fun drawCardinals(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val labels = listOf(
            CompassLabel(context.getString(R.string.compass_north_short), 0f, northPaint),
            CompassLabel(context.getString(R.string.compass_east_short), 90f, labelPaint),
            CompassLabel(context.getString(R.string.compass_south_short), 180f, labelPaint),
            CompassLabel(context.getString(R.string.compass_west_short), 270f, labelPaint),
        )

        labels.forEach { label ->
            val point = pointOnCircle(centerX, centerY, radius - dp(24f), label.degrees - deviceAzimuth)
            canvas.drawText(label.text, point.x, point.y + labelPaint.textSize / 3f, label.paint)
        }
    }

    private fun drawQiblaArrow(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val arrow = Path().apply {
            moveTo(centerX, centerY - radius + dp(22f))
            lineTo(centerX - dp(14f), centerY + dp(14f))
            lineTo(centerX, centerY - dp(7f))
            lineTo(centerX + dp(14f), centerY + dp(14f))
            close()
        }

        canvas.save()
        canvas.rotate(normalizeDegrees(qiblaDirection - deviceAzimuth), centerX, centerY)
        canvas.drawPath(arrow, arrowPaint)
        canvas.restore()
    }

    private fun drawHub(canvas: Canvas, centerX: Float, centerY: Float) {
        canvas.drawCircle(centerX, centerY, dp(31f), hubPaint)
        canvas.drawText(
            context.getString(R.string.qibla_compass),
            centerX,
            centerY - dp(3f),
            hubLabelPaint,
        )
        canvas.drawText(
            context.getString(R.string.qibla_degrees, qiblaDirection),
            centerX,
            centerY + dp(15f),
            hubDegreePaint,
        )
    }

    private fun pointOnCircle(centerX: Float, centerY: Float, radius: Float, degrees: Float): Point {
        val radians = Math.toRadians((degrees - 90f).toDouble())
        return Point(
            x = centerX + cos(radians).toFloat() * radius,
            y = centerY + sin(radians).toFloat() * radius,
        )
    }

    private fun updateDescription() {
        contentDescription = context.getString(
            R.string.qibla_compass_description,
            context.getString(R.string.qibla_degrees, qiblaDirection),
        )
    }

    private fun normalizeDegrees(value: Float): Float {
        val normalized = value % FULL_ROTATION
        return if (normalized < 0f) normalized + FULL_ROTATION else normalized
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class Point(val x: Float, val y: Float)

    private data class CompassLabel(
        val text: String,
        val degrees: Float,
        val paint: Paint,
    )

    private companion object {
        const val FULL_ROTATION = 360f
        const val COMPASS_TICKS = 72
        const val TICK_DEGREES = 5f
        const val MAJOR_TICK_STEP = 6
    }
}
