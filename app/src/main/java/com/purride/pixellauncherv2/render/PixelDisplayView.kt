package com.purride.pixellauncherv2.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class PixelDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface InteractionListener {
        fun onLogicalTap(x: Int, y: Int)
        fun onSwipeUp()
        fun onSwipeDown()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onLogicalDragStart(x: Int, y: Int): Boolean
        fun onLogicalDragMove(x: Int, y: Int): Boolean
        fun onLogicalDragEnd(x: Int, y: Int, cancelled: Boolean): Boolean
    }

    var interactionListener: InteractionListener? = null

    private var pixelBuffer: PixelBuffer? = null
    private var screenProfile: ScreenProfile? = null
    private var palette: PixelPalette = PixelPalette.terminalGreen()
    private val swipeDistanceThresholdPx = max(
        ViewConfiguration.get(context).scaledTouchSlop * 4f,
        context.resources.displayMetrics.density * 32f,
    )
    private val swipeAxisBias = 1.2f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchActive = false
    private var dragConsumed = false
    private var lastLogicalX = 0
    private var lastLogicalY = 0

    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        isAntiAlias = false
        isFilterBitmap = false
    }
    private val reusableDiamondPath = Path()

    fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, palette: PixelPalette) {
        this.pixelBuffer = pixelBuffer
        this.screenProfile = screenProfile
        this.palette = palette
        invalidate()
    }

    fun setPalette(palette: PixelPalette) {
        this.palette = palette
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(palette.backgroundColor)

        val buffer = pixelBuffer ?: return
        val profile = screenProfile ?: return
        val gridMetrics = calculateGridMetrics(profile) ?: return

        onPaint.color = palette.pixelOnColor
        accentPaint.color = palette.accentColor
        offPaint.color = palette.pixelOffColor

        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val left = gridMetrics.originX + (x * gridMetrics.cellSize) + gridMetrics.dotInset
                val top = gridMetrics.originY + (y * gridMetrics.cellSize) + gridMetrics.dotInset
                val right = left + gridMetrics.dotSize
                val bottom = top + gridMetrics.dotSize
                val paint = when (buffer.getPixel(x, y)) {
                    PixelBuffer.ON -> onPaint
                    PixelBuffer.ACCENT -> accentPaint
                    else -> offPaint
                }

                when (profile.pixelShape) {
                    PixelShape.SQUARE -> canvas.drawRect(left, top, right, bottom, paint)
                    PixelShape.CIRCLE -> {
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        val radius = min(right - left, bottom - top) / 2f
                        canvas.drawCircle(centerX, centerY, radius, paint)
                    }

                    PixelShape.DIAMOND -> {
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        reusableDiamondPath.reset()
                        reusableDiamondPath.moveTo(centerX, top)
                        reusableDiamondPath.lineTo(left, centerY)
                        reusableDiamondPath.lineTo(centerX, bottom)
                        reusableDiamondPath.lineTo(right, centerY)
                        reusableDiamondPath.close()
                        canvas.drawPath(reusableDiamondPath, paint)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchActive = true
                dragConsumed = false
                val profile = screenProfile
                val logicalPoint = profile?.let { mapTouchToLogical(event.x, event.y, it) }
                if (logicalPoint != null) {
                    lastLogicalX = logicalPoint.first
                    lastLogicalY = logicalPoint.second
                    dragConsumed = interactionListener?.onLogicalDragStart(
                        x = logicalPoint.first,
                        y = logicalPoint.second,
                    ) == true
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) {
                    return true
                }
                if (!dragConsumed) {
                    return true
                }
                val profile = screenProfile ?: return true
                val logicalPoint = mapTouchToLogical(event.x, event.y, profile) ?: return true
                lastLogicalX = logicalPoint.first
                lastLogicalY = logicalPoint.second
                dragConsumed = interactionListener?.onLogicalDragMove(
                    x = logicalPoint.first,
                    y = logicalPoint.second,
                ) == true
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!touchActive) {
                    return true
                }

                touchActive = false
                val profile = screenProfile
                val logicalPoint = profile?.let { mapTouchToLogical(event.x, event.y, it) }
                if (logicalPoint != null) {
                    lastLogicalX = logicalPoint.first
                    lastLogicalY = logicalPoint.second
                }
                if (dragConsumed) {
                    interactionListener?.onLogicalDragEnd(
                        x = lastLogicalX,
                        y = lastLogicalY,
                        cancelled = false,
                    )
                    dragConsumed = false
                    return true
                }

                performClick()
                val deltaX = event.x - touchDownX
                val deltaY = event.y - touchDownY
                if (abs(deltaY) >= swipeDistanceThresholdPx && abs(deltaY) > abs(deltaX) * swipeAxisBias) {
                    if (deltaY < 0f) {
                        interactionListener?.onSwipeUp()
                    } else {
                        interactionListener?.onSwipeDown()
                    }
                    return true
                }
                if (abs(deltaX) >= swipeDistanceThresholdPx && abs(deltaX) > abs(deltaY) * swipeAxisBias) {
                    if (deltaX < 0f) {
                        interactionListener?.onSwipeLeft()
                    } else {
                        interactionListener?.onSwipeRight()
                    }
                    return true
                }

                val tapProfile = screenProfile ?: return true
                val tapLogicalPoint = mapTouchToLogical(event.x, event.y, tapProfile) ?: return true
                interactionListener?.onLogicalTap(tapLogicalPoint.first, tapLogicalPoint.second)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (touchActive && dragConsumed) {
                    interactionListener?.onLogicalDragEnd(
                        x = lastLogicalX,
                        y = lastLogicalY,
                        cancelled = true,
                    )
                }
                touchActive = false
                dragConsumed = false
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun calculateGridMetrics(profile: ScreenProfile): GridMetrics? {
        if (width <= 0 || height <= 0) {
            return null
        }

        val cellSize = when (profile.scaleMode) {
            ScaleMode.FIT_CENTER -> floor(
                min(
                width.toFloat() / profile.logicalWidth.toFloat(),
                height.toFloat() / profile.logicalHeight.toFloat(),
                ),
            )
        }
        if (cellSize <= 0f) {
            return null
        }

        val contentWidth = cellSize * profile.logicalWidth
        val contentHeight = cellSize * profile.logicalHeight
        val originX = (width - contentWidth) / 2f
        val originY = (height - contentHeight) / 2f
        val dotInset = max(1f, floor(cellSize * 0.16f))
        val dotSize = max(1f, floor(cellSize - (dotInset * 2f)))

        return GridMetrics(
            cellSize = cellSize,
            originX = originX,
            originY = originY,
            dotInset = dotInset,
            dotSize = dotSize,
        )
    }

    private fun mapTouchToLogical(touchX: Float, touchY: Float, profile: ScreenProfile): Pair<Int, Int>? {
        val gridMetrics = calculateGridMetrics(profile) ?: return null
        val localX = touchX - gridMetrics.originX
        val localY = touchY - gridMetrics.originY
        val contentWidth = gridMetrics.cellSize * profile.logicalWidth
        val contentHeight = gridMetrics.cellSize * profile.logicalHeight

        if (localX < 0f || localY < 0f || localX >= contentWidth || localY >= contentHeight) {
            return null
        }

        val logicalX = (localX / gridMetrics.cellSize).toInt().coerceIn(0, profile.logicalWidth - 1)
        val logicalY = (localY / gridMetrics.cellSize).toInt().coerceIn(0, profile.logicalHeight - 1)
        return logicalX to logicalY
    }

    private data class GridMetrics(
        val cellSize: Float,
        val originX: Float,
        val originY: Float,
        val dotInset: Float,
        val dotSize: Float,
    )
}
