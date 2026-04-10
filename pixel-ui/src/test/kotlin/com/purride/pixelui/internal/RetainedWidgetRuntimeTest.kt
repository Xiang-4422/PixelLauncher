package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Builder
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.Directionality
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.State
import com.purride.pixelui.StatefulBuilder
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import org.junit.Assert.assertEquals
import org.junit.Test

class RetainedWidgetRuntimeTest {

    @Test
    fun valueListenableBuilderUpdatesRenderedToneAfterNotifierChanges() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()
        val tone = ValueNotifier(PixelTone.ON)

        val root = ValueListenableBuilder(
            listenable = tone,
        ) { _, currentTone ->
            Container(
                width = 4,
                height = 4,
                fillTone = currentTone,
                borderTone = null,
            )
        }

        val first = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ON.value, first.buffer.getPixel(1, 1))

        tone.value = PixelTone.ACCENT

        val second = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ACCENT.value, second.buffer.getPixel(1, 1))
    }

    @Test
    fun statefulWidgetSetStateTriggersRetainedRebuild() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()
        val root = ToggleToneWidget()

        val first = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelTone.ON.value, first.buffer.getPixel(1, 1))
        first.clickTargets.single().onClick.invoke()

        val second = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelTone.ACCENT.value, second.buffer.getPixel(1, 1))
    }

    @Test
    fun inheritedWidgetNotifiesDependentBuildsOnUpdate() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()

        val first = runtime.render(
            root = ToneScope(
                tone = PixelTone.ON,
                child = ToneConsumerWidget(),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ON.value, first.buffer.getPixel(1, 1))

        val second = runtime.render(
            root = ToneScope(
                tone = PixelTone.ACCENT,
                child = ToneConsumerWidget(),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ACCENT.value, second.buffer.getPixel(1, 1))
    }

    @Test
    fun mediaQueryAndDirectionalityPropagateThroughInheritedContext() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()
        val screenProfile = ScreenProfile(
            logicalWidth = 6,
            logicalHeight = 4,
            dotSizePx = 8,
        )

        val result = runtime.render(
            root = MediaQuery(
                data = MediaQueryData(
                    logicalWidth = 6,
                    logicalHeight = 4,
                    screenProfile = screenProfile,
                ),
                child = Directionality(
                    textDirection = TextDirection.RTL,
                    child = MediaAwareWidget(),
                ),
            ),
            logicalWidth = 6,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(5, 0))
    }

    @Test
    fun inheritedNotifierMarksDependentsDirtyAfterNotifierChanges() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()
        val count = ValueNotifier(0)
        val root = CounterScope(
            notifier = count,
            child = CounterConsumerWidget(),
        )

        val first = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ON.value, first.buffer.getPixel(1, 1))

        count.value = 1

        val second = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelTone.ACCENT.value, second.buffer.getPixel(1, 1))
    }

    @Test
    fun builderReadsLocalInheritedContext() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()

        val result = runtime.render(
            root = ToneScope(
                tone = PixelTone.ON,
                child = Builder { context ->
                    Container(
                        width = 4,
                        height = 4,
                        fillTone = ToneScope.of(context),
                        borderTone = null,
                    )
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
    }

    @Test
    fun statefulBuilderRebuildsLocalState() {
        val runtime = WidgetRenderRuntimeFactory.createDefault()
        var accent = false

        val root = StatefulBuilder { _, setState ->
            GestureDetector(
                onTap = {
                    setState {
                        accent = !accent
                    }
                },
                child = Container(
                    width = 6,
                    height = 6,
                    fillTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                    borderTone = null,
                ),
            )
        }

        val first = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelTone.ON.value, first.buffer.getPixel(1, 1))
        first.clickTargets.single().onClick.invoke()

        val second = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelTone.ACCENT.value, second.buffer.getPixel(1, 1))
    }

    @Test
    fun renderObjectWidgetCreatesAndUpdatesRenderObjectElement() {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = BridgeWidgetAdapter,
        )

        try {
            val firstRoot = buildRuntime.resolveElementTree(TestRenderWidget(label = "first"))
            val firstRenderObject = firstRoot?.findRenderObject() as? TestRenderObject

            val secondRoot = buildRuntime.resolveElementTree(TestRenderWidget(label = "second"))
            val secondRenderObject = secondRoot?.findRenderObject() as? TestRenderObject

            assertEquals(firstRoot, secondRoot)
            assertEquals(firstRenderObject, secondRenderObject)
            assertEquals("second", secondRenderObject?.label)
            assertEquals(2, secondRenderObject?.updateCount)
        } finally {
            buildRuntime.dispose()
        }
    }

    private class ToggleToneWidget : StatefulWidget() {
        override fun createState(): State<out StatefulWidget> = ToggleToneState()
    }

    private class ToggleToneState : State<ToggleToneWidget>() {
        private var accent = false

        override fun build(context: BuildContext): Widget {
            return GestureDetector(
                onTap = {
                    setState {
                        accent = !accent
                    }
                },
                child = Container(
                    width = 6,
                    height = 6,
                    fillTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                    borderTone = null,
                ),
            )
        }
    }

    private class ToneScope(
        val tone: PixelTone,
        override val child: Widget,
    ) : InheritedWidget(child) {
        override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
            return tone != (oldWidget as? ToneScope)?.tone
        }

        companion object {
            fun of(context: BuildContext): PixelTone {
                return context.dependOnInheritedWidgetOfExactType<ToneScope>()?.tone
                    ?: error("ToneScope 未注入")
            }
        }
    }

    private class CounterScope(
        notifier: ValueNotifier<Int>,
        override val child: Widget,
    ) : InheritedNotifier<ValueNotifier<Int>>(
        notifier = notifier,
        child = child,
    ) {
        companion object {
            fun of(context: BuildContext): Int {
                return context.dependOnInheritedWidgetOfExactType<CounterScope>()
                    ?.notifier
                    ?.value
                    ?: 0
            }
        }
    }

    private class ToneConsumerWidget : com.purride.pixelui.StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            return Container(
                width = 4,
                height = 4,
                fillTone = ToneScope.of(context),
                borderTone = null,
            )
        }
    }

    private class MediaAwareWidget : com.purride.pixelui.StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            val mediaQuery = MediaQuery.of(context)
            val direction = Directionality.of(context)
            return Container(
                width = mediaQuery.logicalWidth,
                height = 1,
                fillTone = if (direction == TextDirection.RTL) {
                    PixelTone.ACCENT
                } else {
                    PixelTone.ON
                },
                borderTone = null,
            )
        }
    }

    private class CounterConsumerWidget : com.purride.pixelui.StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            return Container(
                width = 4,
                height = 4,
                fillTone = if (CounterScope.of(context) > 0) {
                    PixelTone.ACCENT
                } else {
                    PixelTone.ON
                },
                borderTone = null,
            )
        }
    }

    private class TestRenderWidget(
        private val label: String,
    ) : RenderObjectWidget() {
        override fun createRenderObject(context: InternalBuildContext): RenderObject {
            return TestRenderObject()
        }

        override fun updateRenderObject(
            context: InternalBuildContext,
            renderObject: RenderObject,
        ) {
            (renderObject as TestRenderObject).apply {
                label = this@TestRenderWidget.label
                updateCount += 1
            }
        }
    }

    private class TestRenderObject : RenderObject() {
        var label: String = ""
        var updateCount: Int = 0
    }
}
