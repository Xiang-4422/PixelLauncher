package com.purride.pixelcompat.external

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize

/** Mutable counters proving retained creation and update behavior through only the published AAR. */
public class ExternalDotStats {
    /** Number of public render-object factory calls. */
    public var createCount: Int = 0
        internal set

    /** Number of immutable Widget updates applied to the retained object. */
    public var updateCount: Int = 0
        internal set
}

/**
 * Example third-party leaf Widget compiled in a build that has no access to pixel-engine source code.
 *
 * @property color Color painted by the retained dot.
 * @property stats Counters used by the compatibility test.
 * @property key Optional identity preserving the retained object across Widget replacements.
 */
public class ExternalDotWidget(
    public val color: PixelColor,
    public val stats: ExternalDotStats,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {
    /** Creates one consumer-owned retained render object. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        stats.createCount += 1
        return ExternalDotRenderObject(color)
    }

    /** Applies an immutable color update without replacing the retained object. */
    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        stats.updateCount += 1
        (renderObject as ExternalDotRenderObject).updateColor(color)
    }
}

/** Public SPI render box implemented entirely outside the pixel-engine repository build. */
public class ExternalDotRenderObject(
    /** Current color copied from the latest immutable Widget. */
    private var color: PixelColor,
) : PixelRenderBox() {
    /** Selects a deterministic 4-by-3 dot within parent constraints. */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(4),
            height = constraints.constrainHeight(3),
        )
    }

    /** Paints the complete selected size using the current dot color. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** Applies a new color and requests paint only when the value changed. */
    public fun updateColor(nextColor: PixelColor) {
        if (color == nextColor) {
            return
        }
        color = nextColor
        markNeedsPaint()
    }
}
