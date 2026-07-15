@file:OptIn(PixelExperimentalApi::class)

package com.purride.pixelui.advanced

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Container
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.Popover
import com.purride.pixelui.Positioned
import com.purride.pixelui.Semantics
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Test

/** Behavior tests for the public advanced RenderObject SPI and its retained-pipeline adapters. */
class PixelRenderObjectSpiTest {
    /** Verifies retained updates reuse the public object and propagate paint invalidation. */
    @Test
    fun leafWidgetCreatesOnceUpdatesAndRepaints() {
        /** Lifecycle counters shared across immutable Widget replacements. */
        val counters = RenderObjectCounters()
        /** Initial and updated colors used for exact pixel assertions. */
        val initialColor = PixelColor.fromRgb(10, 20, 30)
        val updatedColor = PixelColor.fromRgb(90, 80, 70)
        /** Off-screen SDK harness driving the real retained pipeline. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = CountingLeafWidget(initialColor, counters, key = "stable-leaf"),
            logicalWidth = 6,
            logicalHeight = 4,
        )

        assertEquals(1, counters.createCount)
        assertEquals(1, counters.updateCount)
        assertEquals(1, counters.attachCount)
        assertEquals(initialColor, tester.pixelAt(1, 1))

        tester.pumpWidget(
            widget = CountingLeafWidget(updatedColor, counters, key = "stable-leaf"),
            logicalWidth = 6,
            logicalHeight = 4,
        )

        assertEquals(1, counters.createCount)
        assertEquals(2, counters.updateCount)
        assertEquals(updatedColor, tester.pixelAt(1, 1))
        tester.dispose()
        assertEquals(1, counters.detachCount)
    }

    /** Verifies a public single-child object can lay out and paint an adapted retained child. */
    @Test
    fun singleChildWidgetReceivesPublicBoxFacade() {
        /** Distinct parent and child colors proving both paint paths execute at expected offsets. */
        val borderColor = PixelColor.fromRgb(200, 30, 40)
        val childColor = PixelColor.fromRgb(20, 180, 70)
        /** Off-screen SDK harness driving the real retained pipeline. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = InsetWidget(
                borderColor = borderColor,
                child = SolidLeafWidget(childColor, width = 2, height = 2),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(borderColor, tester.pixelAt(0, 0))
        assertEquals(childColor, tester.pixelAt(1, 1))
        assertEquals(childColor, tester.pixelAt(2, 2))
        tester.dispose()
    }

    /** Verifies a public multi-child object receives ordered facades and paints both children. */
    @Test
    fun multiChildWidgetPreservesChildOrder() {
        /** Colors identify the first and second child positions. */
        val firstColor = PixelColor.fromRgb(220, 100, 20)
        val secondColor = PixelColor.fromRgb(30, 120, 230)
        /** Off-screen SDK harness driving the real retained pipeline. */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = HorizontalWidget(
                children = listOf(
                    SolidLeafWidget(firstColor, width = 2, height = 2),
                    SolidLeafWidget(secondColor, width = 2, height = 2),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 2,
        )

        assertEquals(firstColor, tester.pixelAt(0, 0))
        assertEquals(firstColor, tester.pixelAt(1, 1))
        assertEquals(secondColor, tester.pixelAt(2, 0))
        assertEquals(secondColor, tester.pixelAt(3, 1))
        tester.dispose()
    }

    /** Verifies the public experimental hit result preserves insertion order and identity. */
    @Test
    fun hitTestResultPreservesConsumerTargets() {
        /** Render object whose hit region covers its selected layout size. */
        val renderObject = SolidRenderObject(
            color = PixelColor.White,
            requestedWidth = 3,
            requestedHeight = 2,
        )
        renderObject.layout(PixelRenderConstraints(maxWidth = 3, maxHeight = 2))
        /** Public hit path populated directly by the consumer implementation. */
        val result = PixelHitTestResult()

        renderObject.hitTest(localX = 1, localY = 1, result = result)

        assertEquals(listOf(renderObject), result.hits)
    }

    /** An adapted child portal retains the scratch-buffer-to-Host transform through public facades. */
    @Test
    fun singleChildFacadePreservesLiftedOverlayGlobalOrigin() {
        /** Off-screen SDK harness used to inspect the lifted semantic rectangle. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = MediaQuery(
                    data = MediaQueryData(
                        logicalWidth = 80,
                        logicalHeight = 60,
                        screenProfile = ScreenProfile(logicalWidth = 80, logicalHeight = 60, dotSizePx = 1),
                    ),
                    child = Stack(
                        children = listOf(
                            Positioned(
                                left = 30,
                                top = 20,
                                width = 6,
                                height = 6,
                                child = ClipRect(
                                    child = PassthroughWidget(
                                        child = Popover(
                                            anchor = Container(
                                                width = 6,
                                                height = 6,
                                                fillColor = PixelColor.White,
                                                borderColor = null,
                                            ),
                                            content = Semantics(
                                                label = "advanced-popup",
                                                child = Container(
                                                    width = 20,
                                                    height = 12,
                                                    fillColor = PixelColor.Black,
                                                    borderColor = null,
                                                ),
                                            ),
                                            expanded = true,
                                            contentOffset = IntOffset(0, 8),
                                            modal = false,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 60,
            )

            /** Lifted popup whose origin must include the Positioned and ClipRect offsets. */
            val popup = tester.semanticsNodesByLabel("advanced-popup").single()
            assertEquals(30, popup.left)
            assertEquals(28, popup.top)
        } finally {
            tester.dispose()
        }
    }
}

/** Mutable counters used to prove retained creation, update, attach, and detach semantics. */
private class RenderObjectCounters {
    /** Number of public RenderObject factory calls. */
    var createCount: Int = 0

    /** Number of Widget-to-RenderObject update calls. */
    var updateCount: Int = 0

    /** Number of public attach lifecycle callbacks. */
    var attachCount: Int = 0

    /** Number of public detach lifecycle callbacks. */
    var detachCount: Int = 0
}

/** Immutable leaf Widget used to verify retained public SPI updates. */
private class CountingLeafWidget(
    /** Color copied into the retained render object. */
    private val color: PixelColor,
    /** Shared lifecycle counters observed by the test. */
    private val counters: RenderObjectCounters,
    key: Any?,
) : PixelLeafRenderObjectWidget(key) {
    /** Creates the consumer-owned retained leaf exactly once for a stable key and type. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        counters.createCount += 1
        return CountingLeafRenderObject(color, counters)
    }

    /** Copies immutable color configuration into the retained leaf. */
    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        counters.updateCount += 1
        (renderObject as CountingLeafRenderObject).updateColor(color)
    }
}

/** Retained leaf that exposes lifecycle and paint invalidation behavior to the test. */
private class CountingLeafRenderObject(
    /** Current paint color. */
    private var color: PixelColor,
    /** Shared lifecycle counters observed by the test. */
    private val counters: RenderObjectCounters,
) : PixelRenderBox() {
    /** Selects the complete available box so every test pixel is deterministic. */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Fills the selected box with the current color. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** Records attachment through the stable protected lifecycle hook. */
    override fun onAttach() {
        counters.attachCount += 1
    }

    /** Records detachment through the stable protected lifecycle hook. */
    override fun onDetach() {
        counters.detachCount += 1
    }

    /** Updates paint state and invalidates only when the color changed. */
    fun updateColor(nextColor: PixelColor) {
        if (color == nextColor) {
            return
        }
        color = nextColor
        markNeedsPaint()
    }
}

/** Minimal immutable leaf Widget shared by single- and multi-child behavior tests. */
private class SolidLeafWidget(
    /** Solid paint color. */
    private val color: PixelColor,
    /** Requested width before parent constraint clamping. */
    private val width: Int,
    /** Requested height before parent constraint clamping. */
    private val height: Int,
) : PixelLeafRenderObjectWidget() {
    /** Creates a retained solid-color public render object. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return SolidRenderObject(color, width, height)
    }
}

/** Solid-color public render box used as an adapted child. */
private class SolidRenderObject(
    /** Color painted into the selected size. */
    private val color: PixelColor,
    /** Requested width before clamping. */
    private val requestedWidth: Int,
    /** Requested height before clamping. */
    private val requestedHeight: Int,
) : PixelRenderBox() {
    /** Clamps the requested size to parent constraints. */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(requestedWidth),
            height = constraints.constrainHeight(requestedHeight),
        )
    }

