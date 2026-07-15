package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.InteractionDetector
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.SliderWidget
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real View routing acceptance for pressed cleanup and hover source filtering. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostInteractionInstrumentedTest {
    /** Up, cancel, movement takeover, and pause each balance the active pressed callback once. */
    @Test
    fun hostBalancesPressedAcrossEveryGestureTerminationPath() {
        val events = mutableListOf<Boolean>()
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent { interactionWidget(onPressedChanged = events::add) }
                renderSynchronously(host)
                val rawPoint = rawCenterOfFirstClickTarget(host)

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 10L, eventTime = 10L))
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_UP, rawPoint, downTime = 10L, eventTime = 20L))

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 30L, eventTime = 30L))
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_CANCEL, rawPoint, downTime = 30L, eventTime = 40L))

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 50L, eventTime = 50L))
                val movedPoint = RawPoint(rawPoint.x + host.touchSlop + 2f, rawPoint.y)
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_MOVE, movedPoint, downTime = 50L, eventTime = 60L))
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_UP, movedPoint, downTime = 50L, eventTime = 70L))

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 80L, eventTime = 80L))
                host.pause()

                assertEquals(
                    listOf(true, false, true, false, true, false, true, false),
                    events,
                )
                host.resume()
            }
        }
    }

    /** Mouse and stylus hover are routed, while touchscreen accessibility hover is untouched. */
    @Test
    fun hostRoutesPhysicalHoverWithoutConsumingTouchExplorationHover() {
        val events = mutableListOf<Boolean>()
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent { interactionWidget(onHoveredChanged = events::add) }
                renderSynchronously(host)
                val rawPoint = rawCenterOfFirstClickTarget(host)

                host.onHoverEvent(
                    hoverEvent(MotionEvent.ACTION_HOVER_ENTER, rawPoint, InputDevice.SOURCE_TOUCHSCREEN),
                )
                assertEquals(emptyList<Boolean>(), events)

                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_ENTER, rawPoint, InputDevice.SOURCE_MOUSE))
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_MOVE, rawPoint, InputDevice.SOURCE_MOUSE))
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_EXIT, rawPoint, InputDevice.SOURCE_MOUSE))

                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_ENTER, rawPoint, InputDevice.SOURCE_STYLUS))
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_EXIT, rawPoint, InputDevice.SOURCE_STYLUS))

                assertEquals(listOf(true, false, true, false), events)
            }
        }
    }

    /** A foreground removed after down cannot transfer its pending tap to an exposed background. */
    @Test
    fun hostBindsTapToDownSourceAcrossRenderSnapshots() {
        val foregroundEvents = mutableListOf<String>()
        val backgroundEvents = mutableListOf<String>()
        var foregroundVisible = true
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent {
                    Stack(
                        children = buildList {
                            add(
                                interactionWidget(
                                    onTap = { backgroundEvents += "tap" },
                                    key = BackgroundKey,
                                ),
                            )
                            if (foregroundVisible) {
                                add(
                                    interactionWidget(
                                        onTap = { foregroundEvents += "tap" },
                                        onPressedChanged = { foregroundEvents += "pressed:$it" },
                                        key = ForegroundKey,
                                    ),
                                )
                            }
                        },
                    )
                }
                renderSynchronously(host)
                val foregroundBounds = checkNotNull(host.lastRenderResult?.clickTargets?.lastOrNull()).bounds
                val rawPoint = rawCenterOfBounds(host, foregroundBounds)

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 100L, eventTime = 100L))
                foregroundVisible = false
                host.invalidate()
                renderSynchronously(host)
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_UP, rawPoint, downTime = 100L, eventTime = 120L))

                assertEquals(listOf("pressed:true", "pressed:false"), foregroundEvents)
                assertEquals(emptyList<String>(), backgroundEvents)
            }
        }
    }

    /** Removing an active slider cancels pressed feedback and suppresses its later release. */
    @Test
    fun hostCancelsDismissedSliderWithoutRelease() {
        val events = mutableListOf<String>()
        var sliderVisible = true
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent {
                    if (sliderVisible) {
                        SliderWidget(
                            value = 0.5f,
                            onDrag = { events += "drag" },
                            onRelease = { events += "release" },
                            onPressedChanged = { events += "pressed:$it" },
                            key = SliderKey,
                        )
                    } else {
                        emptySurface()
                    }
                }
                renderSynchronously(host)
                val sliderBounds = checkNotNull(host.lastRenderResult?.sliderTargets?.lastOrNull()).bounds
                val rawPoint = rawCenterOfBounds(host, sliderBounds)

                host.onTouchEvent(touchEvent(MotionEvent.ACTION_DOWN, rawPoint, downTime = 200L, eventTime = 200L))
                sliderVisible = false
                host.invalidate()
                renderSynchronously(host)
                host.onTouchEvent(touchEvent(MotionEvent.ACTION_UP, rawPoint, downTime = 200L, eventTime = 220L))

                assertEquals(listOf("pressed:true", "pressed:false"), events)
            }
        }
    }

    /** Removing a physically hovered source emits one exit without re-hitting replacement UI. */
    @Test
    fun hostClearsHoverWhenSourceDisappears() {
        val events = mutableListOf<Boolean>()
        var targetVisible = true
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent {
                    if (targetVisible) {
                        interactionWidget(onHoveredChanged = events::add, key = ForegroundKey)
                    } else {
                        emptySurface()
                    }
                }
                renderSynchronously(host)
                val rawPoint = rawCenterOfFirstClickTarget(host)

                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_ENTER, rawPoint, InputDevice.SOURCE_MOUSE))
                targetVisible = false
                host.invalidate()
                renderSynchronously(host)
                host.onHoverEvent(hoverEvent(MotionEvent.ACTION_HOVER_EXIT, rawPoint, InputDevice.SOURCE_MOUSE))

                assertEquals(listOf(true, false), events)
            }
        }
    }

    /** Builds one fixed interaction surface for real View target collection. */
    private fun interactionWidget(
        onTap: () -> Unit = {},
        onPressedChanged: ((Boolean) -> Unit)? = null,
        onHoveredChanged: ((Boolean) -> Unit)? = null,
        key: Any? = null,
    ): Widget {
        return InteractionDetector(
            child = Container(
                width = 8,
                height = 8,
                fillColor = PixelColor.White,
                borderColor = null,
            ),
            onTap = onTap,
            onPressedChanged = onPressedChanged,
            onHoveredChanged = onHoveredChanged,
            key = key,
        )
    }

    /** Builds a non-interactive replacement for a removed interaction owner. */
    private fun emptySurface(): Widget {
        return Container(
            width = 8,
            height = 8,
            fillColor = PixelColor.Black,
            borderColor = null,
        )
    }

    /** Converts the first logical click target center back into Android View coordinates. */
    private fun rawCenterOfFirstClickTarget(host: PixelHostView): RawPoint {
        val target = checkNotNull(host.lastRenderResult?.clickTargets?.firstOrNull())
        return rawCenterOfBounds(host, target.bounds)
    }

    /** Converts one logical target rectangle center into Android View coordinates. */
    private fun rawCenterOfBounds(host: PixelHostView, bounds: PixelRect): RawPoint {
        val geometry = checkNotNull(host.resolveGridGeometry())
        val logicalX = bounds.left + bounds.width / 2f
        val logicalY = bounds.top + bounds.height / 2f
        return RawPoint(
            x = geometry.originX + (logicalX + 0.5f) * geometry.cellSize,
            y = geometry.originY + (logicalY + 0.5f) * geometry.cellSize,
        )
    }

    /** Creates a touchscreen event at a deterministic virtual event time. */
    private fun touchEvent(action: Int, point: RawPoint, downTime: Long, eventTime: Long): MotionEvent {
        return MotionEvent.obtain(downTime, eventTime, action, point.x, point.y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
    }

    /** Creates a hover event from an explicit physical or accessibility source. */
    private fun hoverEvent(action: Int, point: RawPoint, source: Int): MotionEvent {
        return MotionEvent.obtain(0L, 0L, action, point.x, point.y, 0).apply {
            this.source = source
        }
    }

    /** Draws a retained Host frame synchronously so targets are available immediately. */
    private fun renderSynchronously(host: PixelHostView) {
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    private companion object {
        /** Stable source key for the removable foreground click and hover target. */
        const val ForegroundKey: String = "foreground-target"

        /** Stable source key for the click target exposed beneath a removed foreground. */
        const val BackgroundKey: String = "background-target"

        /** Stable source key for the removable slider target. */
        const val SliderKey: String = "slider-target"
    }
}

/** Android View-space coordinate used by deterministic MotionEvent helpers. */
private data class RawPoint(
    /** Horizontal View coordinate in physical pixels. */
    val x: Float,
    /** Vertical View coordinate in physical pixels. */
    val y: Float,
)
