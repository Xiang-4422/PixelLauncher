package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Coverage for [PixelFontCatalog] — fixed pixel-size choices and the documented
 * UI default. JVM-safe; no Android dependencies.
 */
class PixelFontCatalogTest {

    @Test
    fun sizeLabel_mapsPixelSizes() {
        assertEquals("8PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_8))
        assertEquals("10PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_10))
        assertEquals("12PX", PixelFontCatalog.sizeLabel(PixelFontSize.PX_12))
    }

    @Test
    fun options_exposeAllEnumEntries() {
        assertEquals(PixelFontSize.entries.toList(), PixelFontCatalog.fontSizeOptions())
    }

    @Test
    fun default_is10px() {
        assertEquals(PixelFontSize.PX_10, PixelFontCatalog.defaultUiFontSize)
    }

    @Test
    fun fontSizes_haveMatchingProportionalGlyphPacks() {
        val assetsRoot = resolveModuleRoot().resolve("src/main/assets/glyphpacks")

        PixelFontSize.entries.forEach { size ->
            val catalogMetrics = PixelFontCatalog.metrics(size)
            val metrics = glyphPackLanguages.map { language ->
                val directory = assetsRoot.resolve("fusion_pixel_${size.px}px_proportional_$language")
                val manifest = directory.resolve("manifest.json")
                val glyphs = directory.resolve("glyphs.bin")

                assertTrue("Missing glyph pack directory: $directory", directory.isDirectory)
                assertTrue("Missing glyph pack manifest: $manifest", manifest.isFile)
                assertTrue("Missing glyph data: $glyphs", glyphs.isFile)

                GlyphPackMetrics(
                    cellHeight = manifest.readManifestInt("cellHeight"),
                    baseline = manifest.readManifestInt("baseline"),
                    defaultAdvance = manifest.readManifestInt("defaultAdvance"),
                )
            }

            metrics.forEach { metric ->
                assertEquals(size.px, metric.cellHeight)
                assertTrue("Glyph advance must be positive for ${size.px}px", metric.defaultAdvance > 0)
            }
            assertEquals(catalogMetrics.cellHeight, metrics.first().cellHeight)
            assertEquals(catalogMetrics.baseline, metrics.first().baseline)
            assertEquals(catalogMetrics.narrowAdvanceWidth, metrics.first().defaultAdvance)
            assertEquals(catalogMetrics.wideAdvanceWidth, metrics.last().defaultAdvance)
            assertEquals(metrics.first().cellHeight, metrics.last().cellHeight)
            assertEquals(metrics.first().baseline, metrics.last().baseline)
        }
    }

    @Test
    fun metricsLabel_formatsCellBaselineAndAdvance() {
        assertEquals("C8 B7 A4/8", PixelFontCatalog.metricsLabel(PixelFontSize.PX_8))
        assertEquals("C10 B9 A6/10", PixelFontCatalog.metricsLabel(PixelFontSize.PX_10))
        assertEquals("C12 B11 A8/12", PixelFontCatalog.metricsLabel(PixelFontSize.PX_12))
    }

    @Test
    fun settingsAndStateDoNotExposeUserFontChoices() {
        val moduleRoot = resolveModuleRoot()
        val offenders = filesThatMustNotExposeFontChoices.flatMap { relativePath ->
            val file = moduleRoot.resolve(relativePath)
            file.readLines().flatMapIndexed { index, line ->
                forbiddenUserFontChoicePatterns.mapNotNull { pattern ->
                    if (pattern.regex.containsMatchIn(line)) {
                        "${file.relativeTo(moduleRoot).invariantSeparatorsPath}:${index + 1}: " +
                            "${pattern.description}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
        }

        assertTrue(
            "Font size/style must stay out of user settings and state:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    private fun File.readManifestInt(fieldName: String): Int {
        val match = Regex(""""$fieldName"\s*:\s*(\d+)""").find(readText())
        return requireNotNull(match?.groupValues?.getOrNull(1)?.toIntOrNull()) {
            "Missing numeric manifest field $fieldName in $this"
        }
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "app") cwd else cwd.resolve("app")
    }

    private data class GlyphPackMetrics(
        val cellHeight: Int,
        val baseline: Int,
        val defaultAdvance: Int,
    )

    private data class ForbiddenPattern(
        val regex: Regex,
        val description: String,
    )

    private companion object {
        val glyphPackLanguages = listOf("latin", "zh_hans")

        val filesThatMustNotExposeFontChoices = listOf(
            "src/main/kotlin/com/purride/pixellauncherv2/data/FontSettingsRepository.kt",
            "src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt",
            "src/main/kotlin/com/purride/pixellauncherv2/launcher/SettingsMenuModel.kt",
            "src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt",
            "src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt",
        )

        val forbiddenUserFontChoicePatterns = listOf(
            ForbiddenPattern(Regex("""\bKEY_FONT\b"""), "do not persist font settings"),
            ForbiddenPattern(Regex("""\bFONT_SIZE\b"""), "do not expose user font size"),
            ForbiddenPattern(Regex("""\bFONT_STYLE\b"""), "do not expose user font style"),
            ForbiddenPattern(Regex("""\bfont(size|style|scale)\b""", RegexOption.IGNORE_CASE), "do not expose user font size/style"),
            ForbiddenPattern(Regex("""\bfont_(size|style|scale)\b""", RegexOption.IGNORE_CASE), "do not expose user font size/style keys"),
            ForbiddenPattern(Regex("""\bPixelFontSize\b"""), "settings/state must not store font size enum"),
        )
    }
}
