package com.purride.pixellauncherv2.ui.text

import android.content.Context
import com.purride.pixelcore.BitmapGlyphSource
import com.purride.pixelcore.CompositeGlyphProvider
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.PackedGlyphRecord
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import com.purride.pixelcore.PixelGlyphPack
import com.purride.pixelcore.PixelGlyphPackAssetLoader
import com.purride.pixelcore.PixelStyledTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import com.purride.pixellauncherv2.launcher.PixelFontMetrics

/**
 * Launcher 字体栅格器仓库，同时提供基于真实字形墨迹边界的视觉对齐信息。
 */
class LauncherTextRasterizers(
    /** 用于从 assets 加载所选字体字形包的 Android 上下文。 */
    context: Context,
) {

    /** 复用 pixel-engine 资源缓存的字形包加载器。 */
    private val glyphPackLoader = PixelGlyphPackAssetLoader(context)
    /** 按完整字体选择缓存栅格器及其视觉边界解析器。 */
    private val cache = mutableMapOf<LauncherFontSelection, RasterizerEntry>()
    /** 按默认选择缓存提供给 UI 组件的字号覆盖入口。 */
    private val typographyCache = mutableMapOf<LauncherFontSelection, LauncherTypography>()

    /** 返回指定字体家族、宽度模式和字号的共享文本栅格器。 */
    fun getRasterizer(selection: LauncherFontSelection): PixelTextRasterizer {
        return entryFor(selection).rasterizer
    }

    /** 返回允许 UI 组件在同一字体内明确选择字号的 typography。 */
    fun typography(selection: LauncherFontSelection): LauncherTypography {
        /** 防止无效选择形成重复 typography 实例。 */
        val normalized = PixelFontCatalog.resolveRenderable(selection)
        return typographyCache.getOrPut(normalized) {
            LauncherTypography(selection = normalized, rasterizerResolver = ::getRasterizer)
        }
    }

    /**
     * 返回文本首字形在逻辑原点之后的空白像素数，用于左对齐时校正视觉起点。
     *
     * 前导空格属于应用名内容，不会被当作字体边距消除。
     */
    fun leadingInkInset(text: String, selection: LauncherFontSelection): Int {
        return entryFor(selection).leadingInkResolver.resolve(text)
    }

    /** 获取或创建同时服务测量、绘制和视觉边界查询的字体条目。 */
    private fun entryFor(selection: LauncherFontSelection): RasterizerEntry {
        /** 防止无效持久化组合进入资源路径。 */
        val normalized = PixelFontCatalog.resolveRenderable(selection)
        return cache.getOrPut(normalized) { createRasterizerEntry(normalized) }
    }

    /** 只加载当前选择所属字体家族的字形包，不追加其他家族回退。 */
    private fun createRasterizerEntry(selection: LauncherFontSelection): RasterizerEntry {
        /** 当前选择声明的同家族字形包。 */
        val orderedPacks = PixelFontCatalog.assetDirectories(selection).map(glyphPackLoader::load)
        require(orderedPacks.isNotEmpty()) { "Font selection must declare at least one glyph pack" }
        /** 当前字号和宽度模式对应的基础排版度量。 */
        val metrics = PixelFontCatalog.metrics(selection)
        require(orderedPacks.all { pack -> pack.manifest.cellHeight == metrics.cellHeight }) {
            "Font packs must match selected ${metrics.cellHeight}px cell height"
        }
        return RasterizerEntry(
            rasterizer = PixelStyledTextRasterizer(
                engine = PixelFontEngine(
                    glyphProvider = CompositeGlyphProvider(
                        listOf(BitmapGlyphSource(orderedPacks)),
                    ),
                ),
                style = styleFor(metrics),
                lineSpacing = 1,
            ),
            leadingInkResolver = GlyphPackLeadingInkResolver(orderedPacks),
        )
    }

    /** 根据当前字体选择构建统一的宽窄字形样式。 */
    private fun styleFor(
        /** 当前字体选择对应的真实像素度量。 */
        metrics: PixelFontMetrics,
    ): GlyphStyle {
        return GlyphStyle(
            cellHeight = metrics.cellHeight,
            narrowAdvanceWidth = metrics.narrowAdvanceWidth,
            wideAdvanceWidth = metrics.wideAdvanceWidth,
            oversampleFactor = 1,
            narrowMinimumSampleRatio = 1f,
            wideMinimumSampleRatio = 1f,
            narrowTextSizeRatio = 1f,
            wideTextSizeRatio = 1f,
            narrowFontWeight = PixelFontWeight.NORMAL,
            wideFontWeight = PixelFontWeight.NORMAL,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
            baseLetterSpacing = 0,
        )
    }

    /** 一个字号对应的栅格器及首字形墨迹边界解析器。 */
    private data class RasterizerEntry(
        /** 供 Host 实际测量和绘制文本使用的栅格器。 */
        val rasterizer: PixelTextRasterizer,
        /** 供 drawer 左对齐时查询首字形视觉空白的解析器。 */
        val leadingInkResolver: GlyphPackLeadingInkResolver,
    )

}

