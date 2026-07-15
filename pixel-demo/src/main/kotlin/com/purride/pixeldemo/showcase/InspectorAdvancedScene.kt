@file:OptIn(com.purride.pixelui.advanced.PixelExperimentalApi::class)

package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelFrameDropReason
import com.purride.pixelui.PixelFrameTimings
import com.purride.pixelui.PixelFrameWorkload
import com.purride.pixelui.PixelHostFrameDiagnostics
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.PixelInspectorAllocationSample
import com.purride.pixelui.PixelInspectorBoundsOverlay
import com.purride.pixelui.PixelInspectorPanel
import com.purride.pixelui.PixelInspectorSnapshot
import com.purride.pixelui.PixelInspectorTargetCounts
import com.purride.pixelui.PixelInspectorTargetKind
import com.purride.pixelui.PixelInspectorTargetSnapshot
import com.purride.pixelui.PixelUiState
import com.purride.pixelui.Row
import com.purride.pixelui.Stack
import com.purride.pixelui.StateSetter
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.WidgetBuilder
import com.purride.pixelui.targetAt
import com.purride.pixelui.advanced.PixelHitTestResult
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelMultiChildRenderObject
import com.purride.pixelui.advanced.PixelMultiChildRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderObjectWidget
import com.purride.pixelui.advanced.PixelRenderObjectWithChild
import com.purride.pixelui.advanced.PixelRenderObjectWithChildren
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.advanced.PixelSingleChildRenderObject
import com.purride.pixelui.advanced.PixelSingleChildRenderObjectWidget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object InspectorAdvancedScene : DemoScene {
    override val id = "deep_inspector_advanced"
    override val title = "Inspector 与高级渲染"
    override val summary = "完整帧阶段、Debug overlay、Inspector panel 与自定义 RenderObject"
    override val category = DemoCatalog.debug
    override val tags = setOf("inspector", "debug", "renderobject", "advanced", "hit-test")
    override val apis = setOf(
        "PixelDebugOverlay",
        "PixelInspectorPanel",
        "PixelInspectorBoundsOverlay",
        "PixelInspectorSnapshot",
        "PixelInspectorTargetSnapshot",
        "PixelHostFrameDiagnostics",
        "PixelInspectorTargetKind",
        "PixelInspectorTargetCounts",
        "PixelInspectorAllocationSample",
        "PixelRenderObjectWidget",
        "PixelLeafRenderObjectWidget",
        "PixelSingleChildRenderObjectWidget",
        "PixelMultiChildRenderObjectWidget",
        "PixelRenderObject",
        "PixelRenderBox",
        "PixelSingleChildRenderObject",
        "PixelMultiChildRenderObject",
        "PixelRenderConstraints",
        "PixelRenderSize",
        "PixelPaintContext",
        "PixelHitTestResult",
        "PixelRenderObjectWithChild",
        "PixelRenderObjectWithChildren",
        "PixelUiState",
        "WidgetBuilder",
        "StateSetter",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body(env))

    private fun body(env: DemoEnv): Widget {
        val snapshot = demoInspectorSnapshot()
        val target = snapshot.targetAt(42, 20)
        val uiState = PixelUiState(
            focusedNodeKey = "inspector-panel",
            activeScrollerKey = "render-strip",
            activeGestureKey = target?.kind,
        )
        val builder: WidgetBuilder = { _ ->
            Text("WidgetBuilder -> PixelRenderObjectWidget", style = TextStyle(color = Muted))
        }
        val setter: StateSetter = { action -> action() }
        setter {}
        val leafWidget: PixelRenderObjectWidget = DemoRenderDot(Accent)

        return Column(
            children = listOf(
                sectionTitle("Inspector"),
                samplePanel(
                    title = "Debug overlay / bounds overlay",
                    color = Yellow,
                    child = Row(
                        children = listOf(
                            PixelDebugOverlay(
                                stats = snapshot.frameStats,
                                inspector = snapshot,
                                activeTickerCount = env.vsync.activeTickerCount,
                            ),
                            Container(
                                width = 86,
                                height = 44,
                                borderColor = Yellow,
                                child = Stack(
                                    children = listOf(
                                        Container(
                                            padding = EdgeInsets.all(2),
                                            child = Text("targets=${snapshot.targetSnapshots.size}", style = TextStyle(color = Muted)),
                                        ),
                                        PixelInspectorBoundsOverlay(
                                            snapshot = snapshot,
                                            width = 86,
                                            height = 44,
                                            selectedTarget = target,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                ),
                samplePanel(
                    title = "PixelInspectorPanel",
                    color = Purple,
                    child = PixelInspectorPanel(
                        snapshot = snapshot,
                        selectedTarget = target,
                        maxTreeLines = 5,
                    ),
                ),
                sectionTitle("Advanced RenderObject"),
                samplePanel(
                    title = "leaf / single-child / multi-child",
                    color = Cyan,
                    child = Column(
                        children = listOf(
                            DemoRenderStrip(
                                children = listOf(
                                    leafWidget,
                                    DemoRenderInset(
                                        color = Green,
                                        child = Text("single child", style = TextStyle(color = PixelColor.White)),
                                    ),
                                    DemoRenderDot(Pink),
                                ),
                            ),
                            builder(BuildContextProbe),
                            Text(
                                "ui focus=${uiState.focusedNodeKey} gesture=${uiState.activeGestureKey}",
                                style = TextStyle(color = Cyan),
                            ),
                        ),
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}

private object BuildContextProbe : BuildContext {
    override val widget: Widget = Text("")

    override fun <T : com.purride.pixelui.InheritedWidget> dependOnInheritedWidgetOfExactType(
        type: kotlin.reflect.KClass<T>,
    ): T? = null

    override fun <T : com.purride.pixelui.InheritedWidget> getInheritedWidgetOfExactType(
        type: kotlin.reflect.KClass<T>,
    ): T? = null

    override fun watch(listenable: com.purride.pixelui.Listenable?) = Unit
}

private class DemoRenderDot(
    private val color: PixelColor,
    key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        DemoDotRender(color)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as DemoDotRender).update(color)
    }
}

private class DemoDotRender(
    private var color: PixelColor,
) : PixelRenderBox() {
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(18),
            height = constraints.constrainHeight(12),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, PixelColor.fromRgb(12, 14, 18))
        context.drawRect(offsetX, offsetY, size.width, size.height, color)
        context.fillRect(offsetX + 4, offsetY + 3, 10, 6, color)
    }

    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        if (localX in 0 until size.width && localY in 0 until size.height) {
            result.add(this)
        }
    }

    fun update(next: PixelColor) {
        if (next == color) return
        color = next
        markNeedsPaint()
    }
}

private class DemoRenderInset(
    private val color: PixelColor,
    child: Widget,
    key: Any? = null,
) : PixelSingleChildRenderObjectWidget(child = child, key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        DemoInsetRender(color)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as DemoInsetRender).update(color)
    }
}

private class DemoInsetRender(
    private var color: PixelColor,
) : PixelSingleChildRenderObject(), PixelRenderObjectWithChild {
    override fun layout(constraints: PixelRenderConstraints) {
        val childBox = child as? PixelRenderBox
        childBox?.layout(constraints.inset(left = 3, top = 2, right = 3, bottom = 2))
        size = PixelRenderSize(
            width = constraints.constrainWidth((childBox?.size?.width ?: 42) + 6),
            height = constraints.constrainHeight((childBox?.size?.height ?: 8) + 4),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.drawRect(offsetX, offsetY, size.width, size.height, color)
        (child as? PixelRenderBox)?.paint(context, offsetX + 3, offsetY + 2)
    }

    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        (child as? PixelRenderBox)?.hitTest(localX - 3, localY - 2, result)
        if (localX in 0 until size.width && localY in 0 until size.height) result.add(this)
    }

    fun update(next: PixelColor) {
        if (next == color) return
        color = next
        markNeedsPaint()
    }
}

private class DemoRenderStrip(
    children: List<Widget>,
    key: Any? = null,
) : PixelMultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        DemoStripRender()
}

private class DemoStripRender : PixelMultiChildRenderObject(), PixelRenderObjectWithChildren {
    private val childOffsets = mutableListOf<Int>()

    override fun layout(constraints: PixelRenderConstraints) {
        val childConstraints = constraints.inset(left = 2, top = 2, right = 2, bottom = 2)
        var x = 2
        var height = 0
        childOffsets.clear()
        children.forEach { child ->
            val box = child as? PixelRenderBox ?: return@forEach
            box.layout(childConstraints)
            childOffsets += x
            x += box.size.width + 2
            height = maxOf(height, box.size.height)
        }
        size = PixelRenderSize(
            width = constraints.constrainWidth(x + 2),
            height = constraints.constrainHeight(height + 4),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        context.fillRect(offsetX, offsetY, size.width, size.height, PixelColor.fromRgb(8, 10, 12))
        context.drawRect(offsetX, offsetY, size.width, size.height, Blue)
        children.forEachIndexed { index, child ->
            (child as? PixelRenderBox)?.paint(context, offsetX + childOffsets.getOrElse(index) { 2 }, offsetY + 2)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        children.forEachIndexed { index, child ->
            val box = child as? PixelRenderBox ?: return@forEachIndexed
            box.hitTest(localX - childOffsets.getOrElse(index) { 2 }, localY - 2, result)
        }
        if (localX in 0 until size.width && localY in 0 until size.height) result.add(this)
    }
}

private fun demoInspectorSnapshot(): PixelInspectorSnapshot {
    val targets = listOf(
        PixelInspectorTargetSnapshot(
            kind = PixelInspectorTargetKind.CLICK,
            left = 4,
            top = 5,
            width = 26,
            height = 12,
            detail = "button.primary",
            elementPath = "0:Root/1:Button",
            renderPath = "0:RenderRoot/1:RenderButton",
        ),
        PixelInspectorTargetSnapshot(
            kind = PixelInspectorTargetKind.LIST,
            left = 35,
            top = 8,
            width = 44,
            height = 22,
            detail = "list#catalog",
            elementPath = "0:Root/2:ListView",
            renderPath = "0:RenderRoot/2:LazyList",
        ),
        PixelInspectorTargetSnapshot(
            kind = PixelInspectorTargetKind.SEMANTICS,
            left = 18,
            top = 24,
            width = 55,
            height = 12,
            detail = "label=Inspector",
            elementPath = "0:Root/3:Semantics",
            renderPath = "0:RenderRoot/3:RenderSemantics",
        ),
    )
    return PixelInspectorSnapshot(
        frameStats = PixelHostFrameStats(
            deltaMs = 16,
            fpsAvg = 59.7f,
            paintTimeNanos = 1_650_000,
            frameCount = 120,
        ),
        allocationSample = PixelInspectorAllocationSample(
            usedHeapBytes = 3_540_992,
            totalHeapBytes = 8_388_608,
            maxHeapBytes = 64_000_000,
        ),
        targetCounts = PixelInspectorTargetCounts(
            click = 1,
            pager = 0,
            list = 1,
            scrollbar = 0,
            refresh = 0,
            textInput = 0,
            slider = 0,
            semantics = 1,
        ),
        targetSnapshots = targets,
        elementTree = "Root\n  DemoShell\n    InspectorPanel\n    RenderStrip",
        renderTree = "RenderRoot 128x160\n  RenderBox 86x44\n  DemoStripRender 94x18",
        semanticsTree = "button Inspector\nlist Catalog\ntext Debug stats",
        hasPendingBuild = false,
        focusedTextInput = false,
        activePagerCount = 0,
        activeListCount = 1,
        activeSlider = false,
        activeScrollbar = false,
        activeRefresh = false,
    ).withFrameDiagnostics(
        PixelHostFrameDiagnostics(
            frameNumber = 120L,
            frameIntervalNanos = 16_666_667L,
            frameBudgetNanos = 16_666_667L,
            timings = PixelFrameTimings(
                buildNanos = 210_000L,
                layoutNanos = 330_000L,
                paintNanos = 16_640_000L,
                bufferSubmitNanos = 390_000L,
                androidDrawNanos = 80_000L,
                totalFrameNanos = 17_730_000L,
                unattributedNanos = 80_000L,
            ),
            workload = PixelFrameWorkload(
                dirtyElementCount = 4,
                dirtyRenderNodeCount = 18,
                paintedPixelCount = 20_480L,
                submittedPixelCount = 20_480L,
                allocatedBytes = 1_024L,
                garbageCollectionCount = 0L,
                bufferCacheHitCount = 2L,
                bufferCacheMissCount = 0L,
                renderCacheHit = false,
            ),
            dropReason = PixelFrameDropReason.PAINT,
            missedVsyncCount = 1,
        ),
    )
}
