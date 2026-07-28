package com.purride.pixelui.foundation

import com.purride.pixelui.Column
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.FocusScopeNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证焦点状态由单个 runtime 持有并分发，而不是依赖任何进程级单例。
 *
 * Verifies that focus state is retained and dispatched by one runtime instead of a process singleton.
 */
class PixelFocusOwnerIsolationTest {
    /** 两个并存的测试 runtime 各自保持独立的 primary focus 与遍历状态。 */
    @Test
    fun simultaneousPixelTestersDispatchOnlyWithinTheirOwnRuntime() {
        val firstNodes = listOf(FocusNode("first-a"), FocusNode("first-b"))
        val secondNodes = listOf(FocusNode("second-a"), FocusNode("second-b"))
        val firstTester = PixelTester()
        val secondTester = PixelTester()
        try {
            firstTester.pumpWidget(focusPair(firstNodes, autofocus = true), 48, 24)
            secondTester.pumpWidget(focusPair(secondNodes, autofocus = true), 48, 24)

            assertTrue(firstNodes[0].isFocused)
            assertTrue(secondNodes[0].isFocused)

            assertTrue(firstTester.pressKey(PixelKey.TAB))
            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertFalse(secondNodes[1].isFocused)

            assertTrue(secondTester.pressKey(PixelKey.TAB))
            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[1].isFocused)
        } finally {
            firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** 释放一个 runtime 只清空它自己的节点，不影响兄弟 runtime 的焦点。 */
    @Test
    fun disposingOneTesterDoesNotClearTheOtherTester() {
        val firstNodes = listOf(FocusNode("disposed-a"), FocusNode("disposed-b"))
        val secondNodes = listOf(FocusNode("survivor-a"), FocusNode("survivor-b"))
        val firstTester = PixelTester()
        val secondTester = PixelTester()
        var firstDisposed = false
        try {
            firstTester.pumpWidget(focusPair(firstNodes, autofocus = true), 48, 24)
            secondTester.pumpWidget(focusPair(secondNodes, autofocus = true), 48, 24)

            firstTester.dispose()
            firstDisposed = true

            assertFalse(firstNodes[0].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertTrue(secondTester.pressKey(PixelKey.TAB))
            assertTrue(secondNodes[1].isFocused)
        } finally {
            if (!firstDisposed) firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** 裸 retained runtime 同样具备该隔离性，不依赖 PixelTester 的清理逻辑。 */
    @Test
    fun rawPixelUiRuntimesOwnIndependentFocusManagers() {
        val firstNodes = listOf(FocusNode("raw-first-a"), FocusNode("raw-first-b"))
        val secondNodes = listOf(FocusNode("raw-second-a"), FocusNode("raw-second-b"))
        val firstRuntime = PixelUiRuntime()
        val secondRuntime = PixelUiRuntime()
        try {
            firstRuntime.render(focusPair(firstNodes, autofocus = true), 48, 24)
            secondRuntime.render(focusPair(secondNodes, autofocus = true), 48, 24)

            assertTrue(firstRuntime.focusOwner.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB)))

            assertTrue(firstNodes[1].isFocused)
            assertTrue(secondNodes[0].isFocused)
            assertFalse(secondNodes[1].isFocused)
        } finally {
            firstRuntime.dispose()
            secondRuntime.dispose()
        }
    }

    /** 在没有 primary focus 时，正向遍历从第一个可聚焦节点开始。 */
    @Test
    fun firstTabWithoutPrimaryFocusSelectsFirstNode() {
        val nodes = listOf(FocusNode("tab-first"), FocusNode("tab-second"))
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(nodes, autofocus = false), 48, 24)

            assertTrue(tester.pressKey(PixelKey.TAB))

            assertTrue(nodes[0].isFocused)
            assertFalse(nodes[1].isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** 在没有 primary focus 时，反向遍历从最后一个可聚焦节点开始。 */
    @Test
    fun firstShiftTabWithoutPrimaryFocusSelectsLastNode() {
        val nodes = listOf(FocusNode("shift-first"), FocusNode("shift-last"))
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(nodes, autofocus = false), 48, 24)

            assertTrue(tester.pressKey(PixelKey.SHIFT_TAB))

            assertFalse(nodes[0].isFocused)
            assertTrue(nodes[1].isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** widget key 稳定时，默认的 Focus 与 FocusScope 节点能跨声明式重建存活。 */
    @Test
    fun defaultFocusAndScopeNodesRetainFocusAcrossRebuild() {
        val tester = PixelTester()
        try {
            tester.pumpWidget(defaultOwnedFocusTree(autofocus = true), 48, 24)
            assertTrue(tester.dumpSemanticsTree().contains("focused=true"))

            tester.pumpWidget(defaultOwnedFocusTree(autofocus = false), 48, 24)

            assertTrue(tester.dumpSemanticsTree().contains("focused=true"))
        } finally {
            tester.dispose()
        }
    }

    /** 禁用当前 primary 节点会立即把焦点转移到下一个可聚焦兄弟节点。 */
    @Test
    fun disablingCurrentNodeTransfersFocusWithinItsScope() {
        val first = FocusNode("enabled-first")
        val second = FocusNode("enabled-second")
        val tester = PixelTester()
        try {
            tester.pumpWidget(focusPair(listOf(first, second), autofocus = true), 48, 24)
            assertTrue(first.isFocused)

            first.canRequestFocus = false

            assertFalse(first.isFocused)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** 替换显式 scope 后，旧 scope 会在其后代迁移完成时释放原 owner。 */
    @Test
    fun replacingExplicitScopeAllowsOldScopeInAnotherRuntime() {
        /** 被同一 key 的 retained 边界替换掉的原始 scope。 */
        val firstScope = FocusScopeNode()
        /** 留在第一个 runtime 中的替换 scope。 */
        val secondScope = FocusScopeNode()
        /** 从原始 scope 迁移到替换 scope 的稳定节点。 */
        val migratedNode = FocusNode("migrated")
        /** 在兄弟 runtime 中挂载到已释放 scope 下的独立节点。 */
        val siblingNode = FocusNode("sibling")
        /** 执行 scope 替换的第一个 retained runtime。 */
        val firstRuntime = PixelUiRuntime()
        /** 用于证明原始 scope 不再持有旧 owner 的第二个 runtime。 */
        val secondRuntime = PixelUiRuntime()
        try {
            firstRuntime.render(explicitScopeTree(firstScope, migratedNode), 48, 24)
            firstRuntime.render(explicitScopeTree(secondScope, migratedNode), 48, 24)

            assertSame(secondScope, migratedNode.scope)
            secondRuntime.render(explicitScopeTree(firstScope, siblingNode), 48, 24)
            assertSame(firstScope, siblingNode.scope)
        } finally {
            firstRuntime.dispose()
            secondRuntime.dispose()
        }
    }

    /** 从未挂载的节点请求焦点时安全失败，而不是落进某个进程级的共享焦点树。 */
    @Test
    fun neverMountedNodeCannotRequestFocus() {
        /** 从未挂载到任何 scope 或 runtime 的节点。 */
        val detached = FocusNode("never-mounted")

        assertFalse(detached.requestFocus())
        assertFalse(detached.isFocused)
        assertNull(detached.scope)
        detached.unfocus()
        assertFalse(detached.isFocused)
    }

    /** 节点卸载后回到未挂载状态，其 requestFocus 不会影响仍然存活的 runtime。 */
    @Test
    fun unmountedNodeCannotStealFocusFromALiveRuntime() {
        /** runtime 重建时不再包含、因而被卸载的节点。 */
        val unmounted = FocusNode("unmounted")
        /** 在存活 runtime 中继续持有 primary focus 的节点。 */
        val surviving = FocusNode("surviving")
        /** 先挂载两个节点、随后丢弃第一个节点的 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(keyedFocusTree(unmounted, surviving), 48, 24)
            assertTrue(unmounted.isFocused)

            tester.pumpWidget(keyedFocusTree(null, surviving), 48, 24)
            assertTrue(surviving.requestFocus())

            assertNull(unmounted.scope)
            assertFalse(unmounted.requestFocus())
            assertFalse(unmounted.isFocused)
            assertTrue(surviving.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /**
     * 构造 key 稳定的焦点树；[leading] 为 null 时该 Focus widget 整个离开 retained tree。
     *
     * 每个节点固定绑定自己的 widget key，避免节点在两个不同 key 的 widget 之间搬家干扰断言。
     */
    private fun keyedFocusTree(leading: FocusNode?, trailing: FocusNode): Widget {
        return FocusScope(
            key = "keyed-scope",
            child = Column(
                children = listOfNotNull(
                    leading?.let { node ->
                        Focus(
                            node = node,
                            autofocus = true,
                            key = "keyed-focus-leading",
                            child = Text("LEADING"),
                        )
                    },
                    Focus(
                        node = trailing,
                        autofocus = true,
                        key = "keyed-focus-trailing",
                        child = Text("TRAILING"),
                    ),
                ),
            ),
        )
    }

    /** 构造一个稳定的双节点遍历 scope，用于 runtime 隔离断言。 */
    private fun focusPair(nodes: List<FocusNode>, autofocus: Boolean): Widget {
        return FocusScope(
            key = "pair-scope",
            child = Column(
                children = nodes.mapIndexed { index, node ->
                    Focus(
                        node = node,
                        autofocus = autofocus && index == 0,
                        key = "pair-focus-$index",
                        child = Text("NODE $index"),
                    )
                },
            ),
        )
    }

    /** 构造一棵省略 Focus/FocusScope 节点的树，这些节点必须由 State 保留。 */
    private fun defaultOwnedFocusTree(autofocus: Boolean): Widget {
        return FocusScope(
            key = "default-scope",
            child = Focus(
                autofocus = autofocus,
                key = "default-focus",
                child = OutlinedButton(text = "DEFAULT", onPressed = { }),
            ),
        )
    }

    /** 构造一个带 key 的显式 scope，使 retained 更新只替换其节点 owner。 */
    private fun explicitScopeTree(scope: FocusScopeNode, node: FocusNode): Widget {
        return FocusScope(
            node = scope,
            key = "explicit-scope",
            child = Focus(
                node = node,
                key = "explicit-focus",
                child = Text("EXPLICIT"),
            ),
        )
    }
}
