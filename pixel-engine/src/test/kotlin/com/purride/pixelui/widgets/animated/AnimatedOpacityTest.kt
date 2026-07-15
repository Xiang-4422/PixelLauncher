package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Semantics
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.host.PixelHostFrameScope
import com.purride.pixelui.regression.ReviewedGoldenVerifier
import com.purride.pixelui.testing.PixelTester
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Pixel-level contract tests for continuous AnimatedOpacity and its retained ticker lifecycle. */
class AnimatedOpacityTest {
    /** Values below the lower tier remain hidden for explicit quantized transition callers. */
    @Test
    fun quantizeOpacityBelowThreshold() {
        assertEquals(0f, quantizeOpacity(0f), FLOAT_TOLERANCE)
        assertEquals(0f, quantizeOpacity(0.24f), FLOAT_TOLERANCE)
    }

    /** Inclusive middle thresholds map to the half-opacity pixel tier. */
    @Test
    fun quantizeOpacityMidRange() {
        assertEquals(0.5f, quantizeOpacity(0.25f), FLOAT_TOLERANCE)
        assertEquals(0.5f, quantizeOpacity(0.5f), FLOAT_TOLERANCE)
        assertEquals(0.5f, quantizeOpacity(0.75f), FLOAT_TOLERANCE)
    }

    /** Values above the upper tier become fully visible for quantized transitions. */
    @Test
    fun quantizeOpacityAboveThreshold() {
        assertEquals(1f, quantizeOpacity(0.76f), FLOAT_TOLERANCE)
        assertEquals(1f, quantizeOpacity(1f), FLOAT_TOLERANCE)
    }

    /** The standalone quantizer remains a stable three-tier helper for AnimatedSwitcher. */
    @Test
    fun quantizeOpacityOnlyThreeTiers() {
        val inputs = (0..100).map { it / 100f }
        val outputs = inputs.map { quantizeOpacity(it) }.toSet()
        assertEquals(setOf(0f, 0.5f, 1f), outputs)
    }

    /** 0/25/50/75/100% animation frames match exact ARGB golden and output-channel policy. */
    @Test
    fun milestoneFramesMatchExactPixelGoldenAndInteractionPolicy() {
        val tester = PixelTester()
        try {
            tester.pumpWidget(animatedOpacity(target = 0f, vsync = tester.vsync), WIDTH, HEIGHT)
            tester.pumpWidget(animatedOpacity(target = 1f, vsync = tester.vsync), WIDTH, HEIGHT)
            tester.pumpFrame(deltaMs = 0L)

            val actualGolden = buildString {
                appendMilestoneFrame(percent = 0, tester = tester)
                assertMilestonePolicy(tester = tester, percent = 0)
                for (percent in listOf(25, 50, 75, 100)) {
                    tester.pumpFrame(deltaMs = 250L)
                    appendMilestoneFrame(percent = percent, tester = tester)
                    assertMilestonePolicy(tester = tester, percent = percent)
                }
            }
            ReviewedGoldenVerifier.assertMatches(
                baselineFile = File(GOLDEN_PATH),
                actual = actualGolden,
                reportStem = File("build/reports/golden/animation/animated-opacity-percent-frames"),
            )
        } finally {
            tester.dispose()
        }
    }

