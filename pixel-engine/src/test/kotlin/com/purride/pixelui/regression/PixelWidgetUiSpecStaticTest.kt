package com.purride.pixelui.regression

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
    fun highRiskWidgetPaddingConstantsRemainAtLeastTwoPixels() {
        val moduleRoot = resolveModuleRoot()
        val offenders = minPaddingRules.mapNotNull { rule ->
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

        assertTrue(
            "pixel-engine widget padding constants must stay >= 2px:\n${offenders.joinToString("\n")}",
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
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelComponents.kt",
                constantName = "TEXT_CONTAINER_PADDING_PX",
                regex = Regex("""TEXT_CONTAINER_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingRule(
                path = "src/main/kotlin/com/purride/pixelui/widgets/PixelDebugOverlay.kt",
                constantName = "DEBUG_OVERLAY_PADDING_PX",
                regex = Regex("""DEBUG_OVERLAY_PADDING_PX\s*=\s*(\d+)"""),
            ),
            MinPaddingRule(
                path = "src/main/kotlin/com/purride/pixelui/internal/widgets/content/InputWidgets.kt",
                constantName = "OUTLINED_BUTTON_PADDING_PX",
                regex = Regex("""OUTLINED_BUTTON_PADDING_PX(?::\s*Int)?\s*=\s*(\d+)"""),
            ),
        )
    }
}
