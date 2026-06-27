package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelErrorBoundary
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.LeafRenderObjectWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
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

    @Test
    fun boundaryReplacesRenderErrorWithFallback() {
        val fallbackColor = PixelColor.fromRgb(255, 80, 80)
        val error = IllegalStateException("layout boom")
        var reported: Throwable? = null
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelErrorBoundary(
                onError = { reported = it },
                errorBuilder = { Text("SAFE", color = fallbackColor) },
                child = ThrowingRenderWidget(error),
            ),
            logicalWidth = 32,
            logicalHeight = 8,
        )

        assertSame(error, reported)
        assertTrue(tester.hasPixel(fallbackColor))
        tester.dispose()
    }

    @Test
    fun renderErrorWithoutBoundaryStillEscapes() {
        val error = IllegalStateException("layout boom")
        val thrown = try {
            PixelTester().pumpWidget(
                widget = ThrowingRenderWidget(error),
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

    private class ThrowingRenderWidget(
        private val error: RuntimeException,
    ) : LeafRenderObjectWidget() {
        override fun createRenderObject(context: BuildContext): RenderObject {
            return ThrowingRenderBox(error)
        }
    }

    private class ThrowingRenderBox(
        private val error: RuntimeException,
    ) : RenderBox() {
        override fun layout(constraints: RenderConstraints) {
            throw error
        }

        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) = Unit
    }
}
