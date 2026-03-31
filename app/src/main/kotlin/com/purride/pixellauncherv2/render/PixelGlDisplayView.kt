package com.purride.pixellauncherv2.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.purride.pixelcore.PixelFrameView
import kotlin.math.abs
import kotlin.math.max

class PixelGlDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs), PixelFrameView {
    override var interactionListener: PixelFrameView.InteractionListener? = null

    private val frameSwapBuffer = FrameSwapBuffer()
    private val idleMaskSwapBuffer = IdleMaskSwapBuffer()
    private val glRenderer = PixelGlRenderer(
        frameSwapBuffer = frameSwapBuffer,
        idleMaskSwapBuffer = idleMaskSwapBuffer,
    )
    private var screenProfile: ScreenProfile? = null
    private var latestPixelBuffer: PixelBuffer? = null
    private var latestPalette: PixelPalette = PixelPalette.terminalGreen()
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

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(glRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun submitFrame(pixelBuffer: PixelBuffer, screenProfile: ScreenProfile, palette: PixelPalette) {
        RenderPerfLogger.measure("gl.submitFrame") {
            this.latestPixelBuffer = pixelBuffer
            this.screenProfile = screenProfile
            this.latestPalette = palette
            frameSwapBuffer.offer(
                pixelBuffer = pixelBuffer,
                screenProfile = screenProfile,
                palette = palette,
            )
            requestRender()
        }
    }

    override fun setPalette(palette: PixelPalette) {
        latestPalette = palette
        glRenderer.setFallbackPalette(palette)
        val pixelBuffer = latestPixelBuffer
        val profile = screenProfile
        if (pixelBuffer != null && profile != null) {
            frameSwapBuffer.offer(
                pixelBuffer = pixelBuffer,
                screenProfile = profile,
                palette = palette,
            )
        }
        requestRender()
    }

    override fun setPixelGapEnabled(enabled: Boolean) {
        glRenderer.setPixelGapEnabled(enabled)
        requestRender()
    }

    override fun submitIdleMask(frame: IdleMaskFrame?) {
        RenderPerfLogger.measure("gl.submitIdleMask") {
            if (frame == null) {
                idleMaskSwapBuffer.clear()
            } else {
                idleMaskSwapBuffer.offer(frame)
            }
            requestRender()
        }
    }

    override fun setIdleContinuousRendering(enabled: Boolean) {
        renderMode = if (enabled) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        RenderPerfLogger.mark("gl.renderMode", if (enabled) "continuous" else "when_dirty")
        if (!enabled) {
            requestRender()
        }
    }

    override fun asView(): View = this

    override fun onHostResume() {
        onResume()
    }

    override fun onHostPause() {
        onPause()
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
                val profile = screenProfile ?: return true
                val logicalPoint = mapTouchToLogical(event.x, event.y, profile) ?: return true
                lastLogicalX = logicalPoint.first
                lastLogicalY = logicalPoint.second
                val moveConsumed = interactionListener?.onLogicalDragMove(
                    x = logicalPoint.first,
                    y = logicalPoint.second,
                ) == true
                dragConsumed = dragConsumed || moveConsumed
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

    private fun mapTouchToLogical(touchX: Float, touchY: Float, profile: ScreenProfile): Pair<Int, Int>? {
        return PixelGridGeometryResolver.mapSurfaceToLogical(
            touchX = touchX,
            touchY = touchY,
            viewWidth = width,
            viewHeight = height,
            profile = profile,
            pixelGapEnabled = glRenderer.pixelGapEnabled,
        )
    }
}
