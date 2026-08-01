package com.purride.pixeldesign.font

import com.purride.pixelcore.PixelGlyphPackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证设置和组件使用的每个字体组合都具备可加载且不跨家族回退的资源。 */
class ProductFontAssetsTest {

    /** 罕见高字形和负 bearing 必须保留原始 placement，证明资源没有被统一行框裁切。 */
    @Test
    fun strictOutlierGlyphs_preserveSourcePlacement() {
        val fusionVertical = glyphRecord("fusion_pixel_10px_proportional_zh_hans", 0x3031)
        assertEquals(-3, fusionVertical.bitmapOffsetY)
        assertEquals(20, fusionVertical.height)

        val arkVertical = glyphRecord("ark_pixel_10px_proportional_zh_cn", 0x3032)
        assertEquals(-3, arkVertical.bitmapOffsetY)
        assertEquals(20, arkVertical.height)

        assertEquals(-2, glyphRecord("cubic_11_12px_proportional", 'j'.code).bitmapOffsetX)
        assertEquals(-1, glyphRecord("boutique_9_10px_proportional", 0x0110).bitmapOffsetX)

        val boutiqueReviewed = glyphRecord("boutique_9_10px_proportional", 0x8646)
        assertEquals(9, boutiqueReviewed.width)
        assertEquals(9, boutiqueReviewed.height)

        val pix32Overflow = glyphRecord("pix32_12px_monospaced", 0x247F)
        assertEquals(-7, pix32Overflow.bitmapOffsetX)
        assertEquals(18, pix32Overflow.height)
    }

    /** 所有可渲染组合都必须能由生产解析器读取，并且目录只属于所选家族。 */
    @Test
    fun renderableCombinations_haveStrictSingleFamilyAssets() {
        /** 共享设计模块内实际参与所有 APK 打包的字形包根目录。 */
        val glyphPackRoot = resolveModuleRoot().resolve("src/main/assets/glyphpacks")

        ProductFontCatalog.renderableSelections().forEach { selection ->
            /** 当前资源组合所属的字体家族。 */
            val family = selection.family
            /** 所选家族允许查询的资源目录前缀。 */
            val expectedPrefix = when (family) {
                ProductFontFamily.FUSION -> "fusion_pixel_"
                ProductFontFamily.ARK -> "ark_pixel_"
                else -> "${family.assetFamilyId}_"
            }
            ProductFontCatalog.assetDirectories(selection).forEach { assetDirectory ->
                assertTrue(
                    "cross-family fallback is forbidden: $assetDirectory",
                    assetDirectory.substringAfterLast('/').startsWith(expectedPrefix),
                )
                /** 与生产加载规则完全一致的字形包目录。 */
                val directory = glyphPackRoot.resolve(assetDirectory.substringAfter("glyphpacks/"))
                assertTrue("missing glyph pack directory: $directory", directory.isDirectory)
                /** 当前字体包的清单文件。 */
                val manifestFile = directory.resolve("manifest.json")
                /** 当前字体包的二进制字形文件。 */
                val binaryFile = directory.resolve("glyphs.bin")
                assertTrue("missing manifest: $manifestFile", manifestFile.isFile)
                assertTrue("missing glyph binary: $binaryFile", binaryFile.isFile)

                /** 通过生产解析器校验的字形包元数据。 */
                val manifest = PixelGlyphPackParser.parseManifest(manifestFile.readText())
                /** 当前选择声明的真实排版度量。 */
                val metrics = ProductFontCatalog.metrics(selection)
                assertEquals(metrics.cellHeight, manifest.cellHeight)
                assertEquals(metrics.baseline, manifest.baseline)
                val packDescriptor = ProductFontCatalog.requireFace(selection).packs.single { pack ->
                    pack.assetDirectory == assetDirectory
                }
                assertEquals(packDescriptor.defaultAdvance, manifest.defaultAdvance)
                binaryFile.inputStream().use { input ->
                    PixelGlyphPackParser.parseBinary(manifest = manifest, inputStream = input)
                }
            }
        }
    }

    /** 兼容从仓库根目录或 pixel-design 模块目录启动的 JVM 测试。 */
    private fun resolveModuleRoot(): File {
        /** 当前测试进程的规范工作目录。 */
        val currentDirectory = File(".").canonicalFile
        return if (currentDirectory.name == "pixel-design") {
            currentDirectory
        } else {
            currentDirectory.resolve("pixel-design")
        }
    }

    /** 通过生产解析器读取指定 pack 的完整 V2 字形记录。 */
    private fun glyphRecord(packId: String, codePoint: Int): com.purride.pixelcore.PackedGlyphRecord {
        val directory = resolveModuleRoot().resolve("src/main/assets/glyphpacks/$packId")
        val manifest = PixelGlyphPackParser.parseManifest(directory.resolve("manifest.json").readText())
        val pack = directory.resolve("glyphs.bin").inputStream().use { input ->
            PixelGlyphPackParser.parseBinary(manifest, input)
        }
        return requireNotNull(pack.glyphs[codePoint]) { "missing U+${codePoint.toString(16)} in $packId" }
    }
}
