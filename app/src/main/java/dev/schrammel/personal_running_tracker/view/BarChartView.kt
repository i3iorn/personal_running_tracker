package dev.schrammel.personal_running_tracker.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Minimal bar chart for rolling distance; pure Canvas, no chart library. */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private val barPaint = Paint().apply { color = Color.parseColor("#2E7D32") }
    private val axisPaint = Paint().apply {
        color = Color.GRAY; strokeWidth = 2f
    }

    fun setValues(newValues: List<Float>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        canvas.drawLine(0f, height - 4f, width.toFloat(), height - 4f, axisPaint)
        val maxV = values.max().coerceAtLeast(1f)
        val n = values.size
        val barWidth = width.toFloat() / n
        for ((i, v) in values.withIndex()) {
            val barHeight = (height - 8f) * (v / maxV)
            val left = i * barWidth + barWidth * 0.15f
            val right = (i + 1) * barWidth - barWidth * 0.15f
            canvas.drawRect(left, height - 4f - barHeight, right, height - 4f, barPaint)
        }
    }
}
