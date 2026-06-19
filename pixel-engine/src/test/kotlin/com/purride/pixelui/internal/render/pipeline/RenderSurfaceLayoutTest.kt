package com.purride.pixelui.internal

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderSurfaceLayoutTest {

    @Test
    fun surfaceLoosensChildMinConstraintsByDefault() {
        val child = RecordingRenderBox()
        val surface = RenderSurface(child = child)

        surface.layout(
            RenderConstraints(
                minWidth = 20,
                maxWidth = 20,
                minHeight = 8,
                maxHeight = 8,
            ),
        )

        assertEquals(0, child.lastConstraints.minWidth)
        assertEquals(0, child.lastConstraints.minHeight)
        assertEquals(3, child.size.width)
        assertEquals(2, child.size.height)
        assertEquals(20, surface.size.width)
        assertEquals(8, surface.size.height)
    }

    @Test
    fun surfaceCanPreserveChildMinConstraintsForProxyWidgets() {
        val child = RecordingRenderBox()
        val surface = RenderSurface(
            child = child,
            preserveChildMinConstraints = true,
        )

        surface.layout(
            RenderConstraints(
                minWidth = 20,
                maxWidth = 20,
                minHeight = 8,
                maxHeight = 8,
            ),
        )

        assertEquals(20, child.lastConstraints.minWidth)
        assertEquals(8, child.lastConstraints.minHeight)
        assertEquals(20, child.size.width)
        assertEquals(8, child.size.height)
        assertEquals(20, surface.size.width)
        assertEquals(8, surface.size.height)
    }

    private class RecordingRenderBox : RenderBox() {
        lateinit var lastConstraints: RenderConstraints

        override fun layout(constraints: RenderConstraints) {
            lastConstraints = constraints
            size = RenderSize(
                width = constraints.constrainWidth(3),
                height = constraints.constrainHeight(2),
            )
        }

        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) = Unit
    }
}
