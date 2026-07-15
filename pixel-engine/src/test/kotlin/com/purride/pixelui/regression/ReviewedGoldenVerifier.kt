package com.purride.pixelui.regression

import java.io.File

/**
 * 已审阅 golden 与本次候选之间的比较结果。
 *
 * 候选和差异只写入 `build/`，普通测试永远不会修改源码目录中的基线。
 */
internal data class ReviewedGoldenComparison(
    /** 候选是否与已审阅基线逐字节一致。 */
    val matches: Boolean,
    /** 本次实际内容的可审阅候选文件。 */
    val candidateFile: File,
    /** 不一致时生成的 unified-style 差异文件。 */
    val diffFile: File,
    /** 失败时向测试报告展示的定位信息。 */
    val failureMessage: String,
)

/**
 * 只生成候选和差异、绝不自动接受新基线的 golden 验证器。
 */
internal object ReviewedGoldenVerifier {
    /** 差异块前后保留的上下文行数。 */
    private const val DIFF_CONTEXT_LINES: Int = 3

    /**
     * 比较 [actual] 与 [baselineFile]，并把候选及必要的差异写入 [reportStem] 所在目录。
     */
    internal fun compare(
        baselineFile: File,
        actual: String,
        reportStem: File,
    ): ReviewedGoldenComparison {
        /** 候选文件固定留在构建报告目录，不能被测试当作新基线读取。 */
        val candidateFile = File(reportStem.parentFile, "${reportStem.name}.actual.txt")
        /** 差异文件与候选并列，便于 CI 作为单个报告目录上传。 */
        val diffFile = File(reportStem.parentFile, "${reportStem.name}.diff")
        checkNotNull(candidateFile.parentFile).mkdirs()
        candidateFile.writeText(actual)

        /** 已审阅内容；缺失基线也按不匹配处理，不能隐式创建源码文件。 */
        val expected = baselineFile.takeIf(File::exists)?.readText()
        /** 逐字节匹配结果，包含尾部换行差异。 */
        val matches = expected == actual
        if (matches) {
            if (diffFile.exists()) {
                check(diffFile.delete()) { "无法删除过期 golden diff：${diffFile.absolutePath}" }
            }
        } else {
            diffFile.writeText(
                buildReviewableDiff(
                    expected = expected,
                    actual = actual,
                    baselineLabel = baselineFile.path,
                    candidateLabel = candidateFile.path,
                ),
            )
        }

        /** 统一失败消息同时给出基线、候选和差异，避免只看到大段控制台文本。 */
        val failureMessage = if (expected == null) {
            "缺少已审阅 golden ${baselineFile.absolutePath}；候选：${candidateFile.absolutePath}；" +
                "差异：${diffFile.absolutePath}"
        } else {
            "golden 与已审阅基线不一致：${baselineFile.absolutePath}；" +
                "候选：${candidateFile.absolutePath}；差异：${diffFile.absolutePath}"
        }
        return ReviewedGoldenComparison(
            matches = matches,
            candidateFile = candidateFile,
            diffFile = diffFile,
            failureMessage = failureMessage,
        )
    }

    /**
     * 比较并在不一致时抛出断言错误；源码基线始终保持只读。
     */
    internal fun assertMatches(
        baselineFile: File,
        actual: String,
        reportStem: File,
    ) {
        /** 完整比较结果用于生成稳定、可定位的测试失败。 */
        val comparison = compare(
            baselineFile = baselineFile,
            actual = actual,
            reportStem = reportStem,
        )
        if (!comparison.matches) {
            throw AssertionError(comparison.failureMessage)
        }
    }

    /**
     * 生成包含共同上下文、删除行和新增行的紧凑 unified-style 差异。
     */
    private fun buildReviewableDiff(
        expected: String?,
        actual: String,
        baselineLabel: String,
        candidateLabel: String,
    ): String {
        /** 基线缺失时使用空行集合，明确展示整个候选都是新增内容。 */
        val expectedLines = expected?.split('\n') ?: emptyList()
        /** 候选按换行拆分并保留尾部空行，以检测末尾换行变化。 */
        val actualLines = actual.split('\n')
        /** 从头开始完全一致的行数。 */
        var commonPrefix = 0
        while (
            commonPrefix < expectedLines.size &&
            commonPrefix < actualLines.size &&
            expectedLines[commonPrefix] == actualLines[commonPrefix]
        ) {
            commonPrefix += 1
        }

        /** 从尾部开始完全一致、且不与公共前缀重叠的行数。 */
        var commonSuffix = 0
        while (
            commonSuffix < expectedLines.size - commonPrefix &&
            commonSuffix < actualLines.size - commonPrefix &&
            expectedLines[expectedLines.lastIndex - commonSuffix] ==
            actualLines[actualLines.lastIndex - commonSuffix]
        ) {
            commonSuffix += 1
        }

        /** 差异块开始位置，向前保留固定上下文。 */
        val contextStart = (commonPrefix - DIFF_CONTEXT_LINES).coerceAtLeast(0)
        /** 基线差异块结束位置，向后保留固定上下文。 */
        val expectedEnd = (expectedLines.size - commonSuffix + DIFF_CONTEXT_LINES)
            .coerceAtMost(expectedLines.size)
        /** 差异块中作为前置上下文保留的公共行。 */
        val prefixContext = expectedLines.subList(contextStart, commonPrefix)
        /** 基线中需要删除或替换的行。 */
        val removed = expectedLines.subList(commonPrefix, expectedLines.size - commonSuffix)
        /** 候选中需要新增或替换的行。 */
        val added = actualLines.subList(commonPrefix, actualLines.size - commonSuffix)
        /** 差异块后作为上下文保留的公共行。 */
        val suffixContext = if (commonSuffix == 0) {
            emptyList()
        } else {
            expectedLines.subList(expectedLines.size - commonSuffix, expectedEnd)
        }
        /** 基线差异块的统一格式起始行号。 */
        val expectedStartLine = contextStart + 1
        /** 候选差异块的统一格式起始行号。 */
        val actualStartLine = contextStart + 1
        /** 基线差异块总行数。 */
        val expectedCount = prefixContext.size + removed.size + suffixContext.size
        /** 候选差异块总行数。 */
        val actualCount = prefixContext.size + added.size + suffixContext.size

        return buildString {
            append("--- ").append(baselineLabel).append('\n')
            append("+++ ").append(candidateLabel).append('\n')
            append("@@ -").append(expectedStartLine).append(',').append(expectedCount)
            append(" +").append(actualStartLine).append(',').append(actualCount).append(" @@\n")
            prefixContext.forEach { line -> append(' ').append(line).append('\n') }
            removed.forEach { line -> append('-').append(line).append('\n') }
            added.forEach { line -> append('+').append(line).append('\n') }
            suffixContext.forEach { line -> append(' ').append(line).append('\n') }
        }
    }
}
