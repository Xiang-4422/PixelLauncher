package com.purride.pixellauncherv2.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 静态分层契约测试：launcher / ui / viewmodel 不得依赖 data 包。
 *
 * 阶段二重构把纯 DTO 迁移到了 [com.purride.pixellauncherv2.model]，
 * data 包应只保留带业务逻辑（Android 依赖、I/O）的 Repository 等实现；
 * 这里通过扫描源码中任意出现的 `com.purride.pixellauncherv2.data.` 引用
 * （不局限于 import 语句，也覆盖全限定名写法），防止未来又悄悄引入反向依赖。
 */
class LayeringContractTest {

    @Test
    fun launcherUiViewModelDoNotDependOnDataPackage() {
        // 收集受限目录下所有源文件中，命中禁用前缀的“文件路径: 出现该引用的行”记录。
        val violations = RESTRICTED_PACKAGE_DIRS.flatMap { relativeDir ->
            kotlinFilesUnder(relativeDir).flatMap { file -> findForbiddenReferences(file) }
        }

        assertTrue(
            "以下文件依赖了 $FORBIDDEN_REFERENCE_PREFIX*（无论是 import 还是全限定名），" +
                "launcher/ui/viewmodel 不得依赖 data 包，请改用 com.purride.pixellauncherv2.model 中的纯数据类型：\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /** 扫描单个文件，返回其中每一处命中禁用前缀的“相对路径: 行内容”记录。 */
    private fun findForbiddenReferences(file: File): List<String> {
        val relativePath = file.relativeTo(moduleRoot())
        return file.readLines()
            .filter { line -> line.contains(FORBIDDEN_REFERENCE_PREFIX) }
            .map { line -> "$relativePath: ${line.trim()}" }
    }

    private fun kotlinFilesUnder(relativeDir: String): List<File> {
        val dir = moduleRoot().resolve(relativeDir)
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun moduleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }

    private companion object {
        /** 禁止在受限目录中出现的引用前缀，同时匹配 import 语句和代码中的全限定名。 */
        const val FORBIDDEN_REFERENCE_PREFIX = "com.purride.pixellauncherv2.data."

        /** 不得反向依赖 data 包的受限生产代码目录（相对于 app 模块根目录）。 */
        val RESTRICTED_PACKAGE_DIRS = listOf(
            "src/main/kotlin/com/purride/pixellauncherv2/launcher",
            "src/main/kotlin/com/purride/pixellauncherv2/ui",
            "src/main/kotlin/com/purride/pixellauncherv2/viewmodel",
        )
    }
}