    /** Paints the complete selected size with [color]. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, color)
    }

    /** Adds this object when the local point lies inside its selected size. */
    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        if (localX in 0 until size.width && localY in 0 until size.height) {
            result.add(this)
        }
    }
}

/** Immutable experimental Widget that wraps one child in a one-pixel border inset. */
private class InsetWidget(
    /** Color painted by the parent around its child. */
    private val borderColor: PixelColor,
    child: Widget,
) : PixelSingleChildRenderObjectWidget(child) {
    /** Creates the retained single-child public render object. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return InsetRenderObject(borderColor)
    }
}

/** Experimental identity wrapper used to exercise public-to-internal child paint context bridging. */
private class PassthroughWidget(
    child: Widget,
) : PixelSingleChildRenderObjectWidget(child) {
    /** Creates the public render object that delegates every box operation unchanged. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return PassthroughRenderObject()
    }
}

/** Public single-child render box that preserves its child's layout, paint, and hit coordinates. */
private class PassthroughRenderObject : PixelSingleChildRenderObject() {
    /** Gives the child identical constraints and adopts its selected size. */
    override fun layout(constraints: PixelRenderConstraints) {
        /** Adapted internal child exposed through the stable public box facade. */
        val childBox = child as? PixelRenderBox
        childBox?.layout(constraints)
        size = childBox?.size ?: PixelRenderSize.Zero
    }

    /** Delegates paint with the exact context and offset supplied by the engine bridge. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        (child as? PixelRenderBox)?.paint(context, offsetX, offsetY)
    }

    /** Delegates hit testing without changing the child's local coordinate system. */
    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        (child as? PixelRenderBox)?.hitTest(localX, localY, result)
    }
}

/** Public single-child box that delegates layout and painting through its child facade. */
private class InsetRenderObject(
    /** Border color painted before the child. */
    private val borderColor: PixelColor,
) : PixelSingleChildRenderObject() {
    /** Lays out the child within a one-pixel inset and fills the available parent box. */
    override fun layout(constraints: PixelRenderConstraints) {
        /** Box facade installed by the internal adapter. */
        val childBox = child as? PixelRenderBox
        childBox?.layout(constraints.inset(left = 1, top = 1, right = 1, bottom = 1))
        size = PixelRenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Paints the border and then the child at the matching inset. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, borderColor)
        (child as? PixelRenderBox)?.paint(context, offsetX + 1, offsetY + 1)
    }
}

/** Immutable experimental Widget that arranges ordered children in one horizontal row. */
private class HorizontalWidget(
    children: List<Widget>,
) : PixelMultiChildRenderObjectWidget(children) {
    /** Creates the retained multi-child public render object. */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return HorizontalRenderObject()
    }
}

/** Public multi-child box that assigns equal-width horizontal slots in child order. */
private class HorizontalRenderObject : PixelMultiChildRenderObject() {
    /** Width assigned to each child during the latest layout pass. */
    private var childWidth: Int = 0

    /** Lays out every child into an equal-width slot and fills the available parent box. */
    override fun layout(constraints: PixelRenderConstraints) {
        childWidth = if (children.isEmpty()) 0 else constraints.maxWidth / children.size
        children.forEach { child ->
            (child as? PixelRenderBox)?.layout(
                PixelRenderConstraints(maxWidth = childWidth, maxHeight = constraints.maxHeight),
            )
        }
        size = PixelRenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    /** Paints child facades left to right in their retained order. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        children.forEachIndexed { index, child ->
            (child as? PixelRenderBox)?.paint(
                context = context,
                offsetX = offsetX + index * childWidth,
                offsetY = offsetY,
            )
        }
    }
}
