package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Container
import com.purride.pixelui.PixelMotionRole
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelMotionSpec
import com.purride.pixelui.PixelMotionTheme
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelMotionTransitionPreset
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayHandle
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.SizedBox
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/** Virtual-clock behavior and exact-pixel tests for MotionTheme dialog presentation. */
class PixelDialogMotionTest {
    /** Verifies enter/exit milestones, immediate logical dismissal, and exit target isolation. */
    @Test
    fun dialogEnterAndExitUseExactFramesAndReleaseTicker() {
        val tester = PixelTester()
        val overlayController = PixelOverlayController()
        tester.pumpWidget(
            widget = motionRoot(tester, overlayController, PixelMotionSettings.Default),
            logicalWidth = CanvasSize,
            logicalHeight = CanvasSize,
        )

        val handle = showRedDialog(overlayController)
        tester.pumpFrame(0)
        assertDialogPixel(tester, alpha = 0)
        tester.pumpFrame(0)
        tester.pumpFrame(QuarterMillis)
        assertDialogPixel(tester, alpha = 64)
        tester.pumpFrame(QuarterMillis)
        assertDialogPixel(tester, alpha = 128)
        tester.pumpFrame(QuarterMillis)
        assertDialogPixel(tester, alpha = 191)
        tester.pumpFrame(QuarterMillis)
        assertDialogPixel(tester, alpha = 255)
        assertEquals(1, overlayController.size)
        assertEquals(0, tester.vsync.activeTickerCount)

        assertTrue(handle.dismiss(PixelOverlayDismissReason.Handle))
        assertEquals(0, overlayController.size)
        tester.pumpFrame(0)
        assertDialogPixel(tester, alpha = 255)
        assertTrue(tester.renderResult?.clickTargets.orEmpty().isEmpty())
        assertTrue(tester.renderResult?.semanticsNodes.orEmpty().isEmpty())
        tester.pumpFrame(0)
        tester.pumpFrame(HalfMillis)
        assertDialogPixel(tester, alpha = 128)
        tester.pumpFrame(HalfMillis)
        assertDialogPixel(tester, alpha = 0)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        tester.dispose()
    }

    /** Verifies an enter interrupted by dismiss reverses from the currently painted alpha. */
    @Test
    fun dismissDuringEnterRetargetsWithoutVisualJump() {
        val tester = PixelTester()
        val overlayController = PixelOverlayController()
        tester.pumpWidget(
            widget = motionRoot(tester, overlayController, PixelMotionSettings.Default),
            logicalWidth = CanvasSize,
            logicalHeight = CanvasSize,
        )
        val handle = showRedDialog(overlayController)
        tester.pumpFrame(0)
        tester.pumpFrame(0)
        tester.pumpFrame(HalfMillis)
        assertDialogPixel(tester, alpha = 128)

        assertTrue(handle.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)
        assertDialogPixel(tester, alpha = 128)
        tester.pumpFrame(0)
        tester.pumpFrame(HalfMillis)
        assertDialogPixel(tester, alpha = 64)
        tester.pumpFrame(HalfMillis)
        assertDialogPixel(tester, alpha = 0)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        tester.dispose()
    }

    /** Scale zero applies enter/dismiss terminal state synchronously without pending frames. */
    @Test
    fun zeroAnimatorScaleHasNoDelayedCleanupOrTicker() {
        val tester = PixelTester()
        val overlayController = PixelOverlayController()
        val settings = PixelMotionSettings(animatorDurationScale = 0f, reduceMotion = true)
        tester.pumpWidget(
            widget = motionRoot(tester, overlayController, settings),
            logicalWidth = CanvasSize,
            logicalHeight = CanvasSize,
        )

        val handle = showRedDialog(overlayController)
        tester.pumpFrame(0)
        assertDialogPixel(tester, alpha = 255)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        assertTrue(handle.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(0)
        assertDialogPixel(tester, alpha = 0)
        assertEquals(0, overlayController.size)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)

        tester.dispose()
    }

