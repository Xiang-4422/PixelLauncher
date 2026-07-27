package com.purride.pixelui.regression

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 公开 API 的最低覆盖门禁。
 *
 * 新增公开 class / object / typealias / widget 函数时，至少要在 docs、tests 或 Launcher 中出现一次。
 *
 * widget 工厂函数与其它公开类型都必须被单模块文档或测试明确提及。
 */
class PublicApiCoverageTest {

    @Test
    fun publicApiAppearsInDocsTestsOrLauncher() {
        val moduleRoot = resolveModuleRoot()
        /** 包含 Launcher 真实 SDK 调用点的仓库根目录。 */
        val repoRoot = requireNotNull(moduleRoot.parentFile)

        val (widgetFunctions, otherApis) = collectPublicApiNames(moduleRoot)

        val docsAndTestsText = buildString {
            appendDirectory(moduleRoot.resolve("docs"))
            appendDirectory(moduleRoot.resolve("src/test/kotlin"))
            appendDirectory(repoRoot.resolve("app/src/main"))
        }

        val missingFunctions = widgetFunctions.filterNot { name ->
            Regex("\\b$name\\b").containsMatchIn(docsAndTestsText)
        }

        val missingOthers = otherApis.filterNot { name ->
            Regex("\\b$name\\b").containsMatchIn(docsAndTestsText)
        }

        val missing = missingFunctions + missingOthers

        assertTrue(
            "Public APIs missing docs/test/Launcher coverage: $missing",
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
        /** 排除只为冻结二进制桥接而保留的内部 public JVM 声明。 */
        val stablePublicPrefix = "(?<!@PixelArtifactInternalApi\\n)^public "
        val widgetFunctionPattern =
            Regex("${stablePublicPrefix}fun ([A-Z][A-Za-z0-9_]*)\\(", RegexOption.MULTILINE)
        val otherPatterns = listOf(
            Regex("${stablePublicPrefix}(?:data |sealed )?class ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("${stablePublicPrefix}enum class ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("${stablePublicPrefix}object ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
            Regex("${stablePublicPrefix}typealias ([A-Za-z][A-Za-z0-9_]*)", RegexOption.MULTILINE),
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
