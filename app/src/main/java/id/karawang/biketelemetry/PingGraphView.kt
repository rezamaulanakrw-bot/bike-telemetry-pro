package id.karawang.biketelemetry

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class PingGraphView(context: Context) : View(context) {

    private val history = ArrayDeque<Long>()
    private val maxSamples = 24

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        textSize = 24f
    }

    fun addSample(pingMs: Long) {
        if (pingMs < 0) return
        if (history.size >= maxSamples) history.removeFirst()
        history.addLast(pingMs)
        linePaint.color = when {
            pingMs <= 80L -> Color.parseColor("#10B981")
            pingMs <= 160L -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#EF4444")
        }
        dotPaint.color = linePaint.color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawLine(0f, h * 0.25f, w, h * 0.25f, gridPaint)
        canvas.drawLine(0f, h * 0.50f, w, h * 0.50f, gridPaint)
        canvas.drawLine(0f, h * 0.75f, w, h * 0.75f, gridPaint)

        if (history.size < 2) {
            canvas.drawText("Menunggu sampel ping jaringan...", 16f, h * 0.55f, textPaint)
            return
        }

        val maxVal = (history.maxOrNull() ?: 150L).coerceAtLeast(150L).toFloat()
        val stepX = w / (maxSamples - 1).toFloat()
        val path = Path()

        var lastX = 0f
        var lastY = h

        history.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = (value.toFloat() / maxVal).coerceIn(0.05f, 0.95f)
            val y = h - (normalized * (h - 24f)) - 12f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            lastX = x
            lastY = y
        }

        canvas.drawPath(path, linePaint)
        canvas.drawCircle(lastX, lastY, 8f, dotPaint)
    }
}
