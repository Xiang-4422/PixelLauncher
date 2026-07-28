package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.Builder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.Directionality
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.ImeAvoidingView
import com.purride.pixelui.KeyboardAvoidingView
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.SafeArea
import com.purride.pixelui.State
import com.purride.pixelui.StatefulBuilder
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedWidgetRuntimeTest {

    @Test
    fun valueListenableBuilderUpdatesRenderedToneAfterNotifierChanges() {
        val runtime = PixelUiRuntime()
        val tone = ValueNotifier(PixelColor.White)

        val root = ValueListenableBuilder(
            listenable = tone,
        ) { _, currentTone ->
            Container(
                width = 4,
                height = 4,
                fillColor = currentTone,
                borderColor = null,
            )
        }

        val first = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelColor.White, first.buffer.getPixel(1, 1))

        tone.value = PixelColor.fromRgb(200, 100, 0)

        val second = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelColor.fromRgb(200, 100, 0), second.buffer.getPixel(1, 1))
    }

    @Test
    fun statefulWidgetSetStateTriggersRetainedRebuild() {
        val runtime = PixelUiRuntime()
        val root = ToggleToneWidget()

        val first = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelColor.White, first.buffer.getPixel(1, 1))
        first.clickTargets.single().onClick.invoke()

        val second = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelColor.fromRgb(200, 100, 0), second.buffer.getPixel(1, 1))
    }

    @Test
    fun inheritedWidgetNotifiesDependentBuildsOnUpdate() {
        val runtime = PixelUiRuntime()

        val first = runtime.render(
            root = ToneScope(
                tone = PixelColor.White,
                child = ToneConsumerWidget(),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelColor.White, first.buffer.getPixel(1, 1))

        val second = runtime.render(
            root = ToneScope(
                tone = PixelColor.fromRgb(200, 100, 0),
                child = ToneConsumerWidget(),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelColor.fromRgb(200, 100, 0), second.buffer.getPixel(1, 1))
    }

    @Test
    fun mediaQueryAndDirectionalityPropagateThroughInheritedContext() {
        val runtime = PixelUiRuntime()
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
                    padding = PixelWindowInsets(left = 1, top = 0, right = 0, bottom = 0),
                ),
                child = Directionality(
                    textDirection = TextDirection.RTL,
                    child = MediaAwareWidget(),
                ),
            ),
            logicalWidth = 6,
            logicalHeight = 4,
        )

        assertEquals(PixelColor.fromRgb(200, 100, 0), result.buffer.getPixel(5, 0))
    }

    @Test
    fun safeAreaUsesMediaQueryPadding() {
        val runtime = PixelUiRuntime()
        val screenProfile = ScreenProfile(
            logicalWidth = 8,
            logicalHeight = 8,
            dotSizePx = 8,
        )

        val result = runtime.render(
            root = MediaQuery(
                data = MediaQueryData(
                    logicalWidth = 8,
                    logicalHeight = 8,
                    screenProfile = screenProfile,
                    padding = PixelWindowInsets(left = 1, top = 2, right = 0, bottom = 0),
                ),
                child = SafeArea(
                    child = Container(
                        width = 2,
                        height = 2,
                        fillColor = PixelColor.White,
                        borderColor = null,
                    ),
                ),
            ),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(PixelColor.Transparent, result.buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, result.buffer.getPixel(1, 2))
        assertEquals(PixelColor.White, result.buffer.getPixel(2, 3))
    }

    @Test
    fun imeAvoidingViewUsesMediaQueryViewInsets() {
        val runtime = PixelUiRuntime()
        val screenProfile = ScreenProfile(logicalWidth = 8, logicalHeight = 8, dotSizePx = 8)

        val result = runtime.render(
            root = MediaQuery(
                data = MediaQueryData(
                    logicalWidth = 8,
                    logicalHeight = 8,
                    screenProfile = screenProfile,
                    viewInsets = PixelWindowInsets(left = 1, top = 2, right = 0, bottom = 4),
                    padding = PixelWindowInsets(left = 3, top = 3),
                ),
                child = ImeAvoidingView(
                    left = true,
                    top = true,
                    bottom = false,
                    child = Container(
                        width = 2,
                        height = 2,
                        fillColor = PixelColor.White,
                        borderColor = null,
                    ),
                ),
            ),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(PixelColor.Transparent, result.buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, result.buffer.getPixel(1, 2))
    }

    @Test
    fun keyboardAvoidingViewAliasesImeInsetsPadding() {
        val runtime = PixelUiRuntime()
        val screenProfile = ScreenProfile(logicalWidth = 8, logicalHeight = 8, dotSizePx = 8)

        val result = runtime.render(
            root = MediaQuery(
                data = MediaQueryData(
                    logicalWidth = 8,
                    logicalHeight = 8,
                    screenProfile = screenProfile,
                    viewInsets = PixelWindowInsets(left = 2, top = 1),
                ),
                child = KeyboardAvoidingView(
                    left = true,
                    top = true,
                    bottom = false,
                    child = Container(
                        width = 2,
                        height = 2,
                        fillColor = PixelColor.White,
                        borderColor = null,
                    ),
                ),
            ),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(PixelColor.White, result.buffer.getPixel(2, 1))
    }

    @Test
    fun hostRootSeparatesSystemPaddingFromImeViewInsets() {
        val runtime = PixelUiRuntime()
        val screenProfile = ScreenProfile(logicalWidth = 8, logicalHeight = 8, dotSizePx = 8)
        var captured: MediaQueryData? = null

        val result = runtime.render(
            root = HostRootWidget(
                screenProfile = screenProfile,
                textRasterizer = PixelBitmapFont.Default,
                windowInsets = PixelWindowInsets(left = 1, top = 2, right = 0, bottom = 1),
                viewInsets = PixelWindowInsets(bottom = 4),
                child = Builder {
                    captured = MediaQuery.of(it)
                    SafeArea(
                        child = Container(
                            width = 2,
                            height = 2,
                            fillColor = PixelColor.White,
                            borderColor = null,
                        ),
                    )
                },
            ),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(PixelWindowInsets(left = 1, top = 2, right = 0, bottom = 1), captured?.viewPadding)
        assertEquals(PixelWindowInsets(bottom = 4), captured?.viewInsets)
        assertEquals(PixelWindowInsets(left = 1, top = 2, right = 0, bottom = 0), captured?.padding)
        assertEquals(PixelColor.Transparent, result.buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, result.buffer.getPixel(1, 2))
    }

    @Test
    fun pixelWindowInsetsCanFilterSidesAndApplyMinimums() {
        val filtered = PixelWindowInsets(left = 1, top = 2, right = 3, bottom = 4)
            .only(left = true, top = false, right = false, bottom = true)
            .atLeast(PixelWindowInsets(left = 4, top = 1, right = 0, bottom = 2))

        assertEquals(PixelWindowInsets(left = 4, top = 1, right = 0, bottom = 4), filtered)
        assertEquals(com.purride.pixelui.EdgeInsets(left = 4, top = 1, right = 0, bottom = 4), filtered.toEdgeInsets())
    }

    @Test
    fun containerExplicitFillColorRendersToPixelBuffer() {
        // 替换原 hostThemeProvidesDefaultContainerStyleAndLocalThemeOverridesIt 测试。
        // Theme/ThemeData/ContainerStyle 已在纯色彩化重构中删除，改测直接传入 fillColor 的语义。
        val runtime = PixelUiRuntime()

        val accentPixel = runtime.render(
            root = Container(
                width = 4,
                height = 4,
                fillColor = PixelColor.fromRgb(200, 100, 0),
                borderColor = null,
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        ).buffer.getPixel(1, 1)

        val whitePixel = runtime.render(
            root = Container(
                width = 4,
                height = 4,
                fillColor = PixelColor.White,
                borderColor = null,
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        ).buffer.getPixel(1, 1)

        assertEquals(PixelColor.fromRgb(200, 100, 0), accentPixel)
        assertEquals(PixelColor.White, whitePixel)
    }

    @Test
    fun inheritedNotifierMarksDependentsDirtyAfterNotifierChanges() {
        val runtime = PixelUiRuntime()
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
        assertEquals(PixelColor.White, first.buffer.getPixel(1, 1))

        count.value = 1

        val second = runtime.render(
            root = root,
            logicalWidth = 4,
            logicalHeight = 4,
        )
        assertEquals(PixelColor.fromRgb(200, 100, 0), second.buffer.getPixel(1, 1))
    }

    @Test
    fun builderReadsLocalInheritedContext() {
        val runtime = PixelUiRuntime()

        val result = runtime.render(
            root = ToneScope(
                tone = PixelColor.White,
                child = Builder { context ->
                    Container(
                        width = 4,
                        height = 4,
                        fillColor = ToneScope.of(context),
                        borderColor = null,
                    )
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(PixelColor.White, result.buffer.getPixel(1, 1))
    }

    @Test
    fun statefulBuilderRebuildsLocalState() {
        val runtime = PixelUiRuntime()
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
                    fillColor = if (accent) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                    borderColor = null,
                ),
            )
        }

        val first = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelColor.White, first.buffer.getPixel(1, 1))
        first.clickTargets.single().onClick.invoke()

        val second = runtime.render(
            root = root,
            logicalWidth = 6,
            logicalHeight = 6,
        )
        assertEquals(PixelColor.fromRgb(200, 100, 0), second.buffer.getPixel(1, 1))
    }

    @Test
    fun renderObjectWidgetCreatesAndUpdatesRenderObjectElement() {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
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

    @Test
    fun buildRuntimeDiagnosticsExposeElementTreeAndDirtyQueue() {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
        )

        try {
            buildRuntime.resolveElementTree(
                TestSingleChildRenderWidget(
                    label = "parent",
                    child = TestRenderWidget(label = "child"),
                ),
            )

            val diagnostics = buildRuntime.collectDiagnostics()

            assertEquals(true, diagnostics.hasRoot)
            assertEquals(0, diagnostics.dirtyQueueDiagnostics.pendingElementCount)
            assertEquals(true, diagnostics.dirtyQueueDiagnostics.scheduledCount >= 2)
            assertEquals(true, diagnostics.dirtyQueueDiagnostics.rebuiltElementCount >= 2)
            assertEquals("SingleChildRenderObjectElement", diagnostics.elementDiagnostics.first().name)
            assertEquals("TestSingleChildRenderWidget", diagnostics.elementDiagnostics.first().widgetName)
            assertEquals("TestSingleChildRenderObject", diagnostics.elementDiagnostics.first().renderObjectName)
            assertEquals(
                "TestRenderObject",
                diagnostics.elementDiagnostics.single { it.widgetName == "TestRenderWidget" }.renderObjectName,
            )
        } finally {
            buildRuntime.dispose()
        }
    }

    @Test
    fun repeatedSetStateCoalescesDirtyElementBeforeBuildScope() {
        val visualUpdates = mutableListOf<Unit>()
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { visualUpdates += Unit },
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        val root = DiagnosticsStatefulWidget()

        try {
            buildRuntime.resolveElementTree(root)
            val state = DiagnosticsStatefulWidget.latestState
            val visualUpdatesBeforeSetState = visualUpdates.size
            state.markTwice()
            val beforeBuild = buildRuntime.collectDiagnostics()
            buildRuntime.resolveElementTree(root)
            val afterBuild = buildRuntime.collectDiagnostics()

            assertEquals(1, beforeBuild.dirtyQueueDiagnostics.pendingElementCount)
            assertEquals("StatefulElement", beforeBuild.dirtyQueueDiagnostics.pendingElementNames.single())
            assertEquals(0, afterBuild.dirtyQueueDiagnostics.pendingElementCount)
            assertEquals(2, state.buildCount)
            assertEquals(true, visualUpdates.size >= visualUpdatesBeforeSetState + 2)
        } finally {
            buildRuntime.dispose()
        }
    }

    @Test
    fun reRenderingIdleTreeDoesNotRequestVisualUpdateWithinRenderPass() {
        val visualUpdates = mutableListOf<Unit>()
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { visualUpdates += Unit },
            widgetAdapter = UnsupportedWidgetAdapter,
        )

        try {
            // 首帧（含 mount）：reconcile + buildScope 全在 render pass 内，标脏由本帧
            // buildScope 排空，不得请求新帧。
            buildRuntime.resolveElementTree(
                TestSingleChildRenderWidget(label = "a", child = TestRenderWidget(label = "a")),
            )
            assertEquals(0, visualUpdates.size)

            // 用全新 widget 实例再渲染一帧（宿主 content provider 每帧返回新实例的常态）：
            // reconciliation 会 Element.update→markNeedsBuild，但同样在 pass 内被本帧
            // buildScope 处理完，不得请求新帧——否则宿主 postInvalidateOnAnimation 永不
            // 收敛，任何含 widget 树的页面静止时也满帧空转重绘。
            buildRuntime.resolveElementTree(
                TestSingleChildRenderWidget(label = "b", child = TestRenderWidget(label = "b")),
            )
            assertEquals(0, visualUpdates.size)
        } finally {
            buildRuntime.dispose()
        }
    }

    @Test
    fun singleChildRenderObjectWidgetConnectsChildRenderObject() {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
        )

        try {
            val firstRoot = buildRuntime.resolveElementTree(
                TestSingleChildRenderWidget(
                    label = "parent-first",
                    child = TestRenderWidget(label = "child-first"),
                ),
            )
            val firstParentRenderObject = firstRoot?.findRenderObject() as? TestSingleChildRenderObject
            val firstChildRenderObject = firstParentRenderObject?.renderChild as? TestRenderObject

            val secondRoot = buildRuntime.resolveElementTree(
                TestSingleChildRenderWidget(
                    label = "parent-second",
                    child = TestRenderWidget(label = "child-second"),
                ),
            )
            val secondParentRenderObject = secondRoot?.findRenderObject() as? TestSingleChildRenderObject
            val secondChildRenderObject = secondParentRenderObject?.renderChild as? TestRenderObject

            assertSame(firstRoot, secondRoot)
            assertSame(firstParentRenderObject, secondParentRenderObject)
            assertSame(firstChildRenderObject, secondChildRenderObject)
            assertEquals("parent-second", secondParentRenderObject?.label)
            assertEquals("child-second", secondChildRenderObject?.label)
        } finally {
            buildRuntime.dispose()
        }
    }

    @Test
    fun multiChildRenderObjectWidgetConnectsChildRenderObjects() {
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
        )

        try {
            val firstRoot = buildRuntime.resolveElementTree(
                TestMultiChildRenderWidget(
                    label = "multi-first",
                    children = listOf(
                        TestRenderWidget(label = "first-a"),
                        TestRenderWidget(label = "first-b"),
                    ),
                ),
            )
            val firstParentRenderObject = firstRoot?.findRenderObject() as? TestMultiChildRenderObject
            val firstChildren = firstParentRenderObject?.renderChildren?.map { it as TestRenderObject }.orEmpty()

            val secondRoot = buildRuntime.resolveElementTree(
                TestMultiChildRenderWidget(
                    label = "multi-second",
                    children = listOf(
                        TestRenderWidget(label = "second-a"),
                        TestRenderWidget(label = "second-b"),
                        TestRenderWidget(label = "second-c"),
                    ),
                ),
            )
            val secondParentRenderObject = secondRoot?.findRenderObject() as? TestMultiChildRenderObject
            val secondChildren = secondParentRenderObject?.renderChildren?.map { it as TestRenderObject }.orEmpty()

            assertSame(firstRoot, secondRoot)
            assertSame(firstParentRenderObject, secondParentRenderObject)
            assertEquals("multi-second", secondParentRenderObject?.label)
            assertEquals(3, secondChildren.size)
            assertSame(firstChildren[0], secondChildren[0])
            assertSame(firstChildren[1], secondChildren[1])
            assertEquals("second-a", secondChildren[0].label)
            assertEquals("second-b", secondChildren[1].label)
            assertEquals("second-c", secondChildren[2].label)
        } finally {
            buildRuntime.dispose()
        }
    }

    /** Throwing State disposal still detaches every sibling and commits the empty child slot. */
    @Test
    fun teardownFailureDetachesAllSiblingStatesAndLeavesBuildOwnerReusable() {
        /** Runtime under test exposes retained-tree and dirty-queue diagnostics after failure. */
        val buildRuntime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        /** Exact sibling disposal order proving teardown did not stop at the first exception. */
        val disposalOrder = mutableListOf<String>()
        /** Every created State retained only so terminal mounted flags can be asserted. */
        val createdStates = mutableListOf<ThrowingDisposeState>()
        /** Stable root configuration reused to remove all children in one retained update. */
        fun root(children: List<Widget>): Widget = TestMultiChildRenderWidget(
            label = "teardown-root",
            children = children,
        )
        /** Creates one keyed sibling whose terminal callback may deliberately fail. */
        fun sibling(label: String, throwsOnDispose: Boolean): Widget = ThrowingDisposeWidget(
            label = label,
            throwsOnDispose = throwsOnDispose,
            disposalOrder = disposalOrder,
            createdStates = createdStates,
            key = label,
        )

        try {
            buildRuntime.resolveElementTree(
                root(
                    listOf(
                        sibling(label = "first", throwsOnDispose = true),
                        sibling(label = "second", throwsOnDispose = false),
                        sibling(label = "third", throwsOnDispose = true),
                    ),
                ),
            )

            /** Primary error rethrown only after the multi-child mutation has fully committed. */
            val failure = assertThrows(IllegalStateException::class.java) {
                buildRuntime.resolveElementTree(root(emptyList()))
            }

            assertEquals("dispose:first", failure.message)
            assertEquals(listOf("dispose:third"), failure.suppressed.map { error -> error.message })
            assertEquals(listOf("first", "second", "third"), disposalOrder)
            assertTrue(createdStates.all { state -> !state.mounted })
            /** Owner snapshot after the caught exception must contain only the reusable root. */
            val diagnosticsAfterFailure = buildRuntime.collectDiagnostics()
            assertEquals(1, diagnosticsAfterFailure.elementDiagnostics.size)
            assertEquals(0, diagnosticsAfterFailure.dirtyQueueDiagnostics.pendingElementCount)

            /** A later replacement proves no stale slot or deferred owner failure is retained. */
            buildRuntime.resolveElementTree(
                root(listOf(sibling(label = "replacement", throwsOnDispose = false))),
            )
            assertEquals(3, createdStates.count { state -> !state.mounted })
            assertTrue(createdStates.last().mounted)
            assertEquals(0, buildRuntime.collectDiagnostics().dirtyQueueDiagnostics.pendingElementCount)
        } finally {
            buildRuntime.dispose()
        }
        assertFalse(createdStates.last().mounted)
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
                    fillColor = if (accent) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                    borderColor = null,
                ),
            )
        }
    }

    private class DiagnosticsStatefulWidget : StatefulWidget() {
        override fun createState(): State<out StatefulWidget> {
            return DiagnosticsState().also { state ->
                latestState = state
            }
        }

        companion object {
            lateinit var latestState: DiagnosticsState
        }
    }

    private class DiagnosticsState : State<DiagnosticsStatefulWidget>() {
        var buildCount = 0
            private set

        fun markTwice() {
            setState { }
            setState { }
        }

        override fun build(context: BuildContext): Widget {
            buildCount += 1
            return Container(
                width = 4,
                height = 4,
                fillColor = PixelColor.White,
                borderColor = null,
            )
        }
    }

    /** Stateful sibling fixture whose user disposal callback can fail on demand. */
    private class ThrowingDisposeWidget(
        /** Stable label identifying this fixture in callback order and error messages. */
        val label: String,
        /** Whether this fixture throws after recording its disposal. */
        val throwsOnDispose: Boolean,
        /** Shared callback-order sink owned by the test. */
        val disposalOrder: MutableList<String>,
        /** Shared State sink used to verify terminal detachment. */
        val createdStates: MutableList<ThrowingDisposeState>,
        /** Stable sibling key preventing reconciliation from migrating State. */
        override val key: Any,
    ) : StatefulWidget(key = key) {
        /** Creates and exposes the exact State paired with this keyed fixture. */
        override fun createState(): State<out StatefulWidget> {
            return ThrowingDisposeState().also(createdStates::add)
        }
    }

    /** Records terminal order and optionally throws from the public State callback. */
    private class ThrowingDisposeState : State<ThrowingDisposeWidget>() {
        /** Builds a concrete render leaf so descendant render teardown is also exercised. */
        override fun build(context: BuildContext): Widget {
            return TestRenderWidget(label = "state:${widget.label}")
        }

        /** Records disposal before raising the configured deterministic failure. */
        override fun dispose() {
            widget.disposalOrder += widget.label
            if (widget.throwsOnDispose) error("dispose:${widget.label}")
        }
    }

    private class ToneScope(
        val tone: PixelColor,
        override val child: Widget,
    ) : InheritedWidget(child) {
        override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
            return tone != (oldWidget as? ToneScope)?.tone
        }

        companion object {
            fun of(context: BuildContext): PixelColor {
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
                fillColor = ToneScope.of(context),
                borderColor = null,
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
                fillColor = if (direction == TextDirection.RTL) {
                    PixelColor.fromRgb(200, 100, 0)
                } else {
                    PixelColor.White
                },
                borderColor = null,
            )
        }
    }

    private class CounterConsumerWidget : com.purride.pixelui.StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            return Container(
                width = 4,
                height = 4,
                fillColor = if (CounterScope.of(context) > 0) {
                    PixelColor.fromRgb(200, 100, 0)
                } else {
                    PixelColor.White
                },
                borderColor = null,
            )
        }
    }

    private class TestRenderWidget(
        private val label: String,
    ) : RenderObjectWidget() {
        override fun createRenderObject(context: BuildContext): RenderObject {
            return TestRenderObject()
        }

        override fun updateRenderObject(
            context: BuildContext,
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

    private class TestSingleChildRenderWidget(
        private val label: String,
        child: Widget,
    ) : SingleChildRenderObjectWidget(child = child) {
        override fun createRenderObject(context: BuildContext): RenderObject {
            return TestSingleChildRenderObject()
        }

        override fun updateRenderObject(
            context: BuildContext,
            renderObject: RenderObject,
        ) {
            (renderObject as TestSingleChildRenderObject).label = label
        }
    }

    private class TestSingleChildRenderObject : SingleChildRenderObject() {
        var label: String = ""
        val renderChild: RenderObject?
            get() = child

        override fun layout(constraints: RenderConstraints) {
            size = RenderSize.Zero
        }

        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) = Unit
    }

    private class TestMultiChildRenderWidget(
        private val label: String,
        children: List<Widget>,
    ) : MultiChildRenderObjectWidget(children = children) {
        override fun createRenderObject(context: BuildContext): RenderObject {
            return TestMultiChildRenderObject()
        }

        override fun updateRenderObject(
            context: BuildContext,
            renderObject: RenderObject,
        ) {
            (renderObject as TestMultiChildRenderObject).label = label
        }
    }

    private class TestMultiChildRenderObject : MultiChildRenderObject() {
        var label: String = ""
        val renderChildren: List<RenderObject>
            get() = children

        override fun layout(constraints: RenderConstraints) {
            size = RenderSize.Zero
        }

        override fun paint(
            context: PaintContext,
            offsetX: Int,
            offsetY: Int,
        ) = Unit
    }
}
