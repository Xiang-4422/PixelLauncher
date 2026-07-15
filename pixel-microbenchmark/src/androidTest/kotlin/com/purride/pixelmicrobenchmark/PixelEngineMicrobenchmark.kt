package com.purride.pixelmicrobenchmark

import androidx.benchmark.BlackHole
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelBufferPool
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelResourceManifestJsonLoader
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.PixelGraphemeBoundaryMap
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 覆盖引擎五条代表性 CPU 热路径的官方设备 Microbenchmark。 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class PixelEngineMicrobenchmark {
    /** 提供预热、重复计时、分配输出和热状态检查的官方规则。 */
    @get:Rule
    val benchmarkRule: BenchmarkRule = BenchmarkRule()

    /** 测量代表性嵌套树的完整 retained layout/paint。 */
    @Test
    fun retainedLayoutAndPaint() = benchmarkRule.measureRepeated {
        /** 隔离 runtime，防止缓存帧掩盖布局工作。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(layoutFixture(), LogicalWidth, LogicalHeight)
            BlackHole.consume(tester.dumpRenderTree())
        } finally {
            tester.dispose()
        }
    }

    /** 通过公开 RichText 表面测量 mixed-Bidi/grapheme 段落布局。 */
    @Test
    fun mixedBidiParagraphLayout() = benchmarkRule.measureRepeated {
        /** 隔离 runtime，强制重建段落 cluster 和视觉 run。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(paragraphFixture(), LogicalWidth, LogicalHeight)
            BlackHole.consume(tester.dumpSemanticsTree())
        } finally {
            tester.dispose()
        }
    }

    /** 在固定负载中测量不透明填充、alpha 混合、裁剪 blit 和 pool 复用。 */
    @Test
    fun pixelBufferOperations() = benchmarkRule.measureRepeated {
        /** 每次迭代独立的 pool 同时覆盖 miss 和复用路径，且不串联状态。 */
        val pool = PixelBufferPool(maxBuffersPerKey = 2)
        /** 同时包含不透明和半透明像素的源 buffer。 */
        val source = pool.acquire(BufferWidth, BufferHeight)
        /** 接收裁剪和混合副本的目标 buffer。 */
        val destination = pool.acquire(BufferWidth, BufferHeight)
        source.fillRect(0, 0, BufferWidth, BufferHeight, PixelColor.White)
        source.fillRect(8, 8, 32, 24, PixelColor(0x8040A0FF.toInt()))
        destination.blitRegion(source, 4, 4, 80, 48, -3, 2)
        pool.release(source)
        pool.release(destination)
        BlackHole.consume(pool.stats())
    }

    /** 测量经过完整校验的 v2 bitmap/sprite/color/font catalog 解析。 */
    @Test
    fun resourceCatalogParsing() = benchmarkRule.measureRepeated {
        /** 消费解析后的 catalog，确保完整校验结果保持可观测。 */
        val catalog = PixelResourceManifestJsonLoader.parseCatalog(ResourceCatalogJson)
        BlackHole.consume(catalog)
    }

    /** 测量 Unicode boundary 构建和代表性二分查询。 */
    @Test
    fun graphemeBoundaryMap() = benchmarkRule.measureRepeated {
        /** 解码 surrogate pair 并应用引擎自有 Unicode 表的不可变 map。 */
        val map = PixelGraphemeBoundaryMap(UnicodeFixture)
        /** 聚合查询结果，防止 boundary lookup 被编译器消除。 */
        val result = map.graphemeCount + map.previous(UnicodeFixture.length / 2) +
            map.next(UnicodeFixture.length / 2) + if (map.isBoundary(0)) 1 else 0
        BlackHole.consume(result)
    }

    /** 构造不保留跨迭代状态的确定性嵌套布局 fixture。 */
    private fun layoutFixture() = Column(
        children = List(LayoutRowCount) { rowIndex ->
            Row(
                children = List(LayoutColumnCount) { columnIndex ->
                    Container(
                        width = LayoutCellWidth,
                        height = LayoutCellHeight,
                        borderColor = PixelColor.White,
                        child = Text("${rowIndex % 10}${columnIndex % 10}"),
                    )
                },
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )
        },
        spacing = 1,
        crossAxisAlignment = CrossAxisAlignment.START,
    )

    /** 构造覆盖 grapheme、fallback、换行和 Bidi run 的混合脚本 spans。 */
    private fun paragraphFixture() = RichText(
        spans = listOf(
            PixelTextSpan("Pixel SDK Café 👨‍👩‍👧‍👦 "),
            PixelTextSpan("ABC אבג 123 العربية हिन्दी\r\n"),
            PixelTextSpan("repeat repeat repeat repeat repeat"),
        ),
        softWrap = true,
        maxLines = 8,
    )

    /** 所有基准方法共享的稳定尺寸、负载和源 payload。 */
    private companion object {
        /** 布局和段落 fixture 使用的逻辑渲染宽度。 */
        const val LogicalWidth: Int = 160

        /** 布局和段落 fixture 使用的逻辑渲染高度。 */
        const val LogicalHeight: Int = 120

        /** retained layout fixture 的嵌套行数。 */
        const val LayoutRowCount: Int = 12

        /** 每个 retained layout 行的单元数量。 */
        const val LayoutColumnCount: Int = 8

        /** 单个布局单元的固定逻辑宽度。 */
        const val LayoutCellWidth: Int = 16

        /** 单个布局单元的固定逻辑高度。 */
        const val LayoutCellHeight: Int = 7

        /** 每个 buffer 操作 fixture 的宽度。 */
        const val BufferWidth: Int = 96

        /** 每个 buffer 操作 fixture 的高度。 */
        const val BufferHeight: Int = 64

        /** 重复构造稳定 boundary 负载的 Unicode 密集源文本。 */
        val UnicodeFixture: String = buildString {
            repeat(32) {
                append("Café 👨‍👩‍👧‍👦 🇨🇳 ABC אבג 123\r\n")
            }
        }

        /** 覆盖当前全部资源定义族的合法 catalog。 */
        const val ResourceCatalogJson: String = """
            {
              "version": 2,
              "metadata": {"owner":"benchmark"},
              "bitmaps": [
                {"id":"atlas","path":"atlas.pxb"},
                {"id":"icons","path":"icons.pxb"}
              ],
              "spriteSheets": [
                {"id":"hero","path":"hero.json","bitmap":"atlas"}
              ],
              "colors": [
                {"id":"accent","value":"#FF40A0FF"}
              ],
              "fonts": [
                {"id":"ui","manifest":"font.json","binary":"font.pxg"}
              ]
            }
        """
    }
}
