package com.purride.pixellauncherv2.ui.text

import com.purride.pixelcore.PixelGlyphPackParser
import com.purride.pixellauncherv2.launcher.LauncherFontFamily
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证设置和组件使用的每个字体组合都具备可加载且不跨家族回退的资源。 */
class LauncherFontAssetsTest {

    /** 所有可渲染组合都必须能由生产解析器读取，并且目录只属于所选家族。 */
    @Test
    fun renderableCombinations_haveStrictSingleFamilyAssets() {
        /** 应用模块内实际参与 APK 打包的字形包根目录。 */
        val glyphPackRoot = resolveModuleRoot().resolve("src/main/assets/glyphpacks")

        PixelFontCatalog.renderableSelections().forEach { selection ->
            /** 当前资源组合所属的字体家族。 */
            val family = selection.family
            /** 所选家族允许查询的资源目录前缀。 */
            val expectedPrefix = when (family) {
                LauncherFontFamily.FUSION -> "fusion_pixel_"
                LauncherFontFamily.ARK -> "ark_pixel_"
                else -> "${family.assetFamilyId}_"
            }
            PixelFontCatalog.assetDirectories(selection).forEach { assetDirectory ->
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
                val metrics = PixelFontCatalog.metrics(selection)
                assertEquals(metrics.cellHeight, manifest.cellHeight)
                assertEquals(metrics.baseline, manifest.baseline)
                /** Fusion 的中文分包使用宽字符默认前进宽度；其余分包使用窄字符默认值。 */
                val expectedDefaultAdvance = if (
                    family == LauncherFontFamily.FUSION && assetDirectory.endsWith("_zh_hans")
                ) {
                    metrics.wideAdvanceWidth
                } else {
                    metrics.narrowAdvanceWidth
                }
                assertEquals(expectedDefaultAdvance, manifest.defaultAdvance)
                binaryFile.inputStream().use { input ->
                    PixelGlyphPackParser.parseBinary(manifest = manifest, inputStream = input)
                }
            }
        }
    }

    /** 兼容从仓库根目录或 app 模块目录启动的 JVM 测试。 */
    private fun resolveModuleRoot(): File {
        /** 当前测试进程的规范工作目录。 */
        val currentDirectory = File(".").canonicalFile
        return if (currentDirectory.name == "app") currentDirectory else currentDirectory.resolve("app")
    }
}