/**
 * 按渲染时的字形包优先级解析首字形左侧墨迹空白。
 *
 * 解析结果来自打包后的真实像素，而不是按语言或字符宽度猜测，因此能够覆盖 `I`、`M`、
 * 中文及其他 Unicode 字形之间不同的 side bearing。
 */
internal class GlyphPackLeadingInkResolver(
    /** 与 [BitmapGlyphSource] 一致的字形包查找顺序。 */
    private val orderedPacks: List<PixelGlyphPack>,
) {
    /** 按完整 Unicode 码点缓存已经扫描出的左侧空白像素数。 */
    private val insetCache = mutableMapOf<Int, Int>()

    /** 返回文本首字形的左侧空白；空文本、前导空白和缺失字形均不补偿。 */
    fun resolve(text: String): Int {
        if (text.isEmpty()) return 0
        /** 文本第一个完整 Unicode 标量。 */
        val codePoint = Character.codePointAt(text, 0)
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return 0
        return insetCache.getOrPut(codePoint) { resolveCodePoint(codePoint) }
    }

    /** 按实际 fallback 顺序找到字形，并扫描第一列可见墨迹。 */
    private fun resolveCodePoint(codePoint: Int): Int {
        orderedPacks.forEach { pack ->
            /** 当前字形包中与完整码点对应的压缩记录。 */
            val record = pack.glyphs[codePoint] ?: return@forEach
            return leadingInkInset(record = record, cellHeight = pack.manifest.cellHeight)
        }
        return 0
    }

    /** 从 MSB-first 压缩位图中计算第一列非透明像素的位置。 */
    private fun leadingInkInset(record: PackedGlyphRecord, cellHeight: Int): Int {
        /** 防御性复制后的压缩字形像素。 */
        val packedPixels = record.packedPixels
        for (x in 0 until record.width) {
            for (y in 0 until cellHeight) {
                /** 当前二维像素在线性位图中的位置。 */
                val pixelIndex = (y * record.width) + x
                /** 当前像素所在压缩字节的无符号值。 */
                val packedByte = packedPixels[pixelIndex / BITS_PER_BYTE].toInt() and 0xFF
                /** 当前像素在 MSB-first 字节中的位移。 */
                val bitShift = LAST_BIT_INDEX - (pixelIndex % BITS_PER_BYTE)
                if (((packedByte shr bitShift) and 1) != 0) return x
            }
        }
        return 0
    }

    private companion object {
        /** 一个字节包含的位数。 */
        const val BITS_PER_BYTE = 8
        /** MSB-first 编码中首位对应的位索引。 */
        const val LAST_BIT_INDEX = BITS_PER_BYTE - 1
    }
}
