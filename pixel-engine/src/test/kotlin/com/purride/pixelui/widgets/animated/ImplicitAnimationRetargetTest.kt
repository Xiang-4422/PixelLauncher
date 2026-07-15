package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Stack
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Render-level retarget contract shared by implicit layout and property animations. */
class ImplicitAnimationRetargetTest {
    /** AnimatedContainer starts a replacement segment at its current rendered width. */
    @Test
    fun containerWidthRetargetsFromCurrentVisualValue() {
        // Declarative target changes twice before the first segment can finish.
        val width = ValueNotifier(4)
        // Tester exposes deterministic pixels and the Host-owned virtual ticker provider.
        val tester = PixelTester()
        tester.pumpWidget(
            ValueListenableBuilder(width) { _, targetWidth ->
                AnimatedContainer(
                    duration = SegmentDuration,
                    vsync = tester.vsync,
                    curve = Curves.Linear,
                    width = targetWidth,
                    height = 3,
                    borderColor = PixelColor.White,
                    key = "animated-width",
                )
            },
            logicalWidth = 16,
            logicalHeight = 4,
        )

        width.value = 12
        beginReplacementSegment(tester)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(8, visibleWidth(tester))

        width.value = 2
        tester.pumpFrame(0)
        assertEquals(8, visibleWidth(tester))
        tester.pumpFrame(0)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(5, visibleWidth(tester))
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(2, visibleWidth(tester))

        assertNoAnimationResources(tester)
    }

    /** AnimatedPadding keeps the child at its current offset when a new inset arrives mid-flight. */
    @Test
    fun paddingRetargetsFromCurrentChildOffset() {
        // Only the left inset changes so the white child pixel directly exposes interpolation.
        val leftPadding = ValueNotifier(0)
        // Each test owns an isolated virtual Host and scheduler.
        val tester = PixelTester()
        tester.pumpWidget(
            ValueListenableBuilder(leftPadding) { _, targetLeft ->
                AnimatedPadding(
                    padding = EdgeInsets(left = targetLeft, top = 0, right = 0, bottom = 0),
                    duration = SegmentDuration,
                    vsync = tester.vsync,
                    curve = Curves.Linear,
                    key = "animated-padding",
                    child = whitePixel(),
                )
            },
            logicalWidth = 16,
            logicalHeight = 2,
        )

        leftPadding.value = 8
        beginReplacementSegment(tester)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(4, singleVisiblePixel(tester).first)

        leftPadding.value = 2
        tester.pumpFrame(0)
        assertEquals(4, singleVisiblePixel(tester).first)
        tester.pumpFrame(0)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(3, singleVisiblePixel(tester).first)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(2, singleVisiblePixel(tester).first)

        assertNoAnimationResources(tester)
    }

    /** AnimatedPositioned starts its second path at the currently painted Stack coordinate. */
    @Test
    fun positionedRetargetsFromCurrentPaintedCoordinate() {
        // Left position is observable as the only white pixel's x coordinate.
        val left = ValueNotifier(0)
        // Stack provides the positioning coordinate space for the animated child.
        val tester = PixelTester()
        tester.pumpWidget(
            ValueListenableBuilder(left) { _, targetLeft ->
                Stack(
                    children = listOf(
                        AnimatedPositioned(
                            duration = SegmentDuration,
                            vsync = tester.vsync,
                            curve = Curves.Linear,
                            left = targetLeft,
                            top = 0,
                            key = "animated-position",
                            child = whitePixel(),
                        ),
                    ),
                )
            },
            logicalWidth = 16,
            logicalHeight = 2,
        )

        left.value = 8
        beginReplacementSegment(tester)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(4, singleVisiblePixel(tester).first)

        left.value = 2
        tester.pumpFrame(0)
        assertEquals(4, singleVisiblePixel(tester).first)
        tester.pumpFrame(0)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(3, singleVisiblePixel(tester).first)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(2, singleVisiblePixel(tester).first)

        assertNoAnimationResources(tester)
    }

