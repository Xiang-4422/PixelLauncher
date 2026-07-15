package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies root-lifted presentations and later Stack siblings share one pipeline z-order. */
class LiftedOverlayPipelineOrderTest {
    /** Paint, exported targets, semantics, and raw hit results all retain bottom-to-top order. */
    @Test
    fun laterStackSiblingReplaysAfterLiftedPortalPresentation() {
        /** Anchor geometry source paired with the manually assembled retained portal. */
        val link = PixelAnchoredOverlayLink()
        /** Transparent in-flow anchor that registers the lower presentation during paint. */
        val anchor = OrderedTestBox(
            width = 1,
            height = 1,
            color = PixelColor.Transparent,
            label = null,
        )
        /** Red presentation lifted out of the portal into Host-root coordinates. */
        val lowerPresentation = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(255, 0, 0),
            label = "LOWER",
        )
        /** Portal whose second branch is absent from normal in-flow traversal. */
        val portal = RenderAnchoredOverlayPortal(link = link, viewportWidth = 8, viewportHeight = 8)
        portal.setRenderObjectChildren(listOf(anchor, lowerPresentation))
        /** Blue ordinary sibling that must be deferred after the lower presentation plane. */
        val higherSibling = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(0, 0, 255),
            label = "HIGHER",
        )
        /** Pipeline owner completing paint and raw hit traversal for the retained test tree. */
        val owner = PipelineOwner(root = RenderStack(children = listOf(portal, higherSibling)))

        /** Completed frame whose channel order must match the final overlap pixel. */
        val frame = owner.render(logicalWidth = 8, logicalHeight = 8)
        /** Raw pipeline hit list before any gesture router chooses its topmost entry. */
        val hits = owner.hitTest(x = 1, y = 1).hits

        assertEquals(PixelColor.fromRgb(0, 0, 255), frame.buffer.getPixel(1, 1))
        assertEquals(listOf("LOWER", "HIGHER"), frame.semanticsNodes.map(PixelSemanticsNode::label))
        assertSame(lowerPresentation, frame.clickTargets[0].source)
        assertSame(higherSibling, frame.clickTargets[1].source)
        assertSame(lowerPresentation, hits[0])
        assertSame(higherSibling, hits[1])
        owner.dispose()
    }

    /** Scratch ancestors retain their effects while replaying a higher sibling after the portal. */
    @Test
    fun opacityAndClipScratchKeepHigherSiblingLastAcrossAllChannels() {
        /** Geometry source paired with the lower portal inside both scratch-producing ancestors. */
        val link = PixelAnchoredOverlayLink()
        /** Transparent anchor whose paint registers the lower red presentation. */
        val anchor = OrderedTestBox(
            width = 1,
            height = 1,
            color = PixelColor.Transparent,
            label = null,
        )
        /** Lower red presentation that must remain below the captured blue sibling. */
        val lowerPresentation = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(255, 0, 0),
            label = "SCRATCH LOWER",
        )
        /** Retained portal painted from the nested opacity/clip scratch coordinate system. */
        val portal = RenderAnchoredOverlayPortal(link = link, viewportWidth = 8, viewportHeight = 8)
        portal.setRenderObjectChildren(listOf(anchor, lowerPresentation))
        /** Higher blue sibling captured before the ancestor scratch buffers are released. */
        val higherSibling = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(0, 0, 255),
            label = "SCRATCH HIGHER",
        )
        /** Stack whose portal registration forces the later sibling into a captured raster plane. */
        val orderedStack = RenderStack(children = listOf(portal, higherSibling))
        /** Clip scratch proving the higher sibling does not escape its original raster extent. */
        val clipped = RenderClipRect(child = orderedStack)
        /** Group opacity scratch proving inherited alpha survives deferred plane replay. */
        val root = RenderOpacity(child = clipped, opacity = 0.5f)
        /** Pipeline owner exposing final visual, target, semantic, and raw hit order. */
        val owner = PipelineOwner(root = root)

        /** Completed frame after both scratch contexts have returned their temporary buffers. */
        val frame = owner.render(logicalWidth = 8, logicalHeight = 8)
        /** Raw hit order sorted with the same captured plane ranks as every target family. */
        val hits = owner.hitTest(x = 1, y = 1).hits
        /** Overlap pixel whose stronger blue channel proves the higher plane painted last. */
        val overlap = frame.buffer.getPixel(1, 1)

        assertTrue(overlap.blue > overlap.red)
        assertTrue(overlap.alpha in 1..254)
        assertEquals(
            listOf("SCRATCH LOWER", "SCRATCH HIGHER"),
            frame.semanticsNodes.map(PixelSemanticsNode::label),
        )
        assertSame(lowerPresentation, frame.clickTargets[0].source)
        assertSame(higherSibling, frame.clickTargets[1].source)
        assertSame(lowerPresentation, hits[0])
        assertSame(higherSibling, hits[1])
        owner.dispose()
    }

    /** Captured sibling pixels retain nested fitted scaling and translation in Host coordinates. */
    @Test
    fun fittedTranslateScratchMapsCapturedSiblingBackToGlobalPlane() {
        /** Link receiving the translated and scaled global anchor bounds from the portal. */
        val link = PixelAnchoredOverlayLink()
        /** Transparent one-pixel anchor painted at translated local x=1 and global x=2. */
        val anchor = OrderedTestBox(
            width = 1,
            height = 1,
            color = PixelColor.Transparent,
            label = null,
        )
        /** Lower red presentation replayed directly in Host coordinates. */
        val lowerPresentation = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(255, 0, 0),
            label = "SCALED LOWER",
        )
        /** Portal configured for the four-pixel local viewport before the two-times fit. */
        val portal = RenderAnchoredOverlayPortal(link = link, viewportWidth = 4, viewportHeight = 4)
        portal.setRenderObjectChildren(listOf(anchor, lowerPresentation))
        /** Higher blue sibling whose captured local pixels must scale to the Host plane. */
        val higherSibling = OrderedTestBox(
            width = 4,
            height = 4,
            color = PixelColor.fromRgb(0, 0, 255),
            label = "SCALED HIGHER",
        )
        /** Local Stack producing portal and captured-sibling planes in order. */
        val orderedStack = RenderStack(children = listOf(portal, higherSibling))
        /** One-pixel local translation that becomes two Host pixels after fitting. */
        val translated = RenderTranslate(child = orderedStack, dx = 1, dy = 0)
        /** Four-pixel natural child extent forcing the surrounding fit to a two-times scale. */
        val constrained = RenderConstrainedBox(
            child = translated,
            additionalConstraints = PixelBoxConstraints(maxWidth = 4, maxHeight = 4),
        )
        /** Scratch-producing fitted root carrying the exact rational global mapping. */
        val root = RenderFittedBox(child = constrained)
        /** Pipeline owner used to validate scaled paint, semantics, and raw hit order. */
        val owner = PipelineOwner(root = root)

        /** Completed eight-pixel frame after local capture is mapped through the fit transform. */
        val frame = owner.render(logicalWidth = 8, logicalHeight = 8)
        /** Raw overlap hit inside translated global coordinates. */
        val hits = owner.hitTest(x = 3, y = 1).hits

        assertEquals(PixelColor.fromRgb(0, 0, 255), frame.buffer.getPixel(3, 1))
        assertEquals(
            listOf("SCALED LOWER", "SCALED HIGHER"),
            frame.semanticsNodes.map(PixelSemanticsNode::label),
        )
        assertSame(lowerPresentation, hits[0])
        assertSame(higherSibling, hits[1])
        owner.dispose()
    }

    /** Fixed render box exposing one visual, click target, semantic node, and raw hit source. */
    private class OrderedTestBox(
        /** Requested logical width clamped by parent constraints. */
        private val width: Int,
        /** Requested logical height clamped by parent constraints. */
        private val height: Int,
        /** Pixel color filled over the complete measured box. */
        private val color: PixelColor,
        /** Optional exported target label; null keeps the in-flow anchor inert. */
        private val label: String?,
    ) : RenderBox() {
        /** Resolves the requested fixed extent inside the supplied parent constraints. */
        override fun layout(constraints: RenderConstraints) {
            size = RenderSize(
                width = constraints.constrainWidth(width),
                height = constraints.constrainHeight(height),
            )
        }

        /** Fills the measured box so overlap order is observable in the final frame. */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
            context.buffer.fillRect(offsetX, offsetY, size.width, size.height, color)
        }

        /** Adds this source only when labeled and the point lies inside its measured bounds. */
        override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
            if (label != null && localX in 0 until size.width && localY in 0 until size.height) {
                result.add(this)
            }
        }

        /** Exports one click target for labeled visual planes. */
        override fun collectClickTargets(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelClickTarget>,
        ) {
            if (label == null) return
            targets += PixelClickTarget(
                bounds = PixelRect(offsetX, offsetY, size.width, size.height),
                onClick = { },
                source = this,
            )
        }

        /** Exports one semantic node for labeled visual planes. */
        override fun collectSemantics(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelSemanticsTarget>,
        ) {
            /** Stable label proving this render plane's position in semantic traversal. */
            val semanticLabel = label ?: return
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    id = semanticNodeId(),
                    label = semanticLabel,
                    role = PixelSemanticRole.GENERIC,
                    enabled = true,
                    focused = false,
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                source = this,
            )
        }
    }
}
