package com.purride.pixelui.internal.widgets

import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.ElementDiagnosticsNode
import com.purride.pixelui.internal.ElementTreeBuildRuntimeFactory
import com.purride.pixelui.internal.UnsupportedWidgetAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证常见 widget 组合的 Element 树形结构。
 *
 * 使用 [ElementTreeBuildRuntimeFactory] + [resolveElementTree] +
 * [collectDiagnostics] 断言构建后的树节点名称与层级，确保 widget
 * 组合映射到正确的 element/render object 结构。
 */
class WidgetCompositionTest {

    // ── Row(Text, Button) ────────────────────────────────────────────────

    @Test
    fun `Row of Text and Button produces tree with two leaf children`() {
        val widget = Row(
            children = listOf(
                Text("Hello"),
                OutlinedButton(text = "OK", onPressed = null),
            ),
        )
        val nodes = buildAndDump(widget)

        // 根节点应是 Row 相关的 MultiChild element
        val root = nodes.first()
        assertEquals(0, root.depth)
        assertEquals(2, root.childCount)

        // Row 本身应当有 2 个直接子节点（Text + Button wrapper）
        val rowChildren = nodes.filter { it.depth == 1 }
        assertEquals(2, rowChildren.size)
    }

    // ── Padding wrapping Container ────────────────────────────────────────

    @Test
    fun `Padding wrapping Container produces single child chain`() {
        val widget = Padding(
            child = Container(),
            all = 4,
        )
        val nodes = buildAndDump(widget)
        assertTrue("Tree should have at least 2 nodes", nodes.size >= 2)

        // Padding 是 SingleChild element，child 深度比 root 深 1
        val root = nodes.first()
        assertEquals(1, root.childCount)

        val child = nodes.first { it.depth == 1 }
        assertEquals(0, child.childCount)
    }

    // ── nested Columns ───────────────────────────────────────────────────

    @Test
    fun `nested Columns preserve child order`() {
        val widget = Column(
            children = listOf(
                Text("A"),
                Column(
                    children = listOf(
                        Text("B"),
                        Text("C"),
                    ),
                ),
            ),
        )
        val nodes = buildAndDump(widget)

        val outerColumn = nodes.first { it.depth == 0 }
        assertEquals(2, outerColumn.childCount)

        // 深度 1 的两个节点：一个文本叶子 + 一个内层 Column
        val depth1 = nodes.filter { it.depth == 1 }
        assertEquals(2, depth1.size)

        // 内层 Column 应有 2 个深度 2 的子节点
        val depth2 = nodes.filter { it.depth == 2 }
        assertEquals(2, depth2.size)
    }

    // ── Stack + Positioned ───────────────────────────────────────────────

    @Test
    fun `Stack with two Positioned children attaches children in order`() {
        val widget = Stack(
            children = listOf(
                Positioned(
                    left = 0,
                    top = 0,
                    child = Text("Front"),
                ),
                Positioned(
                    left = 10,
                    top = 10,
                    child = Text("Back"),
                ),
            ),
        )
        val nodes = buildAndDump(widget)

        val stackNode = nodes.first { it.depth == 0 }
        assertEquals(2, stackNode.childCount)

        // 每个 Positioned 下还有一个叶子（Text）
        val depth1 = nodes.filter { it.depth == 1 }
        assertEquals(2, depth1.size)
        depth1.forEach { assertEquals(1, it.childCount) }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private fun buildAndDump(root: Widget): List<ElementDiagnosticsNode> {
        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        return try {
            runtime.resolveElementTree(root)
            runtime.collectDiagnostics().elementDiagnostics
        } finally {
            runtime.dispose()
        }
    }
}