    /** Retarget and rapid interruption start from the currently painted alpha without a jump. */
    @Test
    fun retargetAndRapidSwitchContinueFromCurrentVisualValue() {
        val tester = PixelTester()
        try {
            tester.pumpWidget(animatedOpacity(target = 0f, vsync = tester.vsync), WIDTH, HEIGHT)
            tester.pumpWidget(animatedOpacity(target = 1f, vsync = tester.vsync), WIDTH, HEIGHT)
            tester.pumpFrame(deltaMs = 0L)
            tester.pumpFrame(deltaMs = 400L)
            assertPaintedAlpha(tester, expectedAlpha = 102)

            tester.pumpWidget(animatedOpacity(target = 0f, vsync = tester.vsync), WIDTH, HEIGHT)
            assertPaintedAlpha(tester, expectedAlpha = 102)
            tester.pumpFrame(deltaMs = 0L)
            assertPaintedAlpha(tester, expectedAlpha = 102)
            tester.pumpFrame(deltaMs = 250L)
            assertPaintedAlpha(tester, expectedAlpha = 77)

            tester.pumpWidget(animatedOpacity(target = 1f, vsync = tester.vsync), WIDTH, HEIGHT)
            assertPaintedAlpha(tester, expectedAlpha = 77)
            tester.pumpFrame(deltaMs = 0L)
            tester.pumpFrame(deltaMs = 500L)
            assertPaintedAlpha(tester, expectedAlpha = 166)

            tester.pumpWidget(animatedOpacity(target = 0f, vsync = tester.vsync), WIDTH, HEIGHT)
            tester.pumpWidget(animatedOpacity(target = 1f, vsync = tester.vsync), WIDTH, HEIGHT)
            assertPaintedAlpha(tester, expectedAlpha = 166)
            assertEquals(1, tester.scheduler.pendingCount)
            assertEquals(1, tester.vsync.diagnostics().activeTickerCount)
            assertEquals(1, tester.vsync.diagnostics().liveTickerCount)
        } finally {
            tester.dispose()
        }

        assertEquals(0, tester.scheduler.pendingCount)
        assertEquals(0, tester.vsync.diagnostics().activeTickerCount)
        assertEquals(0, tester.vsync.diagnostics().liveTickerCount)
    }

    /** Host-owned scope pause freezes visual alpha; resume continues active time and dispose leaks no ticker. */
    @Test
    fun hostPauseResumeKeepsContinuousValueAndDisposeReleasesTicker() {
        val scheduler = ManualFrameScheduler()
        val scope = PixelHostFrameScope(scheduler)
        val tester = PixelTester()
        try {
            tester.pumpWidget(animatedOpacity(target = 0f, vsync = scope.tickerProvider), WIDTH, HEIGHT)
            tester.pumpWidget(animatedOpacity(target = 1f, vsync = scope.tickerProvider), WIDTH, HEIGHT)
            advanceExternalFrame(tester, scope.tickerProvider, scheduler, sourceNanos = 0L)
            advanceExternalFrame(tester, scope.tickerProvider, scheduler, sourceNanos = 250_000_000L)
            assertPaintedAlpha(tester, expectedAlpha = 64)

            scope.pause()
            assertTrue(scope.diagnostics().isPaused)
            assertEquals(0, scheduler.pendingCount)
            advanceExternalFrame(
                tester,
                scope.tickerProvider,
                scheduler,
                sourceNanos = 60_250_000_000L,
            )
            assertPaintedAlpha(tester, expectedAlpha = 64)

            scope.resume()
            assertFalse(scope.diagnostics().isPaused)
            assertEquals(1, scheduler.pendingCount)
            advanceExternalFrame(
                tester,
                scope.tickerProvider,
                scheduler,
                sourceNanos = 60_250_000_000L,
            )
            assertPaintedAlpha(tester, expectedAlpha = 64)
            advanceExternalFrame(
                tester,
                scope.tickerProvider,
                scheduler,
                sourceNanos = 60_500_000_000L,
            )
            assertPaintedAlpha(tester, expectedAlpha = 128)
        } finally {
            tester.dispose()
        }

        val diagnostics = scope.diagnostics()
        assertEquals(0, diagnostics.activeTickerCount)
        assertEquals(0, diagnostics.liveTickerCount)
        assertEquals(0, diagnostics.pendingCallbackCount)
        assertFalse(diagnostics.sourceFramePending)
        assertEquals(0, scheduler.pendingCount)
        scope.dispose()
    }

