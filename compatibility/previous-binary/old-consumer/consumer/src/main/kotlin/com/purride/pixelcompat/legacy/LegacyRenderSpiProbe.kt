package com.purride.pixelcompat.legacy

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.testing.PixelTester

/** Entry point compiled once against the frozen SPI and invoked reflectively by the current runner. */
public object LegacyRenderSpiProbe {
    /** Runs deterministic retained creation, layout, paint, and update behavior on the runtime engine. */
    @JvmStatic
    public fun run(): String {
        /** Counters proving that a stable key reuses the retained render object. */
        val counters = LegacyCounters()
        /** Initial deterministic paint color. */
        val firstColor = PixelColor.fromRgb(11, 22, 33)
        /** Updated deterministic paint color. */
        val secondColor = PixelColor.fromRgb(210, 120, 30)
        /** Public test harness resolved only at runtime by the current runner. */
        val tester = PixelTester()
        tester.pumpWidget(
            widget = LegacyDotWidget(firstColor, counters, key = "legacy-dot"),
            logicalWidth = 5,
            logicalHeight = 4,
        )
        /** Whether the first frame exactly matches the frozen consumer's expectation. */
        val firstFrameMatches = tester.pixelAt(2, 1) == firstColor
        tester.pumpWidget(
            widget = LegacyDotWidget(secondColor, counters, key = "legacy-dot"),
            logicalWidth = 5,
            logicalHeight = 4,
        )
        /** Whether the updated frame was repainted by the same retained object. */
        val secondFrameMatches = tester.pixelAt(2, 1) == secondColor
        tester.dispose()
        return "create=${counters.createCount};update=${counters.updateCount};" +
            "first=$firstFrameMatches;second=$secondFrameMatches"
    }
}

/** Mutable counters embedded in the old binary to detect accidental recreation. */
private class LegacyCounters {
    /** Number of RenderObject factory calls. */
    var createCount: Int = 0

    /** Number of retained configuration updates. */
    var updateCount: Int = 0
}

/** Immutable Widget compiled against the frozen real SPI baseline. */
private class LegacyDotWidget(
    /** Color copied into the retained object. */
    private val color: PixelColor,
    /** Shared counters observed by the reflective probe. */
    private val counters: LegacyCounters,
    key: Any?,
) : PixelLeafRenderObjectWidget(key) {
    /** Creates one old consumer render object. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        counters.createCount += 1
        return LegacyDotRenderObject(color)
    }

    /** Applies a new immutable color to the existing old consumer render object. */
    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        counters.updateCount += 1
        (renderObject as LegacyDotRenderObject).updateColor(color)
    }
}

/** Consumer-owned RenderBox whose bytecode is never recompiled against the current engine. */
private class LegacyDotRenderObject(
    /** Color painted by the current retained configuration. */
    private var color: PixelColor,
) : PixelRenderBox() {
    /** Selects a deterministic 3-by-2 dot. */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(3),
            height = constraints.constrainHeight(2),
        )
    }

    /** Paints the complete selected dot size. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** Updates paint state and invalidates only when the color changes. */
    fun updateColor(nextColor: PixelColor) {
        if (color == nextColor) {
            return
        }
        color = nextColor
        markNeedsPaint()
    }
}
