package com.purride.pixellauncherv2.ui.text

import com.purride.pixelcore.PixelGlyphPackParser
import com.purride.pixellauncherv2.launcher.PixelFontCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 验证设置页公开的每个字体选项都具备可加载的默认字号资源。 */
class LauncherFontAssetsTest {

    /** 所有可选字体都必须同时提供拉丁和简体中文字形包。 */
    @Test
    fun selectableFamilies_haveLoadableDefaultAssets() {
        /** 应用模块内实际参与 APK 打包的字形包根目录。 */
        val glyphPackRoot = resolveModuleRoot().resolve("src/main/assets/glyphpacks")
        /** 设置页和 Host 共用的默认像素字号。 */
        val size = PixelFontCatalog.defaultUiFontSize

        PixelFontCatalog.fontFamilyOptions().forEach { family ->
            SUPPORTED_LANGUAGE_SUFFIXES.forEach { languageSuffix ->
                /** 与生产加载规则完全一致的字形包目录。 */
                val directory = glyphPackRoot.resolve(
                    "fusion_pixel_${size.px}px_${family.assetStyleName}_$languageSuffix",
                )
                assertTrue("missing glyph pack directory: $directory", directory.isDirectory)
                /** 当前字体包的清单文件。 */
                val manifestFile = directory.resolve("manifest.json")
                /** 当前字体包的二进制字形文件。 */
                val binaryFile = directory.resolve("glyphs.bin")
                assertTrue("missing manifest: $manifestFile", manifestFile.isFile)
                assertTrue("missing glyph binary: $binaryFile", binaryFile.isFile)

                /** 通过生产解析器校验的字形包元数据。 */
                val manifest = PixelGlyphPackParser.parseManifest(manifestFile.readText())
                assertEquals(size.px, manifest.cellHeight)
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

    private companion object {
        /** Launcher 字体组合固定要求覆盖的语言包后缀。 */
        val SUPPORTED_LANGUAGE_SUFFIXES: List<String> = listOf("latin", "zh_hans")
    }
}
