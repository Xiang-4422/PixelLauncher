package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.EdgeInsets
import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies retained Slider paint fields update independently without replacing render identity. */
class RenderSliderTokenUpdateTest {
    /** A border-only retheme and a radius-only retheme both repaint the existing render object. */
    @Test
    fun retainedSliderAppliesIndependentBorderAndRadiusUpdates() {
        /** Stable no-op drag callback preventing callback identity from masking paint-only changes. */
        val onDrag: (Float) -> Unit = { }
        /** Stable no-op release callback preventing callback identity from masking paint-only changes. */
        val onRelease: (Float) -> Unit = { }
        /** Initial exact outline sentinel. */
        val firstBorder = PixelColor.fromRgb(31, 97, 163)
        /** Replacement exact outline sentinel. */
        val secondBorder = PixelColor.fromRgb(227, 71, 109)
        /** Retained render object whose token-backed paint fields are updated in place. */
        val render = RenderSlider(
            value = 0.5f,
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = null,
            onHoveredChanged = null,
            enabled = true,
            minimumWidth = WIDTH,
            height = HEIGHT,
            padding = EdgeInsets.all(1),
            activeColor = ACTIVE_COLOR,
            trackColor = TRACK_COLOR,
            borderColor = firstBorder,
            borderWidth = 1,
            cornerRadius = 0,
            shadowColor = null,
            shadowOffset = 0,
        )
        render.layout(RenderConstraints(maxWidth = WIDTH, maxHeight = HEIGHT))
        assertEquals(firstBorder, paint(render).getPixel(0, 0))

        render.update(
            value = 0.5f,
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = null,
            onHoveredChanged = null,
            enabled = true,
            minimumWidth = WIDTH,
            height = HEIGHT,
            padding = EdgeInsets.all(1),
            activeColor = ACTIVE_COLOR,
            trackColor = TRACK_COLOR,
            borderColor = secondBorder,
            borderWidth = 1,
            cornerRadius = 0,
            shadowColor = null,
            shadowOffset = 0,
        )
        assertEquals(secondBorder, paint(render).getPixel(0, 0))

        render.update(
            value = 0.5f,
            onDrag = onDrag,
            onRelease = onRelease,
            onPressedChanged = null,
            onHoveredChanged = null,
            enabled = true,
            minimumWidth = WIDTH,
            height = HEIGHT,
            padding = EdgeInsets.all(1),
            activeColor = ACTIVE_COLOR,
            trackColor = TRACK_COLOR,
            borderColor = secondBorder,
            borderWidth = 1,
            cornerRadius = 2,
            shadowColor = null,
            shadowOffset = 0,
        )
        assertEquals(PixelColor.Transparent, paint(render).getPixel(0, 0))
        assertEquals(secondBorder, paint(render).getPixel(1, 0))
    }

    /** Paints [render] into a fresh transparent buffer for one exact assertion frame. */
    private fun paint(render: RenderSlider): PixelBuffer {
        /** Fresh buffer preventing pixels from the previous retained frame from surviving. */
        val buffer = PixelBuffer(width = WIDTH, height = HEIGHT).also(PixelBuffer::clear)
        render.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        return buffer
    }

    private companion object {
        /** Tight logical render width. */
        const val WIDTH: Int = 9

        /** Tight logical render height. */
        const val HEIGHT: Int = 7

        /** Stable active-range color kept unchanged across rethemes. */
        val ACTIVE_COLOR: PixelColor = PixelColor.fromRgb(43, 199, 113)

        /** Stable background-track color kept unchanged across rethemes. */
        val TRACK_COLOR: PixelColor = PixelColor.fromRgb(17, 29, 47)
    }
}
