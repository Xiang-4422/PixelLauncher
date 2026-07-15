package com.purride.pixelmicrobenchmark

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.benchmark.BlackHole
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.benchmark.junit4.measureRepeatedOnMainThread
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.Opacity
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 覆盖 M6-3 六类渲染与提交最坏场景的官方设备 Microbenchmark。 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PixelRenderWorstCaseMicrobenchmark {
    /** 提供预热、重复计时、分配统计和热状态检查的官方规则。 */
    @get:Rule
    val benchmarkRule: BenchmarkRule = BenchmarkRule()

    /** 测量全亮逻辑帧经无 gap Bitmap 快速路径提交到完整物理画布的成本。 */
    @Test
    fun fullBrightnessNoGapCanvasSubmit() {
        /** 在 Android 主线程提前创建、布局并填充的离屏提交夹具。 */
        val fixture = createCanvasSubmitFixture(pixelGapEnabled = false)
        try {
            benchmarkRule.measureRepeatedOnMainThread {
                fixture.drawAndConsume()
            }
        } finally {
            disposeCanvasSubmitFixture(fixture)
        }
    }

    /** 测量全亮方形点阵经 Bitmap 与内部 bezel gap 组合提交的最坏成本。 */
    @Test
    fun squareGapGridCanvasSubmit() {
        /** 开启默认 gap 的完整物理画布提交夹具。 */
        val fixture = createCanvasSubmitFixture(pixelGapEnabled = true)
        try {
            benchmarkRule.measureRepeatedOnMainThread {
                fixture.drawAndConsume()
            }
        } finally {
            disposeCanvasSubmitFixture(fixture)
        }
    }

    /** 测量三层 opacity 离屏合成、alpha 缩放和回写父缓冲的完整逻辑帧。 */
    @Test
    fun nestedOpacityPaint() {
        measureAlternatingWidgetFrames(
            first = opacityFixture(variant = 0),
            second = opacityFixture(variant = 1),
            logicalWidth = LogicalWidth,
            logicalHeight = LogicalHeight,
            sampleX = LogicalWidth / 2,
            sampleY = LogicalHeight / 2,
        )
    }

    /** 测量大于 viewport 的内容在负向平移后执行矩形裁剪与区域合成的成本。 */
    @Test
    fun clippedOverflowPaint() {
        measureAlternatingWidgetFrames(
            first = clipFixture(variant = 0),
            second = clipFixture(variant = 1),
            logicalWidth = LogicalWidth,
            logicalHeight = LogicalHeight,
            sampleX = LogicalWidth - 1,
            sampleY = LogicalHeight - 1,
        )
    }

    /** 测量长段落的 grapheme、Bidi、换行、fallback glyph、布局与绘制。 */
    @Test
    fun complexTextLayoutAndPaint() {
        measureAlternatingWidgetFrames(
            first = complexTextFixture(variant = 0),
            second = complexTextFixture(variant = 1),
            logicalWidth = LogicalWidth,
            logicalHeight = LogicalHeight,
            sampleX = LogicalWidth / 3,
            sampleY = LogicalHeight / 3,
        )
    }

    /** 测量 5,000 行定高懒列表从首屏快速跳到远端窗口后的重建、布局与绘制。 */
    @Test
    fun fastLazyListScrollPaint() {
        /** 跨迭代复用的懒列表控制器。 */
        val controller = PixelListController()
        /** 跨迭代复用的滚动状态。 */
        val state = controller.create()
        /** 使用生产定高快速路径和较大 cache extent 的最坏场景列表。 */
        val list = Container(
            height = ScrollViewportHeight,
            child = ListViewBuilder(
                itemCount = ScrollItemCount,
                state = state,
                controller = controller,
                itemExtent = ScrollItemExtent,
                cacheExtent = ScrollCacheExtent,
                itemBuilder = { index -> Text("ROW ${index.toString().padStart(4, '0')} — אבג") },
            ),
        )
        /** 只在计时区外创建和销毁的 retained runtime。 */
        val tester = PixelTester()
        try {
            /** 首屏冷启动在计时前完成，样本只包含真实滚动帧。 */
            tester.pumpWidget(list, LogicalWidth, ScrollViewportHeight)
            /** 在两个远端窗口间切换，保证每次迭代都执行重建、布局和绘制。 */
            var usePrimaryTarget = true
            benchmarkRule.measureRepeated {
                controller.scrollTo(
                    state = state,
                    targetOffsetPx = if (usePrimaryTarget) {
                        ScrollTargetOffset
                    } else {
                        ScrollAlternateTargetOffset
                    },
                    viewportHeightPx = ScrollViewportHeight,
                    contentHeightPx = ScrollItemCount * ScrollItemExtent,
                )
                usePrimaryTarget = !usePrimaryTarget
                tester.pumpFrame(FrameStepMs)
                BlackHole.consume(tester.pixelAt(LogicalWidth / 2, ScrollViewportHeight / 2))
            }
        } finally {
            tester.dispose()
        }
    }

    /** 复用同一个 retained runtime，在两个预构建树之间交替触发完整逻辑帧。 */
    private fun measureAlternatingWidgetFrames(
        first: Widget,
        second: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
        sampleX: Int,
        sampleY: Int,
    ) {
        /** 计时区外创建并在全部样本结束后释放的测试驱动器。 */
        val tester = PixelTester()
        try {
            /** 首个冷态 build/layout/paint 不计入稳态样本。 */
            tester.pumpWidget(first, logicalWidth, logicalHeight)
            /** 交替标记，确保相邻迭代输入不同且不会被静态缓存吞掉。 */
            var useSecond = true
            benchmarkRule.measureRepeated {
                tester.pumpWidget(
                    widget = if (useSecond) second else first,
                    logicalWidth = logicalWidth,
                    logicalHeight = logicalHeight,
                )
                useSecond = !useSecond
                BlackHole.consume(tester.pixelAt(sampleX, sampleY))
            }
        } finally {
            tester.dispose()
        }
    }

    /** 在 Android 主线程创建一个固定逻辑/物理尺寸的 Canvas 提交夹具。 */
    private fun createCanvasSubmitFixture(pixelGapEnabled: Boolean): CanvasSubmitFixture {
        /** 用于满足 Host 平台源主线程约束的 instrumentation。 */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** 由主线程写入、测试线程读取的已完成夹具。 */
        var fixture: CanvasSubmitFixture? = null
        instrumentation.runOnMainSync {
            /** 全部逻辑像素均为不透明白色的最坏提交缓冲。 */
            val buffer = PixelBuffer(CanvasLogicalWidth, CanvasLogicalHeight).apply {
                fillRect(
                    left = 0,
                    top = 0,
                    rectWidth = CanvasLogicalWidth,
                    rectHeight = CanvasLogicalHeight,
                    color = PixelColor.White,
                )
            }
            /** 未挂载 Window、但执行真实 PixelHostView.onDraw 的离屏 Host。 */
            val host = PixelHostView(ApplicationProvider.getApplicationContext()).apply {
                layout(0, 0, CanvasPhysicalWidth, CanvasPhysicalHeight)
                setPixelGapEnabled(pixelGapEnabled)
                setPixelGapRatio(1f)
                submitFrame(
                    pixelBuffer = buffer,
                    screenProfile = CanvasProfile,
                    backgroundColor = PixelColor.Black,
                )
            }
            /** 接收真实 Android Canvas 软件栅格输出的复用物理位图。 */
            val bitmap = Bitmap.createBitmap(
                CanvasPhysicalWidth,
                CanvasPhysicalHeight,
                Bitmap.Config.ARGB_8888,
            )
            fixture = CanvasSubmitFixture(host = host, bitmap = bitmap)
        }
        return requireNotNull(fixture)
    }

    /** 在 Android 主线程释放 Host 生命周期资源和物理 bitmap。 */
    private fun disposeCanvasSubmitFixture(fixture: CanvasSubmitFixture) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            fixture.dispose()
        }
    }

    /** 构造三层 opacity 与高密度不透明子节点组成的逻辑合成树。 */
    private fun opacityFixture(variant: Int): Widget {
        /** 填满逻辑 viewport 的多行彩色表面。 */
        val content = denseColorGrid(rows = 20, columns = 15)
        /** 两个稳定 alpha 组合之间的微小差值，强制 opacity 层重新合成。 */
        val opacityDelta = variant * 0.01f
        return Opacity(
            opacity = 0.82f - opacityDelta,
            child = Opacity(
                opacity = 0.67f + opacityDelta,
                child = Opacity(opacity = 0.53f - opacityDelta, child = content),
            ),
        )
    }

    /** 构造尺寸大于 viewport 的内容，并在负向平移后交给 ClipRect 裁剪。 */
    private fun clipFixture(variant: Int): Widget {
        /** 超出可视区域、用于制造四边裁剪和大量拒绝像素的内容。 */
        val oversizedContent = denseColorGrid(rows = 28, columns = 21)
        /** 两个负向位移之间的单像素差，强制变换与 clip 区域更新。 */
        val offsetDelta = variant.coerceIn(0, 1)
        return ClipRect(
            child = Container(
                width = LogicalWidth,
                height = LogicalHeight,
                child = Transform.translate(
                    offset = IntOffset(x = -32 + offsetDelta, y = -24 - offsetDelta),
                    child = oversizedContent,
                ),
            ),
        )
    }

    /** 构造包含多脚本、组合字符、emoji cluster、CRLF 与长换行的复杂段落。 */
    private fun complexTextFixture(variant: Int): Widget {
        /** 保持等宽但不同的文本标记，强制段落内容和 visual run 重建。 */
        val variantMarker = if (variant == 0) "A" else "B"
        /** 多个样式 span 共同触发段落集群、fallback 与视觉 run 重建。 */
        val spans = List(ComplexTextSpanCount) { index ->
            PixelTextSpan(
                "${index.toString().padStart(2, '0')}$variantMarker Café 👨‍👩‍👧‍👦 🇨🇳 ABC אבג 123 العربية हिन्दी\r\n",
                style = PixelTextStyle(
                    color = if (index % 2 == 0) PixelColor.White else AccentColor,
                ),
            )
        }
        return RichText(
            spans = spans,
            softWrap = true,
            maxLines = ComplexTextMaxLines,
        )
    }

    /** 构造固定单元尺寸的高密度彩色 Row/Column 树。 */
    private fun denseColorGrid(rows: Int, columns: Int): Widget {
        return Column(
            children = List(rows) { rowIndex ->
                Row(
                    children = List(columns) { columnIndex ->
                        Container(
                            width = DenseCellWidth,
                            height = DenseCellHeight,
                            fillColor = if ((rowIndex + columnIndex) % 2 == 0) AccentColor else PixelColor.White,
                        )
                    },
                    crossAxisAlignment = CrossAxisAlignment.START,
                )
            },
            crossAxisAlignment = CrossAxisAlignment.START,
        )
    }

    /** 封装一个可在主线程重复执行并最终释放的真实 Canvas 提交夹具。 */
    private class CanvasSubmitFixture(
        /** 执行生产 `onDraw` 和 buffer submit 的离屏 Host。 */
        private val host: PixelHostView,
        /** 每次迭代复用的完整物理 ARGB bitmap。 */
        private val bitmap: Bitmap,
    ) {
        /** 绑定复用 bitmap 的软件 Canvas，避免把 Canvas 包装对象分配计入每帧热路径。 */
        private val canvas: Canvas = Canvas(bitmap)

        /** 执行一帧真实 Host 绘制，并消费中心像素防止结果被优化掉。 */
        fun drawAndConsume() {
            host.draw(canvas)
            BlackHole.consume(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        }

        /** 释放 Host 内部缓存、平台观察器和物理输出 bitmap。 */
        fun dispose() {
            host.dispose()
            bitmap.recycle()
        }
    }

    /** 六类最坏场景共用的稳定尺寸、数量、颜色与帧步长。 */
    private companion object {
        /** 逻辑 opacity/clip/text viewport 宽度。 */
        const val LogicalWidth: Int = 160

        /** 逻辑 opacity/clip/text viewport 高度。 */
        const val LogicalHeight: Int = 120

        /** Canvas 提交场景的逻辑列数。 */
        const val CanvasLogicalWidth: Int = 270

        /** Canvas 提交场景的逻辑行数。 */
        const val CanvasLogicalHeight: Int = 600

        /** Canvas 提交场景的物理宽度。 */
        const val CanvasPhysicalWidth: Int = 1080

        /** Canvas 提交场景的物理高度。 */
        const val CanvasPhysicalHeight: Int = 2400

        /** 方形整数 viewport 使用的固定逻辑屏幕 profile。 */
        val CanvasProfile: ScreenProfile = ScreenProfile(
            logicalWidth = CanvasLogicalWidth,
            logicalHeight = CanvasLogicalHeight,
            dotSizePx = 4,
            pixelShape = PixelShape.SQUARE,
        )

        /** 高密度网格单元宽度。 */
        const val DenseCellWidth: Int = 8

        /** 高密度网格单元高度。 */
        const val DenseCellHeight: Int = 6

        /** 复杂文本使用的 span 数量。 */
        const val ComplexTextSpanCount: Int = 32

        /** 复杂文本允许生成的最大行数。 */
        const val ComplexTextMaxLines: Int = 64

        /** 快速滚动场景的懒列表总行数。 */
        const val ScrollItemCount: Int = 5_000

        /** 快速滚动场景的固定行高。 */
        const val ScrollItemExtent: Int = 8

        /** 快速滚动场景的逻辑 viewport 高度。 */
        const val ScrollViewportHeight: Int = 120

        /** 视口上下额外保留的离屏行数。 */
        const val ScrollCacheExtent: Int = 16

        /** 每次迭代从首屏跳到的远端绝对偏移。 */
        const val ScrollTargetOffset: Float = 24_000f

        /** 与主偏移交替使用的第二个远端绝对偏移。 */
        const val ScrollAlternateTargetOffset: Float = 8_000f

        /** 控制器变化后推进的一帧确定性时间。 */
        const val FrameStepMs: Long = 16L

        /** opacity、clip 与复杂文本交替使用的非白色。 */
        val AccentColor: PixelColor = PixelColor.fromRgb(0x40, 0xA0, 0xFF)
    }
}
