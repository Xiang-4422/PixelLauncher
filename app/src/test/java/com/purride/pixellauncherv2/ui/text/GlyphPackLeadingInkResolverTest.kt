package com.purride.pixellauncherv2.ui.text

import com.purride.pixelcore.PixelGlyphPack
import com.purride.pixelcore.PixelGlyphPackParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** 验证 drawer 视觉左对齐使用真实 Fusion Pixel 字形墨迹边界。 */
class GlyphPackLeadingInkResolverTest {

    /** 当前 10px 字形中拉丁大写字母的 side bearing 会被精确识别。 */
    @Test
    fun resolve_latinUppercase_returnsActualLeadingInkInset() {
        /** 使用与 Launcher Host 相同优先级构建的解析器。 */
        val resolver = resolverFor10Px()

        assertEquals(2, resolver.resolve("A"))
        assertEquals(1, resolver.resolve("M"))
        assertEquals(3, resolver.resolve("I"))
    }

    /** 中文字形墨迹已经从单元格首列开始时不应产生额外位移。 */
    @Test
    fun resolve_chineseGlyph_returnsZeroForFlushInk() {
        /** 使用与 Launcher Host 相同优先级构建的解析器。 */
        val resolver = resolverFor10Px()

        assertEquals(0, resolver.resolve("微信"))
    }

    /** 应用名主动包含的前导空格属于内容，不应被视觉校正吞掉。 */
    @Test
    fun resolve_leadingWhitespace_preservesContentInset() {
        /** 使用与 Launcher Host 相同优先级构建的解析器。 */
        val resolver = resolverFor10Px()

        assertEquals(0, resolver.resolve(" A"))
        assertEquals(0, resolver.resolve(""))
    }

    /** 加载 10px proportional 拉丁与简体中文字形包。 */
    private fun resolverFor10Px(): GlyphPackLeadingInkResolver {
        /** 应用模块的 assets/glyphpacks 根目录。 */
        val glyphPackRoot = resolveModuleRoot().resolve("src/main/assets/glyphpacks")
        /** 与生产环境 BitmapGlyphSource 相同顺序的字形包列表。 */
        val orderedPacks = listOf(
            loadPack(glyphPackRoot.resolve("fusion_pixel_10px_proportional_latin")),
            loadPack(glyphPackRoot.resolve("fusion_pixel_10px_proportional_zh_hans")),
        )
        return GlyphPackLeadingInkResolver(orderedPacks)
    }

    /** 从一个 glyph pack 目录解析 manifest 与二进制字形。 */
    private fun loadPack(directory: File): PixelGlyphPack {
        /** 字形包元数据。 */
        val manifest = PixelGlyphPackParser.parseManifest(directory.resolve("manifest.json").readText())
        return directory.resolve("glyphs.bin").inputStream().use { input ->
            PixelGlyphPackParser.parseBinary(manifest = manifest, inputStream = input)
        }
    }

    /** 兼容从仓库根目录或 app 模块目录启动的 JVM 测试。 */
    private fun resolveModuleRoot(): File {
        /** 当前测试进程的规范工作目录。 */
        val currentDirectory = File(".").canonicalFile
        return if (currentDirectory.name == "app") currentDirectory else currentDirectory.resolve("app")
    }
}
