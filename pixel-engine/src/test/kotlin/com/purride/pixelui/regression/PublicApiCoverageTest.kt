package com.purride.pixelui.regression

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 公开 API 的最低覆盖门禁。
 *
 * 新增公开 class / object / typealias / widget 函数时，至少要在 docs / tests / demo 中出现一次。
 *
 * widget 工厂函数（`public fun Name(` 命名规范）使用更严格的 demo 规则：
 * 要求 `Name(` 形式的调用出现在非 import 行，仅 import 不计为覆盖。
 * 其它公开类型（class / object / typealias）允许以 `Name.xxx` 或类型注解形式出现，
 * 使用宽松的 `\bName\b` 规则，不限定调用形式。
 */
class PublicApiCoverageTest {

    @Test
    fun publicApiAppearsInDocsDemoOrTests() {
        val moduleRoot = resolveModuleRoot()
        val repoRoot = requireNotNull(moduleRoot.parentFile)

        val (widgetFunctions, otherApis) = collectPublicApiNames(moduleRoot)

        val docsAndTestsText = buildString {
            appendDirectory(moduleRoot.resolve("docs"))
            appendDirectory(moduleRoot.resolve("src/test/kotlin"))
        }

        val demoText = buildString {
            appendDirectory(repoRoot.resolve("pixel-demo/src/main/kotlin"))
        }
        val demoNonImportText = stripImportLines(demoText)

        val combined = docsAndTestsText + demoText

        val missingFunctions = widgetFunctions.filterNot { name ->
            Regex("\\b$name\\b").containsMatchIn(docsAndTestsText) ||
                Regex("\\b$name\\s*\\(").containsMatchIn(demoNonImportText)
        }

        val missingOthers = otherApis.filterNot { name ->
            Regex("\\b$name\\b").containsMatchIn(combined)
        }

        val missing = missingFunctions + missingOthers

        val importOnlyFunctions = if (missing.isEmpty()) emptyList() else {
            missingFunctions.filter { name ->
                Regex("\\b$name\\b").containsMatchIn(demoText) &&
                    !Regex("\\b$name\\s*\\(").containsMatchIn(demoNonImportText)
            }
        }

        val importOnlyMsg = if (importOnlyFunctions.isNotEmpty()) {
            "\nNote: $importOnlyFunctions appear in import statements but have no call site"
        } else ""

        assertTrue(
            "Public APIs missing docs/demo/test coverage: $missing$importOnlyMsg",
            missing.isEmpty(),
        )
    }

    private fun collectPublicApiNames(moduleRoot: File): Pair<Set<String>, Set<String>> {
        val scanDirs = listOf("widgets", "advanced", "controllers", "foundation", "state", "gesture", "host")
        val extraFiles = listOf(
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelui/PixelGeometry.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/graphics/PixelBlendMode.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelui/PixelNavigatorAliases.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/PixelBitmapLoaders.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/PixelResourceManifest.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/PixelResourceCache.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/PixelSpriteSheet.kt"),
            moduleRoot.resolve("src/main/kotlin/com/purride/pixelcore/PixelSpriteSheetLoader.kt"),
        )
        val widgetFunctionPattern = Regex("^public fun ([A-Z][A-Za-z0-9_]*)\\(", RegexOption.MULTILINE)
        val otherPatterns = listOf(
            Regex("^public (?:data |sealed )?class ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("^public enum class ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("^public object ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("^public typealias ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
        )
        val widgetFunctions = mutableSetOf<String>()
        val otherApis = mutableSetOf<String>()
        for (dir in scanDirs) {
            val directory = moduleRoot.resolve("src/main/kotlin/com/purride/pixelui/$dir")
            if (!directory.exists()) continue
            directory.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    widgetFunctionPattern.findAll(text).forEach { match -> widgetFunctions.add(match.groupValues[1]) }
                    for (pattern in otherPatterns) {
                        pattern.findAll(text).forEach { match -> otherApis.add(match.groupValues[1]) }
                    }
                }
        }
        extraFiles
            .filter { it.exists() }
            .forEach { file ->
                val text = file.readText()
                for (pattern in otherPatterns) {
                    pattern.findAll(text).forEach { match -> otherApis.add(match.groupValues[1]) }
                }
            }
        return widgetFunctions to otherApis
    }

    private fun stripImportLines(text: String): String {
        return text.lines().filterNot { line ->
            line.trimStart().startsWith("import ")
        }.joinToString("\n")
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "pixel-engine") cwd else cwd.resolve("pixel-engine")
    }

    private fun StringBuilder.appendDirectory(directory: File) {
        if (!directory.exists()) return
        directory.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "md") }
            .forEach { file ->
                append('\n')
                append(file.readText())
            }
    }
}
