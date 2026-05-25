package com.purride.pixeldemo.showcase.integration

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
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
    override val description = "FPS / 帧时间 overlay 视觉样式"

    override fun build(env: DemoEnv): Widget {
        val fakeStats = PixelHostFrameStats(
            deltaMs = 16,
            fpsAvg = 60f,
            paintTimeNanos = 2_500_000,
            frameCount = 1234,
        )
        val labelStyle = TextStyle(color = PixelColor.fromRgb(160, 160, 160))
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("OVERLAY", style = labelStyle),
                    SizedBox(height = 2),
                    PixelDebugOverlay(stats = fakeStats),
                    SizedBox(height = 6),
                    Text("NULL → 0x0", style = labelStyle),
                    SizedBox(height = 2),
                    PixelDebugOverlay(stats = null),
                ),
            ),
        )
    }
}
