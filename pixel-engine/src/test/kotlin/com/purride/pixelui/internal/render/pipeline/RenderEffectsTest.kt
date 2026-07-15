package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderEffectsTest {
    /** Intermediate group opacity blends child pixels into the existing destination. */
    @Test
    fun opacityBlendsChildIntoDestination() {
        val child = SolidBox(width = 2, height = 2, color = PixelColor.fromRgb(255, 0, 0))
        val opacity = RenderOpacity(child = child, opacity = 0.5f)
        opacity.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))
        val buffer = PixelBuffer(width = 2, height = 2)
        buffer.fillRect(0, 0, 2, 2, PixelColor.fromRgb(0, 0, 255))

        opacity.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        val pixel = buffer.getPixel(0, 0)
        assertTrue(pixel.red in 127..129)
        assertTrue(pixel.blue in 126..128)
    }

    /** 0/25/50/75/100% share one explicit paint, hit, target and semantics policy. */
    @Test
    fun opacityMilestonesApplyPaintHitTargetAndSemanticsContract() {
        val milestones = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        milestones.forEach { milestone ->
            val child = SolidBox(
                width = 2,
                height = 2,
                color = PixelColor.fromRgb(255, 64, 16),
                onClick = {},
                semanticsLabel = "opacity-child",
            )
            val opacity = RenderOpacity(child = child, opacity = milestone)
            opacity.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))
            val buffer = PixelBuffer(width = 2, height = 2)
            val hitResult = HitTestResult()
            val clickTargets = mutableListOf<PixelClickTarget>()
            val semanticsTargets = mutableListOf<PixelSemanticsTarget>()

            opacity.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
            opacity.hitTest(localX = 1, localY = 1, result = hitResult)
            opacity.collectClickTargets(offsetX = 0, offsetY = 0, targets = clickTargets)
            opacity.collectSemantics(offsetX = 0, offsetY = 0, targets = semanticsTargets)

            val expectedVisible = milestone > 0f
            val expectedAlpha = (milestone * 255f + 0.5f).toInt()
            assertEquals("alpha at $milestone", expectedAlpha, buffer.getPixel(0, 0).alpha)
            assertEquals("paint count at $milestone", if (expectedVisible) 1 else 0, child.paintCount)
            assertEquals("hit count at $milestone", if (expectedVisible) 1 else 0, hitResult.hits.size)
            assertEquals("click count at $milestone", if (expectedVisible) 1 else 0, clickTargets.size)
            assertEquals("semantics count at $milestone", if (expectedVisible) 1 else 0, semanticsTargets.size)
            if (expectedVisible) {
                assertEquals("opacity-child", semanticsTargets.single().node.label)
            }
        }
    }

    /** Opacity clamps finite values and treats NaN/infinity as the fully hidden safe state. */
    @Test
    fun opacityNormalizesInvalidAndOutOfRangeValues() {
        val opacity = RenderOpacity(opacity = Float.NaN)

        assertEquals(0f, opacity.effectiveOpacity, 0f)
        opacity.updateOpacity(Float.POSITIVE_INFINITY)
        assertEquals(0f, opacity.effectiveOpacity, 0f)
        opacity.updateOpacity(-2f)
        assertEquals(0f, opacity.effectiveOpacity, 0f)
        opacity.updateOpacity(2f)
        assertEquals(1f, opacity.effectiveOpacity, 0f)
    }

    /** Exit visuals keep exact paint/layout while exporting no hit, click, or semantics channel. */
    @Test
    fun visualOnlyPaintsWithoutInteractionOrSemantics() {
        val child = SolidBox(
            width = 2,
            height = 2,
            color = PixelColor.fromRgb(32, 96, 224),
            onClick = {},
            semanticsLabel = "dismissed-dialog",
        )
        val visualOnly = RenderVisualOnly(child = child)
        val constraints = RenderConstraints(maxWidth = 3, maxHeight = 3)
        val buffer = PixelBuffer(width = 3, height = 3)
        val hitResult = HitTestResult()
        val clickTargets = mutableListOf<PixelClickTarget>()
        val semanticsTargets = mutableListOf<PixelSemanticsTarget>()

        visualOnly.layout(constraints)
        visualOnly.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)
        visualOnly.hitTest(localX = 1, localY = 1, result = hitResult)
        visualOnly.collectClickTargets(offsetX = 0, offsetY = 0, targets = clickTargets)
        visualOnly.collectSemantics(offsetX = 0, offsetY = 0, targets = semanticsTargets)

        assertEquals(RenderSize(2, 2), visualOnly.size)
        assertEquals(PixelColor.fromRgb(32, 96, 224), buffer.getPixel(1, 1))
        assertEquals(1, child.paintCount)
        assertTrue(hitResult.hits.isEmpty())
        assertTrue(clickTargets.isEmpty())
        assertTrue(semanticsTargets.isEmpty())

        visualOnly.updateVisualOnly(false)
        visualOnly.hitTest(localX = 1, localY = 1, result = hitResult)
        visualOnly.collectClickTargets(offsetX = 0, offsetY = 0, targets = clickTargets)
        visualOnly.collectSemantics(offsetX = 0, offsetY = 0, targets = semanticsTargets)

        assertEquals(1, hitResult.hits.size)
        assertEquals(1, clickTargets.size)
        assertEquals("dismissed-dialog", semanticsTargets.single().node.label)
    }

    @Test
    fun clipRectClipsChildPaintToLayoutBox() {
        val child = SolidBox(width = 4, height = 4, color = PixelColor.White)
        val clip = RenderClipRect(child = child)
        clip.layout(RenderConstraints(maxWidth = 2, maxHeight = 2))
        val buffer = PixelBuffer(width = 4, height = 4)

        clip.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        assertEquals(PixelColor.White.argb, buffer.getPixel(1, 1).argb)
        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(2, 0).argb)
        assertEquals(PixelColor.Transparent.argb, buffer.getPixel(0, 2).argb)
    }

    @Test
    fun translateMovesChildPaintAndTargets() {
        var clicked = false
        val child = SolidBox(width = 1, height = 1, color = PixelColor.White, onClick = { clicked = true })
        val translate = RenderTranslate(child = child, dx = 2, dy = 1)
        translate.layout(RenderConstraints(maxWidth = 4, maxHeight = 4))
        val buffer = PixelBuffer(width = 4, height = 4)

        translate.paint(PaintContext(buffer), offsetX = 0, offsetY = 0)

        assertEquals(PixelColor.White.argb, buffer.getPixel(2, 1).argb)
        assertNotEquals(PixelColor.White.argb, buffer.getPixel(0, 0).argb)
        val targets = mutableListOf<PixelClickTarget>()
        translate.collectClickTargets(0, 0, targets)
        assertEquals(1, targets.size)
        assertEquals(PixelRect(2, 1, 1, 1), targets.single().bounds)
        targets.single().onClick()
        assertTrue(clicked)
    }

    /** Minimal render child that exposes paint, hit, click and semantics participation. */
    private class SolidBox(
        /** Requested logical width before constraints. */
        private val width: Int,
        /** Requested logical height before constraints. */
        private val height: Int,
        /** Solid color written by [paint]. */
        private val color: PixelColor,
        /** Optional click callback enabling hit and click-target collection. */
        private val onClick: (() -> Unit)? = null,
        /** Optional label enabling semantics collection. */
        private val semanticsLabel: String? = null,
    ) : RenderBox() {
        /** Number of real child paint invocations. */
        var paintCount: Int = 0
            private set

        /** Resolves the constrained render size. */
        override fun layout(constraints: RenderConstraints) {
            size = RenderSize(
                width = constraints.constrainWidth(width),
                height = constraints.constrainHeight(height),
            )
        }

        /** Records and fills one solid logical rectangle. */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
            paintCount += 1
            context.fillRect(offsetX, offsetY, size.width, size.height, color)
        }

        /** Adds this child only when click handling is enabled and the point is in bounds. */
        override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
            if (onClick == null) return
            if (localX !in 0 until size.width || localY !in 0 until size.height) return
            result.add(this)
        }

        /** Exports the optional click target. */
        override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
            val callback = onClick ?: return
            targets += PixelClickTarget(PixelRect(offsetX, offsetY, size.width, size.height), callback)
        }

        /** Exports the optional semantics node. */
        override fun collectSemantics(
            offsetX: Int,
            offsetY: Int,
            targets: MutableList<PixelSemanticsTarget>,
        ) {
            val label = semanticsLabel ?: return
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    label = label,
                    role = PixelSemanticRole.BUTTON,
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
