package com.purride.pixelui.foundation

import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 exact-text 分发、文本与按键链路的彻底分离，以及多 runtime 隔离。 */
class PixelTextInputDispatchTest {
    /** 公开事件保留精确的 UTF-16 内容，并具备普通不可变 data class 语义。 */
    @Test
    fun publicTextInputEventPreservesExactPayload() {
        /** 同时包含一个 supplementary 标量和一段组合序列、且未被规范化的事件。 */
        val event = PixelTextInputEvent("😀é")
        /** 用于证明公开 copy 契约保留全部原始码元的独立副本。 */
        val copied = event.copy()

        assertEquals("😀é", event.text)
        assertEquals(event, copied)
    }

    /** 聚焦节点的文本处理器向祖先冒泡，一旦某一层消费就立即停止。 */
    @Test
    fun textInputBubblesFromFocusedNodeToParentUntilConsumed() {
        /** 用于验证冒泡方向和载荷精确性的有序轨迹。 */
        val trace = mutableListOf<String>()
        /** 在子节点拒绝后消费该载荷的父焦点节点。 */
        val parentNode = FocusNode("parent")
        /** 初始获得焦点、作为文本分发起点的子节点。 */
        val childNode = FocusNode("child")
        /** 用于驱动 canonical 声明式 Focus 工厂的离屏 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = parentNode,
                    child = Focus(
                        node = childNode,
                        autofocus = true,
                        child = Text("CHILD"),
                        onTextInput = { event ->
                            trace += "child:${event.text}"
                            false
                        },
                    ),
                    onTextInput = { event ->
                        trace += "parent:${event.text}"
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressText("😀"))
            assertEquals(listOf("child:😀", "parent:😀"), trace)
        } finally {
            tester.dispose()
        }
    }

    /** 未被消费的文本不会退化成按键事件，哪怕它只是一个可用 Char 表示的 BMP 标量。 */
    @Test
    fun unconsumedBmpTextNeverReachesTheKeyHandler() {
        /** 用于区分文本阶段与非文本按键阶段的有序轨迹。 */
        val trace = mutableListOf<String>()
        /** 同时配置了文本处理器和按键处理器的焦点节点。 */
        val node = FocusNode("no-fallback")
        /** 通过公开 tester API 投递精确文本的离屏 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("NO-FALLBACK"),
                    onTextInput = { event ->
                        trace += "text:${event.text}"
                        false
                    },
                    onKeyEvent = { event ->
                        trace += "key:${event.key}"
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertFalse(tester.pressText("A"))
            assertEquals(listOf("text:A"), trace)
        } finally {
            tester.dispose()
        }
    }

    /** 文本链路和按键链路互不重叠：非文本键只到 onKeyEvent，不会到 onTextInput。 */
    @Test
    fun nonTextKeysNeverReachTheTextHandler() {
        /** 用于验证每个分发阶段只到达各自处理器的有序轨迹。 */
        val trace = mutableListOf<String>()
        /** 同时观察两个分发阶段的焦点节点。 */
        val node = FocusNode("separated")
        /** 依次投递一个导航按键和一段文本载荷的离屏 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("SEPARATED"),
                    onTextInput = { event ->
                        trace += "text:${event.text}"
                        true
                    },
                    onKeyEvent = { event ->
                        trace += "key:${event.key}"
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertTrue(tester.pressText("A"))
            assertEquals(listOf("key:${PixelKey.ENTER}", "text:A"), trace)
        } finally {
            tester.dispose()
        }
    }

    /** supplementary 和多 code point 文本保持精确，不会退化成逐码元的按键事件。 */
    @Test
    fun supplementaryAndMultiCodePointTextStaysExact() {
        /** 文本处理器观察到的精确 String 载荷。 */
        val textPayloads = mutableListOf<String>()
        /** 必须保持为空的按键事件列表；文本永远不会回落到按键链路。 */
        val keyEvents = mutableListOf<PixelKeyEvent>()
        /** 拒绝精确文本的焦点节点，便于暴露任何残留回落。 */
        val node = FocusNode("unicode")
        /** 投递 supplementary 与组合序列的离屏 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("UNICODE"),
                    onTextInput = { event ->
                        textPayloads += event.text
                        false
                    },
                    onKeyEvent = { event ->
                        keyEvents += event
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertFalse(tester.pressText("😀"))
            assertFalse(tester.pressText("é"))
            assertFalse(tester.pressText("😀😁"))
            assertEquals(
                listOf("😀", "é", "😀😁"),
                textPayloads,
            )
            assertTrue(keyEvents.isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** 并存的 tester runtime 只把精确文本投递给各自的 primary focus 链。 */
    @Test
    fun simultaneousRuntimesKeepTextInputIsolated() {
        /** 第一个 runtime 观察到的载荷。 */
        val firstPayloads = mutableListOf<String>()
        /** 第二个 runtime 观察到的载荷。 */
        val secondPayloads = mutableListOf<String>()
        /** 第一个独立保留的测试 runtime。 */
        val firstTester = PixelTester()
        /** 第二个独立保留的测试 runtime。 */
        val secondTester = PixelTester()
        try {
            firstTester.pumpWidget(textFocus("first", firstPayloads), 24, 12)
            secondTester.pumpWidget(textFocus("second", secondPayloads), 24, 12)

            assertTrue(firstTester.pressText("😀"))
            assertEquals(listOf("😀"), firstPayloads)
            assertTrue(secondPayloads.isEmpty())

            assertTrue(secondTester.pressText("é"))
            assertEquals(listOf("😀"), firstPayloads)
            assertEquals(listOf("é"), secondPayloads)
        } finally {
            firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** 一个 tester 释放后，另一个 runtime 的文本分发完全不受影响。 */
    @Test
    fun disposingOneRuntimeLeavesTheOtherTextChainIntact() {
        /** 保持挂载的 runtime 观察到的载荷。 */
        val survivingPayloads = mutableListOf<String>()
        /** 测试中途被释放的 runtime 观察到的载荷。 */
        val disposedPayloads = mutableListOf<String>()
        /** 在整个测试期间保持挂载的 runtime。 */
        val survivingTester = PixelTester()
        /** 在另一个 runtime 继续分发文本时被释放的 runtime。 */
        val disposedTester = PixelTester()
        try {
            survivingTester.pumpWidget(textFocus("surviving", survivingPayloads), 24, 12)
            disposedTester.pumpWidget(textFocus("disposed", disposedPayloads), 24, 12)
            disposedTester.dispose()

            assertTrue(survivingTester.pressText("A"))
            assertEquals(listOf("A"), survivingPayloads)
            assertTrue(disposedPayloads.isEmpty())
        } finally {
            survivingTester.dispose()
        }
    }

    /** 构造一个独立自动聚焦的 widget，其 String 处理器记录精确载荷。 */
    private fun textFocus(label: String, payloads: MutableList<String>): Widget {
        /** 仅由渲染该 widget 的 tester 持有的稳定节点。 */
        val node = FocusNode(label)
        return Focus(
            node = node,
            autofocus = true,
            child = Text(label),
            onTextInput = { event ->
                payloads += event.text
                true
            },
        )
    }
}
