package com.purride.pixellauncherv2.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.purride.pixellauncherv2.data.HandTrackingDebugFrame
import kotlin.math.min

internal class HandTrackingDebugOverlayView(
    context: Context,
) : View(context) {
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(188, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f * resources.displayMetrics.density
        typeface = Typeface.MONOSPACE
    }
    private val bitmapRect = Rect()
    private val panelRect = RectF()
    private val imageRect = RectF()
    private var frame: HandTrackingDebugFrame? = null

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun updateFrame(next: HandTrackingDebugFrame?) {
        frame = next
        visibility = if (next == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = frame ?: return
        val density = resources.displayMetrics.density
        val margin = 8f * density
        val panelWidth = min(width * 0.52f, 220f * density).coerceAtLeast(120f * density)
        val panelHeight = panelWidth * 1.36f
        panelRect.set(margin, margin, margin + panelWidth, margin + panelHeight)
        canvas.drawRect(panelRect, panelPaint)
        canvas.drawRect(panelRect, borderPaint)

        val labelHeight = 24f * density
        imageRect.set(
            panelRect.left + 4f * density,
            panelRect.top + labelHeight,
            panelRect.right - 4f * density,
            panelRect.bottom - 4f * density,
        )
        drawCameraFrame(canvas, current)
        drawLandmarks(canvas, current)
        drawLabel(canvas, current, density)
    }

    private fun drawCameraFrame(canvas: Canvas, frame: HandTrackingDebugFrame) {
        bitmapRect.set(0, 0, frame.bitmap.width, frame.bitmap.height)
        canvas.drawBitmap(frame.bitmap, bitmapRect, imageRect, null)
    }

    private fun drawLandmarks(canvas: Canvas, frame: HandTrackingDebugFrame) {
        val xs = frame.normalizedX ?: return
        val ys = frame.normalizedY ?: return
        if (xs.size < landmarkCount || ys.size < landmarkCount) return

        for ((from, to) in handConnections) {
            canvas.drawLine(
                landmarkX(xs[from]),
                landmarkY(ys[from]),
                landmarkX(xs[to]),
                landmarkY(ys[to]),
                bonePaint,
            )
        }
        for (index in 0 until landmarkCount) {
            val paint = if (index == thumbTip || index == indexTip) tipPaint else pointPaint
            val radius = if (paint == tipPaint) 4f else 2.5f
            canvas.drawCircle(landmarkX(xs[index]), landmarkY(ys[index]), radius, paint)
        }
    }

    private fun drawLabel(canvas: Canvas, frame: HandTrackingDebugFrame, density: Float) {
        labelPaint.color = when (frame.label) {
            "PINCH" -> Color.rgb(255, 84, 84)
            "OPEN" -> Color.rgb(96, 255, 160)
            "FIST" -> Color.rgb(255, 176, 64)
            "POINT" -> Color.rgb(112, 220, 255)
            "VICTORY" -> Color.rgb(180, 140, 255)
            "LOVE" -> Color.rgb(255, 96, 180)
            else -> Color.WHITE
        }
        val confidence = (frame.confidence * 100f).toInt().coerceIn(0, 100)
        canvas.drawText(
            "HAND ${frame.label} $confidence%",
            panelRect.left + 6f * density,
            panelRect.top + 16f * density,
            labelPaint,
        )
    }

    private fun landmarkX(value: Float): Float = imageRect.left + value.coerceIn(0f, 1f) * imageRect.width()

    private fun landmarkY(value: Float): Float = imageRect.top + value.coerceIn(0f, 1f) * imageRect.height()

    private companion object {
        const val landmarkCount: Int = 21
        const val thumbTip: Int = 4
        const val indexTip: Int = 8
        val handConnections = arrayOf(
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 4,
            0 to 5,
            5 to 6,
            6 to 7,
            7 to 8,
            5 to 9,
            9 to 10,
            10 to 11,
            11 to 12,
            9 to 13,
            13 to 14,
            14 to 15,
            15 to 16,
            13 to 17,
            0 to 17,
            17 to 18,
            18 to 19,
            19 to 20,
        )
    }
}
