package com.purride.pixelui.regression

import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelSpacingTokens
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards high-risk SDK widgets against regressions that clip pixel text or
 * collapse bordered text containers back to 1px padding.
 */
class PixelWidgetUiSpecStaticTest {

    @Test
    fun highRiskWidgetSourcesAvoidKnownTextClippingPatterns() {
        val moduleRoot = resolveModuleRoot()
        val offenders = fileRules.flatMap { rule ->
            val file = moduleRoot.resolve(rule.path)
            if (!file.exists()) {
                listOf("${rule.path}: missing file")
            } else {
                file.readLines().flatMapIndexed { index, line ->
                    rule.patterns.mapNotNull { pattern ->
                        if (pattern.regex.containsMatchIn(line)) {
                            "${file.relativeTo(moduleRoot).invariantSeparatorsPath}:${index + 1}: " +
                                "${pattern.description}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
            }
        }

        assertTrue(
            "pixel-engine widget UI spec violations:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun highRiskWidgetPaddingTokensRemainAtLeastTwoPixels() {
        val moduleRoot = resolveModuleRoot()
        /** 仍以源码常量表达的非主题化内边距，由该静态门禁继续保护。 */
        val constantOffenders = minPaddingRules.mapNotNull { rule ->
            val file = moduleRoot.resolve(rule.path)
            if (!file.exists()) {
                "${rule.path}: missing file"
            } else {
                val source = file.readText()
                val match = rule.regex.find(source)
                val value = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                when {
                    value == null -> "${rule.path}: missing ${rule.constantName}"
                    value < 2 -> "${rule.path}: ${rule.constantName} must be >= 2, found $value"
                    else -> null
                }
            }
        }
        /** Canonical spacing scale used to resolve encoded component padding. */
        val spacing = PixelSpacingTokens.Default
        /** High-risk bordered text surfaces now governed by component tokens. */
        val tokenPadding = linkedMapOf(
            "button" to PixelComponentTokens.Default.button.resolvePadding(spacing),
            "textField" to PixelComponentTokens.Default.textField.resolvePadding(spacing),
            "tabs" to PixelComponentTokens.Default.tabs.resolvePadding(spacing),
            "segmented" to PixelComponentTokens.Default.segmented.resolvePadding(spacing),
        )
        /** Token fields whose horizontal or vertical text inset regressed below two pixels. */
        val tokenOffenders = tokenPadding.mapNotNull { (name, padding) ->
            if (minOf(padding.left, padding.top, padding.right, padding.bottom) < 2) {
                "$name token padding must be >= 2px, found $padding"
            } else {
                null
            }
        }
        /** Unified static and tokenized padding violations. */
        val offenders = constantOffenders + tokenOffenders

        assertTrue(
            "pixel-engine widget text padding must stay >= 2px:\n${offenders.joinToString("\n")}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun hostGapRendererCachesDeadPixelGrid() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt")
            .readText()

        val offenders = listOfNotNull(
            if (!source.contains("private var gapBackgroundBitmap: Bitmap? = null")) {
                "gap renderer must cache the dead-pixel grid background"
            } else {
                null
            },
            if (!source.contains("drawGapBackground(canvas, buffer, geometry, shape)")) {
                "gap renderer must draw cached dead-pixel background before lit pixels"
            } else {
                null
            },
            if (!source.contains("if (pixel.alpha <= 0) continue")) {
                "per-frame gap loop must skip unlit pixels after drawing the cached background"
            } else {
                null
            },
            if (!source.contains("recycleGapBackgroundBitmap()")) {
                "gap renderer must release cached bitmap when the host is detached or gap is disabled"
            } else {
                null
            },
        )

        assertTrue(
            "PixelHostView gap path must not redraw the full dead-pixel grid every frame:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun hostGapRendererLeavesViewportEdgesUncovered() {
        val moduleRoot = resolveModuleRoot()
        val source = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt")
            .readText()

        val offenders = listOfNotNull(
            if (!source.contains("for (x in 1 until buffer.width)")) {
                "vertical bezel overlay must start at the first internal grid line"
            } else {
                null
            },
            if (!source.contains("for (y in 1 until buffer.height)")) {
                "horizontal bezel overlay must start at the first internal grid line"
            } else {
                null
            },
            if (source.contains("for (x in 0..buffer.width)") || source.contains("for (y in 0..buffer.height)")) {
                "bezel overlay must not repaint the viewport outer edge"
            } else {
                null
            },
            if (!source.contains("private fun dotLeft(cell: Float, inset: Float, x: Int): Float =") ||
                !source.contains("x * cell + if (x == 0) 0f else inset")
            ) {
                "left edge dots must not keep an outer inset"
            } else {
                null
            },
            if (!source.contains("private fun dotTop(cell: Float, inset: Float, y: Int): Float =") ||
                !source.contains("y * cell + if (y == 0) 0f else inset")
            ) {
                "top edge dots must not keep an outer inset"
            } else {
                null
            },
            if (!source.contains("private fun dotRight(cell: Float, inset: Float, width: Int, x: Int): Float =") ||
                !source.contains("(x + 1) * cell - if (x == width - 1) 0f else inset")
            ) {
                "right edge dots must not keep an outer inset"
            } else {
                null
            },
            if (!source.contains("private fun dotBottom(cell: Float, inset: Float, height: Int, y: Int): Float =") ||
                !source.contains("(y + 1) * cell - if (y == height - 1) 0f else inset")
            ) {
                "bottom edge dots must not keep an outer inset"
            } else {
                null
            },
        )

        assertTrue(
            "PixelHostView gap overlay must not cover full-bleed edge controls:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "pixel-engine") cwd else cwd.resolve("pixel-engine")
    }

    private data class FileRule(
        val path: String,
        val patterns: List<ForbiddenPattern>,
    )

    private data class ForbiddenPattern(
        val regex: Regex,
        val description: String,
    )

    private data class MinPaddingRule(
        val path: String,
        val constantName: String,
        val regex: Regex,
    )

    private companion object {
        private val textContainerPatterns = listOf(
            ForbiddenPattern(
                regex = Regex("""\bvertical\s*=\s*1\b"""),
                description = "text containers need at least 2px vertical padding",
            ),
            ForbiddenPattern(
                regex = Regex("""EdgeInsets\.all\(1\)"""),
                description = "bordered text containers need at least 2px padding",
            ),
            ForbiddenPattern(
                regex = Regex("""PixelTextOverflow\.CLIP"""),
                description = "compressible component text should use ellipsis",
            ),
            ForbiddenPattern(
                regex = Regex("""TextOverflow\.CLIP"""),
                description = "compressible component text should use ellipsis",
            ),
        )

        private val fileRules = listOf(
            FileRule(
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelComponents.kt",
                patterns = textContainerPatterns,
            ),
            FileRule(
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelDebugOverlay.kt",
                patterns = listOf(
                    ForbiddenPattern(
                        regex = Regex("""\ball\s*=\s*1\b"""),
                        description = "debug overlay text padding must be at least 2px",
                    ),
                    ForbiddenPattern(
                        regex = Regex("""Text\(line,\s*style\s*=\s*style\)"""),
                        description = "debug overlay lines must use single-line ellipsis helper",
                    ),
                    ForbiddenPattern(
                        regex = Regex("""TextOverflow\.CLIP"""),
                        description = "debug overlay text must not clip directly",
                    ),
                ),
            ),
            FileRule(
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelInspectorPanel.kt",
                patterns = listOf(
                    ForbiddenPattern(
                        regex = Regex("""TextOverflow\.CLIP"""),
                        description = "inspector body lines must use ellipsis",
                    ),
                ),
            ),
            FileRule(
                path = "src/main/kotlin/com/purride/pixelui/internal/widgets/content/InputWidgets.kt",
                patterns = listOf(
                    ForbiddenPattern(
                        regex = Regex("""EdgeInsets\.all\(1\)"""),
                        description = "outlined button padding must be at least 2px",
                    ),
                ),
            ),
        )

        private val minPaddingRules = listOf(
            MinPaddingRule(
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelDebugOverlay.kt",
                constantName = "DEBUG_OVERLAY_PADDING_PX",
                regex = Regex("""DEBUG_OVERLAY_PADDING_PX\s*=\s*(\d+)"""),
            ),
        )
    }
}
