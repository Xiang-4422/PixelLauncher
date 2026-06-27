package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelErrorBoundary
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelErrorBoundaryTest {
    @Test
    fun boundaryReplacesFailingChildWithFallback() {
        val fallbackColor = PixelColor.fromRgb(255, 80, 80)
        val error = IllegalStateException("boom")
        var reported: Throwable? = null
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelErrorBoundary(
                onError = { reported = it },
                errorBuilder = { Text("SAFE", color = fallbackColor) },
                child = ThrowingWidget(error),
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        )

        assertSame(error, reported)
        assertTrue(tester.hasPixel(fallbackColor))
    }

    @Test
    fun buildErrorWithoutBoundaryStillEscapes() {
        val error = IllegalStateException("boom")
        val thrown = try {
            PixelTester().pumpWidget(
                widget = ThrowingWidget(error),
                logicalWidth = 32,
                logicalHeight = 8,
            )
            null
        } catch (caught: IllegalStateException) {
            caught
        }

        assertSame(error, thrown)
    }

    private class ThrowingWidget(
        private val error: RuntimeException,
    ) : StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            throw error
        }
    }
}
