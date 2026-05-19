package com.purride.pixelui.regression

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 公开 widget API 的最低覆盖门禁。
 *
 * 新增公开 widget 函数时，至少要在 docs / demo / tests 中出现一次，避免 SDK
 * API 只停留在源码里而没有使用说明或验收入口。
 */
class PublicApiCoverageTest {

    @Test
    fun publicWidgetFactoriesAppearInDocsDemoOrTests() {
        val moduleRoot = resolveModuleRoot()
        val repoRoot = requireNotNull(moduleRoot.parentFile)
        val apiFile = moduleRoot.resolve("src/main/kotlin/com/purride/pixelui/widgets/PixelWidgets.kt")
        val apiNames = Regex("^public fun ([A-Z][A-Za-z0-9_]*)\\(", RegexOption.MULTILINE)
            .findAll(apiFile.readText())
            .map { match -> match.groupValues[1] }
            .toSet()

        val searchableText = buildString {
            appendDirectory(moduleRoot.resolve("docs"))
            appendDirectory(moduleRoot.resolve("src/test/kotlin"))
            appendDirectory(repoRoot.resolve("pixel-demo/src/main/kotlin"))
        }

        val missing = apiNames.filterNot { name ->
            Regex("\\b$name\\b").containsMatchIn(searchableText)
        }
        assertTrue(
            "Public widget APIs missing docs/demo/test coverage: $missing",
            missing.isEmpty(),
        )
    }

    private fun resolveModuleRoot(): File {
        val cwd = File(".").canonicalFile
        return if (cwd.name == "pixel-engine") {
            cwd
        } else {
            cwd.resolve("pixel-engine")
        }
    }

    private fun StringBuilder.appendDirectory(directory: File) {
        if (!directory.exists()) {
            return
        }
        directory.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "md") }
            .forEach { file ->
                append('\n')
                append(file.readText())
            }
    }
}