    /** Builds one stable-key AnimatedOpacity around paint, click and semantics-producing content. */
    private fun animatedOpacity(
        target: Float,
        vsync: PixelTickerProvider,
    ): Widget {
        return AnimatedOpacity(
            opacity = target,
            duration = DURATION_MS.milliseconds,
            vsync = vsync,
            curve = Curves.Linear,
            key = ANIMATED_OPACITY_KEY,
            child = Semantics(
                label = SEMANTICS_LABEL,
                role = PixelSemanticRole.BUTTON,
                child = GestureDetector(
                    onTap = {},
                    child = Container(
                        width = WIDTH,
                        height = HEIGHT,
                        fillColor = CONTENT_COLOR,
                        borderColor = null,
                    ),
                ),
            ),
        )
    }

    /** Drives one external Host-scope source frame and re-renders the retained widget tree. */
    private fun advanceExternalFrame(
        tester: PixelTester,
        vsync: PixelTickerProvider,
        scheduler: ManualFrameScheduler,
        sourceNanos: Long,
    ) {
        scheduler.advanceFrame(sourceNanos)
        tester.pumpWidget(animatedOpacity(target = 1f, vsync = vsync), WIDTH, HEIGHT)
    }

    /** Asserts exact alpha plus fixed child RGB for a painted animation frame. */
    private fun assertPaintedAlpha(tester: PixelTester, expectedAlpha: Int) {
        val pixel = tester.pixelAt(0, 0)
        assertEquals(expectedAlpha, pixel.alpha)
        if (expectedAlpha > 0) {
            assertEquals(CONTENT_COLOR.red, pixel.red)
            assertEquals(CONTENT_COLOR.green, pixel.green)
            assertEquals(CONTENT_COLOR.blue, pixel.blue)
        }
    }

    /** Locks paint alpha and click/semantics exposure at one milestone frame. */
    private fun assertMilestonePolicy(tester: PixelTester, percent: Int) {
        val expectedAlpha = (percent * 255f / 100f + 0.5f).toInt()
        assertPaintedAlpha(tester, expectedAlpha)
        val result = requireNotNull(tester.renderResult)
        val expectedOutputCount = if (percent == 0) 0 else 1
        assertEquals(expectedOutputCount, result.clickTargets.size)
        assertEquals(expectedOutputCount, result.semanticsNodes.size)
        if (percent > 0) {
            assertEquals(SEMANTICS_LABEL, result.semanticsNodes.single().label)
        }
    }

    /** Appends every exact ARGB pixel in one milestone frame to the reviewed golden text. */
    private fun StringBuilder.appendMilestoneFrame(percent: Int, tester: PixelTester) {
        append("frame=").append(percent).append("%\n")
        append("size=").append(WIDTH).append('x').append(HEIGHT).append('\n')
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                if (x > 0) append(' ')
                append(tester.pixelAt(x, y).argb.toUInt().toString(radix = 16).padStart(8, '0').uppercase())
            }
            append('\n')
        }
    }

    private companion object {
        /** Floating-point tolerance used only by the explicit three-tier quantizer tests. */
        const val FLOAT_TOLERANCE: Float = 1e-4f

        /** Logical width of the exact-pixel animation fixture. */
        const val WIDTH: Int = 2

        /** Logical height of the exact-pixel animation fixture. */
        const val HEIGHT: Int = 2

        /** Duration whose quarter frames land exactly at 25% milestones. */
        const val DURATION_MS: Int = 1_000

        /** Stable retained identity across target updates. */
        const val ANIMATED_OPACITY_KEY: String = "animated-opacity-contract"

        /** Semantics label whose zero/nonzero exposure is asserted. */
        const val SEMANTICS_LABEL: String = "opacity-content"

        /** Exact-pixel reviewed golden resource. */
        /** 已审阅动画关键帧 exact-ARGB 源码基线路径。 */
        const val GOLDEN_PATH: String = "src/test/resources/golden/animated_opacity_percent_frames.txt"

        /** Opaque source color whose alpha alone is scaled by RenderOpacity. */
        val CONTENT_COLOR: PixelColor = PixelColor.fromRgb(255, 64, 16)
    }
}
