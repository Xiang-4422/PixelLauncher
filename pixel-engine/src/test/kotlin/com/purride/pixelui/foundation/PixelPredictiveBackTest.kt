package com.purride.pixelui.foundation

import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelPredictiveBackCallback
import com.purride.pixelui.PixelPredictiveBackEvent
import com.purride.pixelui.PixelPredictiveBackHandler
import com.purride.pixelui.PixelPredictiveBackSwipeEdge
import com.purride.pixelui.Text
import com.purride.pixelui.internal.host.PixelHostPredictiveBackSession
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android 无关的预测返回 Dispatcher 与 Host 会话契约测试。 */
class PixelPredictiveBackTest {
    /** Widget handler 只有 enabled 时占据栈位，并完整转发预测返回生命周期。 */
    @Test
    fun widgetHandlerRegistersOnlyWhileEnabled() {
        val events = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        val tester = PixelTester()

        tester.pumpWidget(
            widget = predictiveBackTree(
                dispatcher = dispatcher,
                enabled = false,
                callback = recordingCallback("route", events),
            ),
            logicalWidth = 32,
            logicalHeight = 16,
        )
        assertFalse(dispatcher.hasRegisteredHandlers)

        tester.pumpWidget(
            widget = predictiveBackTree(
                dispatcher = dispatcher,
                enabled = true,
                callback = recordingCallback("route", events),
            ),
            logicalWidth = 32,
            logicalHeight = 16,
        )
        assertTrue(dispatcher.hasRegisteredHandlers)
        assertTrue(dispatcher.startPredictiveBack(event(0f)))
        dispatcher.updatePredictiveBack(event(0.5f))
        dispatcher.cancelPredictiveBack()

        tester.pumpWidget(
            widget = predictiveBackTree(
                dispatcher = dispatcher,
                enabled = false,
                callback = recordingCallback("route", events),
            ),
            logicalWidth = 32,
            logicalHeight = 16,
        )
        assertFalse(dispatcher.hasRegisteredHandlers)
        assertEquals(
            listOf("route:start:0.0", "route:progress:0.5", "route:cancel"),
            events,
        )
        tester.dispose()
    }

    /** 完整手势只发送给 start 时接受它的栈顶 callback。 */
    @Test
    fun dispatcherLocksStartProgressAndCommitToOneCallback() {
        val events = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        dispatcher.registerPredictive(recordingCallback("bottom", events))
        dispatcher.registerPredictive(recordingCallback("top", events))

        assertTrue(dispatcher.startPredictiveBack(event(0f)))
        dispatcher.updatePredictiveBack(event(0.35f))
        dispatcher.updatePredictiveBack(event(0.8f))
        assertTrue(dispatcher.commitPredictiveBack())

        assertEquals(
            listOf("top:start:0.0", "top:progress:0.35", "top:progress:0.8", "top:commit"),
            events,
        )
    }

    /** 取消会回滚选中的 callback，且之后的 progress/commit 不会复用旧会话。 */
    @Test
    fun dispatcherCancellationIsTerminalAndIdempotent() {
        val events = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        dispatcher.registerPredictive(recordingCallback("route", events))

        assertTrue(dispatcher.startPredictiveBack(event(0f)))
        dispatcher.updatePredictiveBack(event(0.4f))
        dispatcher.cancelPredictiveBack()
        dispatcher.cancelPredictiveBack()
        dispatcher.updatePredictiveBack(event(0.9f))

        assertEquals(
            listOf("route:start:0.0", "route:progress:0.4", "route:cancel"),
            events,
        )
    }

    /** 顶层传统 handler 在 commit 前阻挡底层预览，拒绝后才走离散 fallback。 */
    @Test
    fun discreteTopHandlerBlocksUnderlyingPreviewUntilCommitFallback() {
        val events = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        dispatcher.registerPredictive(recordingCallback("route", events))
        dispatcher.register {
            events += "overlay:invoke"
            false
        }

        assertTrue(dispatcher.startPredictiveBack(event(0f)))
        dispatcher.updatePredictiveBack(event(0.7f))
        assertTrue(dispatcher.commitPredictiveBack())

        assertEquals(listOf("overlay:invoke", "route:invoke"), events)
    }

