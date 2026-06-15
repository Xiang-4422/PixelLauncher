package com.purride.pixelui

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRenderResult
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelInspectorTargetAssociationTest {
    @Test
    fun targetSnapshotsLinkToExactElementAndRenderPaths() {
        val runtime = PixelUiRuntime()
        try {
            val result = runtime.render(
                root = GestureDetector(
                    child = Text("OK"),
                    onTap = { },
                ),
                logicalWidth = 32,
                logicalHeight = 16,
            )
            val snapshots = result.toInspectorTargetSnapshots(runtime.collectInspectorNodeAssociations())
            val click = snapshots.single { it.kind == PixelInspectorTargetKind.CLICK }
            val semantics = snapshots.single { it.kind == PixelInspectorTargetKind.SEMANTICS }

            assertNotNull(click.elementPath)
            assertTrue(click.elementPath!!.endsWith("0:GestureDetectorWidget"))
            assertTrue(click.renderPath!!.endsWith("0:RenderSurface"))
            assertTrue(semantics.elementPath!!.endsWith("0:TextWidget"))
            assertTrue(semantics.renderPath!!.endsWith("0:RenderText"))
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun targetsWithoutRuntimeSourceRemainExplicitlyUnlinked() {
        val result = PixelRenderResult(
            buffer = PixelBuffer(width = 1, height = 1),
            clickTargets = listOf(
                PixelClickTarget(
                    bounds = PixelRect(left = 0, top = 0, width = 1, height = 1),
                    onClick = { },
                ),
            ),
            pagerTargets = emptyList(),
            listTargets = emptyList(),
            scrollbarTargets = emptyList(),
            refreshTargets = emptyList(),
            textInputTargets = emptyList(),
            sliderTargets = emptyList(),
            semanticsNodes = emptyList(),
        )
        val target = result.toInspectorTargetSnapshots(emptyMap()).single()

        assertEquals(null, target.elementPath)
        assertEquals(null, target.renderPath)
    }
}