    /** Controller clear force-removes retained exits and cancels their owned ticker immediately. */
    @Test
    fun clearForcesSynchronousVisualAndTickerTeardown() {
        val tester = PixelTester()
        val overlayController = PixelOverlayController()
        tester.pumpWidget(
            widget = motionRoot(tester, overlayController, PixelMotionSettings.Default),
            logicalWidth = CanvasSize,
            logicalHeight = CanvasSize,
        )
        showRedDialog(overlayController)
        tester.pumpFrame(0)
        assertEquals(1, tester.vsync.liveTickerCount)

        overlayController.clear()
        tester.pumpFrame(0)

        assertDialogPixel(tester, alpha = 0)
        assertEquals(0, overlayController.size)
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.vsync.liveTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    /** Builds one explicit Motion scope with deterministic linear dialog tokens. */
    private fun motionRoot(
        tester: PixelTester,
        overlayController: PixelOverlayController,
        settings: PixelMotionSettings,
    ) = PixelMotionTheme(
        data = PixelMotionThemeData.Default.copy(
            dialogEnter = dialogSpec(),
            dialogExit = dialogSpec(),
        ),
        child = PixelMotionScope(
            vsync = tester.vsync,
            settings = settings,
            child = PixelOverlayHost(
                controller = overlayController,
                child = SizedBox(width = CanvasSize, height = CanvasSize),
            ),
        ),
    )

    /** Returns the spatial fade token shared by enter and exit in these deterministic tests. */
    private fun dialogSpec(): PixelMotionSpec {
        return PixelMotionSpec(
            duration = MotionMillis.milliseconds,
            curve = Curves.Linear,
            transition = PixelMotionTransitionPreset.Fade,
            role = PixelMotionRole.Spatial,
        )
    }

    /** Adds one transparent-shell dialog whose center content has an exact red ARGB pixel. */
    private fun showRedDialog(controller: PixelOverlayController): PixelOverlayHandle {
        return controller.showDialog(
            content = Container(
                width = ContentSize,
                height = ContentSize,
                fillColor = PixelColor.fromRgb(255, 0, 0),
            ),
            fillColor = PixelColor.Transparent,
            borderColor = PixelColor.Transparent,
        )
    }

    /** Asserts an exact group-opacity red pixel, or its complete absence at zero alpha. */
    private fun assertDialogPixel(tester: PixelTester, alpha: Int) {
        val expected = PixelColor.fromArgb(alpha, 255, 0, 0)
        if (alpha > 0) {
            val visibleRedAlphas = (tester.renderResult?.buffer?.pixels ?: IntArray(0))
                .asSequence()
                .map(::PixelColor)
                .filter { pixel -> pixel.red == 255 && pixel.green == 0 && pixel.blue == 0 }
                .map(PixelColor::alpha)
                .filter { value -> value > 0 }
                .distinct()
                .sorted()
                .toList()
            assertTrue(
                "Missing exact dialog pixel $expected; visible red alphas=$visibleRedAlphas",
                alpha in visibleRedAlphas,
            )
            return
        }
        val pixels = tester.renderResult?.buffer?.pixels ?: IntArray(0)
        val hasVisibleRed = pixels.any { argb ->
            val pixel = PixelColor(argb)
            pixel.alpha > 0 && pixel.red == 255 && pixel.green == 0 && pixel.blue == 0
        }
        assertFalse("Dialog red content must be fully absent", hasVisibleRed)
    }

    /** Deterministic canvas, geometry, and virtual-time constants. */
    private companion object {
        /** Square logical canvas size. */
        const val CanvasSize: Int = 16

        /** Square red dialog content size before outer padding. */
        const val ContentSize: Int = 2

        /** Full linear enter or exit duration. */
        const val MotionMillis: Long = 400L

        /** Quarter-duration frame delta. */
        const val QuarterMillis: Long = 100L

        /** Half-duration frame delta. */
        const val HalfMillis: Long = 200L
    }
}
