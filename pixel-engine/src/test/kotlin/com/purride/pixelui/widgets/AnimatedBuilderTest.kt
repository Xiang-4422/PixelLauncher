package com.purride.pixelui.widgets

import com.purride.pixelui.AnimatedBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.ElementTreeBuildRuntimeFactory
import com.purride.pixelui.internal.UnsupportedWidgetAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AnimatedBuilderTest {

    /** child 参数会原样传给 builder。 */
    @Test
    fun childParameterIsForwardedToBuilder() {
        val staticChild = Text("STATIC")
        var captured: Widget? = null
        val widget = AnimatedBuilder(
            animation = ValueNotifier(0),
            child = staticChild,
        ) { _, child ->
            captured = child
            SizedBox(width = 1, height = 1)
        }
        buildOnce(widget)
        assertSame("child should reach builder by reference", staticChild, captured)
    }

    /** 未传 child 时 builder 收到 null。 */
    @Test
    fun missingChildIsNullInBuilder() {
        var captured: Widget? = Text("placeholder")
        val widget = AnimatedBuilder(
            animation = ValueNotifier(0),
        ) { _, child ->
            captured = child
            SizedBox(width = 1, height = 1)
        }
        buildOnce(widget)
        assertNull(captured)
    }

    /** builder 的 BuildContext 非空。 */
    @Test
    fun builderReceivesBuildContext() {
        var capturedContext: BuildContext? = null
        val widget = AnimatedBuilder(
            animation = ValueNotifier(0),
        ) { ctx, _ ->
            capturedContext = ctx
            SizedBox(width = 1, height = 1)
        }
        buildOnce(widget)
        assertNotNull(capturedContext)
    }

    /** animation 变化触发重建——builder 再次执行。 */
    @Test
    fun animationChangeTriggersRebuild() {
        val notifier = ValueNotifier(0)
        var buildCount = 0
        val widget = AnimatedBuilder(animation = notifier) { _, _ ->
            buildCount += 1
            SizedBox(width = 1, height = 1)
        }
        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        try {
            runtime.resolveElementTree(widget)
            val initial = buildCount
            assertEquals(1, initial)
            notifier.value = 1
            runtime.resolveElementTree(widget)
            assertEquals("builder should rebuild after notify", 2, buildCount)
        } finally {
            runtime.dispose()
        }
    }

    private fun buildOnce(widget: Widget) {
        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        try {
            runtime.resolveElementTree(widget)
        } finally {
            runtime.dispose()
        }
    }
}