    /** 正在持有手势的注册被释放时必须收到一次取消，不能再提交。 */
    @Test
    fun disposingActiveRegistrationCancelsSessionExactlyOnce() {
        val events = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        val registration = dispatcher.registerPredictive(recordingCallback("route", events))

        assertTrue(dispatcher.startPredictiveBack(event(0f)))
        registration.dispose()
        registration.dispose()

        assertFalse(dispatcher.commitPredictiveBack())
        assertEquals(listOf("route:start:0.0", "route:cancel"), events)
    }

    /** 输入焦点只在 commit 时清除，cancel 不触碰焦点或 widget Dispatcher。 */
    @Test
    fun hostSessionDefersTextInputBlurUntilCommit() {
        var focused = true
        var clears = 0
        var redraws = 0
        val dispatcher = PixelBackDispatcher()
        var widgetInvocations = 0
        dispatcher.register {
            widgetInvocations++
            true
        }
        val session = PixelHostPredictiveBackSession(
            hasFocusedTextInput = { focused },
            clearFocusedTextInput = {
                focused = false
                clears++
            },
            backDispatcher = { dispatcher },
            onUnhandledBack = { null },
            onSessionChanged = { redraws++ },
        )

        assertTrue(session.start(event(0f)))
        session.progress(event(0.5f))
        session.cancel()
        assertTrue(focused)
        assertEquals(0, clears)
        assertEquals(0, widgetInvocations)

        assertTrue(session.start(event(0f)))
        assertTrue(session.commit())
        assertFalse(focused)
        assertEquals(1, clears)
        assertEquals(0, widgetInvocations)
        assertEquals(5, redraws)
    }

    /** API 33 式无 start 提交仍遵循输入、widget、fallback 的既有顺序。 */
    @Test
    fun hostSessionCommitWithoutStartUsesDiscreteCompatibilityPath() {
        val calls = mutableListOf<String>()
        val dispatcher = PixelBackDispatcher()
        dispatcher.register {
            calls += "widget"
            false
        }
        val session = PixelHostPredictiveBackSession(
            hasFocusedTextInput = { false },
            clearFocusedTextInput = { calls += "clear" },
            backDispatcher = { dispatcher },
            onUnhandledBack = {
                {
                    calls += "fallback"
                    true
                }
            },
            onSessionChanged = { calls += "redraw" },
        )

        assertTrue(session.commit())

        assertEquals(listOf("widget", "fallback", "redraw"), calls)
    }

    /** 构造一帧有效的左边缘手势数据。 */
    private fun event(progress: Float): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = progress,
            touchX = 12f,
            touchY = 80f,
            swipeEdge = PixelPredictiveBackSwipeEdge.Left,
        )
    }

    /** 构造带稳定 key 的 Host + 预测返回 widget 树。 */
    private fun predictiveBackTree(
        dispatcher: PixelBackDispatcher,
        enabled: Boolean,
        callback: PixelPredictiveBackCallback,
    ) = PixelBackHost(
        dispatcher = dispatcher,
        child = PixelPredictiveBackHandler(
            enabled = enabled,
            callback = callback,
            child = Text("CONTENT"),
            key = "predictive-handler",
        ),
        key = "back-host",
    )

    /** 创建同时记录完整手势与离散 fallback 的 callback。 */
    private fun recordingCallback(
        name: String,
        events: MutableList<String>,
    ): PixelPredictiveBackCallback {
        return object : PixelPredictiveBackCallback {
            override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
                events += "$name:start:${event.progress}"
                return true
            }

            override fun onBackProgressed(event: PixelPredictiveBackEvent) {
                events += "$name:progress:${event.progress}"
            }

            override fun onBackCancelled() {
                events += "$name:cancel"
            }

            override fun onBackCommitted(): Boolean {
                events += "$name:commit"
                return true
            }

            override fun onBackInvoked(): Boolean {
                events += "$name:invoke"
                return true
            }
        }
    }
}
