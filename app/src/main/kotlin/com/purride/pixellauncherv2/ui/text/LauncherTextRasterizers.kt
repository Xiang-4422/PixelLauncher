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
import com.purride.pixellauncherv2.launcher.PixelFontSize

/**
 * Launcher 字体栅格器仓库，同时提供基于真实字形墨迹边界的视觉对齐信息。
 */
class LauncherTextRasterizers(
    /** 用于从 assets 加载 Fusion Pixel 字形包的 Android 上下文。 */
    context: Context,
) {

    /** 复用 pixel-engine 资源缓存的字形包加载器。 */
    private val glyphPackLoader = PixelGlyphPackAssetLoader(context)
    /** 按固定字号缓存栅格器及其视觉边界解析器。 */
    private val cache = mutableMapOf<PixelFontSize, RasterizerEntry>()

    /** 返回指定像素字号的共享文本栅格器。 */
    fun getRasterizer(size: PixelFontSize): PixelTextRasterizer {
        return entryFor(size).rasterizer
    }

    /**
     * 返回文本首字形在逻辑原点之后的空白像素数，用于左对齐时校正视觉起点。
     *
     * 前导空格属于应用名内容，不会被当作字体边距消除。
     */
    fun leadingInkInset(text: String, size: PixelFontSize): Int {
        return entryFor(size).inkInsetResolver.resolveLeading(text)
    }

    /** 返回文本末字形在逻辑前进宽度末端之前的空白像素数，用于右对齐视觉校正。 */
    fun trailingInkInset(text: String, size: PixelFontSize): Int {
        return entryFor(size).inkInsetResolver.resolveTrailing(text)
    }

    /** 返回与实际 Launcher 绘制一致的文字逻辑宽度。 */
    fun measureTextWidth(text: String, size: PixelFontSize): Int {
        return entryFor(size).rasterizer.measureText(text)
    }

    /** 获取或创建同时服务测量、绘制和视觉边界查询的字号条目。 */
    private fun entryFor(size: PixelFontSize): RasterizerEntry {
        return cache.getOrPut(size) {
            fusionRasterizer(
                latinAssetDirectory = "glyphpacks/fusion_pixel_${size.px}px_${DEFAULT_STYLE_NAME}_latin",
                zhHansAssetDirectory = "glyphpacks/fusion_pixel_${size.px}px_${DEFAULT_STYLE_NAME}_zh_hans",
            )
        }
    }

    /** 加载同字号的中英文字形包并构建一个共享栅格器条目。 */
    private fun fusionRasterizer(
        /** 拉丁字形包的 assets 目录。 */
        latinAssetDirectory: String,
        /** 简体中文字形包的 assets 目录。 */
        zhHansAssetDirectory: String,
    ): RasterizerEntry {
        /** 与当前字号匹配的拉丁字形包。 */
        val latinPack = glyphPackLoader.load(latinAssetDirectory)
        /** 与当前字号匹配的简体中文字形包。 */
        val zhHansPack = glyphPackLoader.load(zhHansAssetDirectory)

        require(latinPack.manifest.cellHeight == zhHansPack.manifest.cellHeight) {
            "Fusion Pixel latin and zh_hans packs must share cellHeight"
        }

        /** 与实际字形查找顺序一致的有序字形包。 */
        val orderedPacks = listOf(latinPack, zhHansPack)
        return RasterizerEntry(
            rasterizer = PixelStyledTextRasterizer(
                engine = PixelFontEngine(
                    glyphProvider = CompositeGlyphProvider(
                        listOf(BitmapGlyphSource(orderedPacks)),
                    ),
                ),
                style = styleFor(latinPack, zhHansPack),
                lineSpacing = 1,
            ),
            inkInsetResolver = GlyphPackInkInsetResolver(orderedPacks),
        )
    }

    /** 根据两套同字号字形包构建统一的宽窄字形样式。 */
    private fun styleFor(
        /** 提供窄字形默认前进宽度的拉丁字形包。 */
        latinPack: PixelGlyphPack,
        /** 提供宽字形默认前进宽度的简体中文字形包。 */
        zhHansPack: PixelGlyphPack,
    ): GlyphStyle {
        return GlyphStyle(
            cellHeight = latinPack.manifest.cellHeight,
            narrowAdvanceWidth = latinPack.manifest.defaultAdvance,
            wideAdvanceWidth = zhHansPack.manifest.defaultAdvance,
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

    /** 一个字号对应的栅格器及首末字形墨迹边界解析器。 */
    private data class RasterizerEntry(
        /** 供 Host 实际测量和绘制文本使用的栅格器。 */
        val rasterizer: PixelTextRasterizer,
        /** 供页面边缘文字查询首末字形视觉空白的解析器。 */
        val inkInsetResolver: GlyphPackInkInsetResolver,
    )

    private companion object {
        /** Launcher 固定使用的 Fusion Pixel 字体样式名。 */
        const val DEFAULT_STYLE_NAME = "proportional"
    }
}

/**
 * 按渲染时的字形包优先级解析首末字形的墨迹空白。
 *
 * 解析结果来自打包后的真实像素，而不是按语言或字符宽度猜测，因此能够覆盖 `I`、`M`、
 * 中文及其他 Unicode 字形之间不同的 side bearing。
 */
internal class GlyphPackInkInsetResolver(
    /** 与 [BitmapGlyphSource] 一致的字形包查找顺序。 */
    private val orderedPacks: List<PixelGlyphPack>,
) {
    /** 按完整 Unicode 码点缓存已经扫描出的左侧空白像素数。 */
    private val insetCache = mutableMapOf<Int, Int>()
    /** 按完整 Unicode 码点缓存已经扫描出的右侧空白像素数。 */
    private val trailingInsetCache = mutableMapOf<Int, Int>()

    /** 返回文本首字形的左侧空白；空文本、前导空白和缺失字形均不补偿。 */
    fun resolveLeading(text: String): Int {
        if (text.isEmpty()) return 0
        /** 文本第一个完整 Unicode 标量。 */
        val codePoint = Character.codePointAt(text, 0)
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return 0
        return insetCache.getOrPut(codePoint) { resolveCodePoint(codePoint) }
    }

    /** 返回文本末字形的右侧空白；空文本、尾随空白和缺失字形均不补偿。 */
    fun resolveTrailing(text: String): Int {
        if (text.isEmpty()) return 0
        /** 文本最后一个完整 Unicode 标量。 */
        val codePoint = Character.codePointBefore(text, text.length)
        if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) return 0
        return trailingInsetCache.getOrPut(codePoint) { resolveTrailingCodePoint(codePoint) }
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

    /** 按实际 fallback 顺序找到末字形，并扫描最后一列可见墨迹。 */
    private fun resolveTrailingCodePoint(codePoint: Int): Int {
        orderedPacks.forEach { pack ->
            /** 当前字形包中与完整码点对应的压缩记录。 */
            val record = pack.glyphs[codePoint] ?: return@forEach
            return trailingInkInset(record = record, cellHeight = pack.manifest.cellHeight)
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

    /** 从 MSB-first 压缩位图中计算最后一列墨迹到前进宽度末端的空白。 */
    private fun trailingInkInset(record: PackedGlyphRecord, cellHeight: Int): Int {
        /** 防御性复制后的压缩字形像素。 */
        val packedPixels = record.packedPixels
        for (x in record.width - 1 downTo 0) {
            for (y in 0 until cellHeight) {
                /** 当前二维像素在线性位图中的位置。 */
                val pixelIndex = (y * record.width) + x
                /** 当前像素所在压缩字节的无符号值。 */
                val packedByte = packedPixels[pixelIndex / BITS_PER_BYTE].toInt() and 0xFF
                /** 当前像素在 MSB-first 字节中的位移。 */
                val bitShift = LAST_BIT_INDEX - (pixelIndex % BITS_PER_BYTE)
                if (((packedByte shr bitShift) and 1) != 0) {
                    return (record.advanceWidth - x - 1).coerceAtLeast(0)
                }
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
