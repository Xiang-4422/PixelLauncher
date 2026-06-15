package com.purride.pixeldemo.showcase.integration

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.PixelInspectorAllocationSample
import com.purride.pixelui.PixelInspectorPanel
import com.purride.pixelui.PixelInspectorSnapshot
import com.purride.pixelui.PixelInspectorTargetCounts
import com.purride.pixelui.PixelInspectorTargetKind
import com.purride.pixelui.PixelInspectorTargetSnapshot
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

/**
 * 演示 [PixelDebugOverlay]：用一个伪造的 [PixelHostFrameStats] 静态展示
 * overlay 视觉规格。
 *
 * 真实使用：调用 `hostView.frameStatsObserver = { notifier.value = it }`，
 * 然后在树里用 `ValueListenableBuilder(notifier) { _, s -> PixelDebugOverlay(s) }`。
 * 因为 demo runner 不暴露 hostView 直接给场景，这里用静态值演示样式即可。
 */
object DebugOverlayScene : DemoScene {
    override val id = "debug_overlay"
    override val title = "DEBUG OVERLAY"
    override val description = "FPS / target / ticker diagnostics and inspector panel"

    override fun build(env: DemoEnv): Widget {
        val fakeStats = PixelHostFrameStats(
            deltaMs = 16,
            fpsAvg = 60f,
            paintTimeNanos = 2_500_000,
            frameCount = 1234,
        )
        val fakeInspector = PixelInspectorSnapshot(
            frameStats = fakeStats,
            allocationSample = PixelInspectorAllocationSample(
                usedHeapBytes = 512L * 1024L,
                totalHeapBytes = 2L * 1024L * 1024L,
                maxHeapBytes = 16L * 1024L * 1024L,
            ),
            targetCounts = PixelInspectorTargetCounts(
                click = 4,
                pager = 1,
                list = 2,
                scrollbar = 1,
                refresh = 0,
                textInput = 1,
                slider = 1,
                semantics = 6,
            ),
            targetSnapshots = listOf(
                PixelInspectorTargetSnapshot(
                    kind = PixelInspectorTargetKind.CLICK,
                    left = 4,
                    top = 8,
                    width = 32,
                    height = 9,
                    detail = "#0",
                ),
                PixelInspectorTargetSnapshot(
                    kind = PixelInspectorTargetKind.LIST,
                    left = 2,
                    top = 24,
                    width = 92,
                    height = 48,
                    detail = "#0 active=1 content=160 viewport=48",
                ),
                PixelInspectorTargetSnapshot(
                    kind = PixelInspectorTargetKind.TEXT_INPUT,
                    left = 8,
                    top = 74,
                    width = 72,
                    height = 10,
                    detail = "#0 readOnly=0 action=DONE",
                ),
            ),
            elementTree = "RootElement\n  ColumnElement\n    PixelDebugOverlay\n    PixelInspectorPanel",
            renderTree = "RenderHostRoot\n  RenderFlex\n    RenderDecoratedBox\n    RenderText",
            semanticsTree = "SemanticsRoot\n  role=panel label=Debug\n  role=button label=RENDER",
            hasPendingBuild = false,
            focusedTextInput = true,
            activePagerCount = 1,
            activeListCount = 1,
            activeSlider = false,
            activeScrollbar = false,
            activeRefresh = false,
        )
        val labelStyle = TextStyle(color = PixelColor.fromRgb(160, 160, 160))
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("OVERLAY", style = labelStyle),
                    SizedBox(height = 2),
                    PixelDebugOverlay(stats = fakeStats, inspector = fakeInspector, activeTickerCount = 2),
                    SizedBox(height = 6),
                    PixelInspectorPanel(snapshot = fakeInspector, maxTreeLines = 4),
                    SizedBox(height = 6),
                    Text("NULL → 0x0", style = labelStyle),
                    SizedBox(height = 2),
                    PixelDebugOverlay(stats = null),
                ),
            ),
        )
    }
}