    /** Discrete AnimatedAlign retargeting retains the last visible enum position until its threshold. */
    @Test
    fun alignRetargetKeepsCurrentDiscretePosition() {
        // Alignment is a finite enum, so its public animation contract switches at 50 percent.
        val alignment = ValueNotifier(Alignment.TOP_START)
        // A single pixel makes each discrete alignment position unambiguous.
        val tester = PixelTester()
        tester.pumpWidget(
            ValueListenableBuilder(alignment) { _, targetAlignment ->
                AnimatedAlign(
                    alignment = targetAlignment,
                    duration = SegmentDuration,
                    vsync = tester.vsync,
                    curve = Curves.Linear,
                    key = "animated-alignment",
                    child = whitePixel(),
                )
            },
            logicalWidth = 5,
            logicalHeight = 5,
        )

        alignment.value = Alignment.BOTTOM_END
        beginReplacementSegment(tester)
        tester.pumpFrame(750)
        assertEquals(4 to 4, singleVisiblePixel(tester))

        alignment.value = Alignment.TOP_END
        tester.pumpFrame(0)
        assertEquals(4 to 4, singleVisiblePixel(tester))
        tester.pumpFrame(0)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(4 to 0, singleVisiblePixel(tester))
        tester.pumpFrame(HalfSegmentMillis)

        assertNoAnimationResources(tester)
    }

    /** A Host pause freezes an in-flight property animation and resume excludes background time. */
    @Test
    fun hostTickerPauseAndResumePreserveTheRenderedFrame() {
        // Width makes animation progress directly measurable from painted pixels.
        val width = ValueNotifier(4)
        // The tester provider exposes the same pause/resume contract used by PixelHostFrameScope.
        val tester = PixelTester()
        tester.pumpWidget(
            ValueListenableBuilder(width) { _, targetWidth ->
                AnimatedContainer(
                    duration = SegmentDuration,
                    vsync = tester.vsync,
                    curve = Curves.Linear,
                    width = targetWidth,
                    height = 3,
                    borderColor = PixelColor.White,
                    key = "paused-animated-width",
                )
            },
            logicalWidth = 16,
            logicalHeight = 4,
        )

        width.value = 12
        beginReplacementSegment(tester)
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(8, visibleWidth(tester))

        tester.vsync.pause()
        tester.pumpFrame(60_000)
        assertEquals(8, visibleWidth(tester))
        assertEquals(0, tester.scheduler.pendingCount)

        tester.vsync.resume()
        tester.pumpFrame(0)
        assertEquals(8, visibleWidth(tester))
        tester.pumpFrame(HalfSegmentMillis)
        assertEquals(12, visibleWidth(tester))

        assertNoAnimationResources(tester)
    }

    /** Anchors a controller restarted during the first retained rebuild. */
    private fun beginReplacementSegment(tester: PixelTester) {
        tester.pumpFrame(0)
        tester.pumpFrame(0)
    }

    /** Returns the x/y coordinate of the only non-transparent pixel in the current frame. */
    private fun singleVisiblePixel(tester: PixelTester): Pair<Int, Int> {
        val buffer = checkNotNull(tester.renderResult?.buffer)
        val indices = buffer.pixels.indices.filter { index ->
            buffer.pixels[index] != PixelColor.Transparent.argb
        }
        assertEquals(1, indices.size)
        val index = indices.single()
        return (index % buffer.width) to (index / buffer.width)
    }

    /** Returns the inclusive painted x span as a width for the bordered container fixture. */
    private fun visibleWidth(tester: PixelTester): Int {
        val buffer = checkNotNull(tester.renderResult?.buffer)
        val visibleX = buffer.pixels.indices.mapNotNull { index ->
            index.takeIf { buffer.pixels[it] != PixelColor.Transparent.argb }?.rem(buffer.width)
        }
        return checkNotNull(visibleX.maxOrNull()) - checkNotNull(visibleX.minOrNull()) + 1
    }

    /** Creates the minimal painted child used to expose layout coordinates. */
    private fun whitePixel() = Container(
        width = 1,
        height = 1,
        fillColor = PixelColor.White,
        borderColor = null,
    )

    /** Verifies completion and terminal tester cleanup leave no ticker or source callback. */
    private fun assertNoAnimationResources(tester: PixelTester) {
        assertEquals(0, tester.vsync.activeTickerCount)
        tester.dispose()
        assertEquals(0, tester.scheduler.pendingCount)
    }

    private companion object {
        /** Duration shared by every deterministic implicit-animation segment. */
        val SegmentDuration = 1_000.milliseconds

        /** Half-duration frame step used for exact integer interpolation assertions. */
        const val HalfSegmentMillis: Long = 500L
    }
}
