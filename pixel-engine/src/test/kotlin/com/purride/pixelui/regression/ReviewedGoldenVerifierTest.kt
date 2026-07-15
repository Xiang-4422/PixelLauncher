package com.purride.pixelui.regression

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 golden 更新只能生成可审阅候选和差异，不能自动修改源码基线。 */
class ReviewedGoldenVerifierTest {
    /** 单个像素变化必须失败、保留原基线并生成精确候选和差异。 */
    @Test
    fun onePixelMutationProducesReviewableDiffWithoutChangingBaseline() {
        /** 本测试独占的临时目录，模拟源码基线和 build 报告分离。 */
        val root = Files.createTempDirectory("pixel-reviewed-golden").toFile()
        try {
            /** 模拟已提交、不可由普通测试覆盖的 golden。 */
            val baseline = File(root, "source/golden/frame.txt").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("size=2x1\nFF000000 FFFFFFFF\n")
            }
            /** 只改变第二个像素的候选内容。 */
            val actual = "size=2x1\nFF000000 FFFFFF00\n"
            /** build 目录下的候选文件前缀。 */
            val reportStem = File(root, "build/reports/golden/frame")
            /** 比较结果必须是失败，并包含可审阅产物。 */
            val result = ReviewedGoldenVerifier.compare(baseline, actual, reportStem)

            assertFalse(result.matches)
            assertEquals("size=2x1\nFF000000 FFFFFFFF\n", baseline.readText())
            assertEquals(actual, result.candidateFile.readText())
            assertTrue(result.diffFile.readText().contains("-FF000000 FFFFFFFF"))
            assertTrue(result.diffFile.readText().contains("+FF000000 FFFFFF00"))
        } finally {
            root.deleteRecursively()
        }
    }

    /** semantics action 或生命周期顺序变化同样必须被逐字节契约拒绝。 */
    @Test
    fun semanticsActionAndLifecycleOrderMutationsAreRejected() {
        /** 本测试独占目录，避免报告相互覆盖。 */
        val root = Files.createTempDirectory("pixel-reviewed-contract").toFile()
        try {
            /** 已审阅的 semantics action 集合。 */
            val semanticsBaseline = File(root, "source/semantics.txt").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("actions=[CLICK,SET_TEXT]\n")
            }
            /** 已审阅的 Host 生命周期顺序。 */
            val lifecycleBaseline = File(root, "source/lifecycle.txt").apply {
                writeText("attach\nresume\npause\ndestroy\n")
            }
            /** 删除 SET_TEXT 的故障候选。 */
            val semanticsResult = ReviewedGoldenVerifier.compare(
                baselineFile = semanticsBaseline,
                actual = "actions=[CLICK]\n",
                reportStem = File(root, "build/semantics"),
            )
            /** 把 pause 和 destroy 交换的故障候选。 */
            val lifecycleResult = ReviewedGoldenVerifier.compare(
                baselineFile = lifecycleBaseline,
                actual = "attach\nresume\ndestroy\npause\n",
                reportStem = File(root, "build/lifecycle"),
            )

            assertFalse(semanticsResult.matches)
            assertFalse(lifecycleResult.matches)
            assertTrue(semanticsResult.diffFile.readText().contains("+actions=[CLICK]"))
            assertTrue(lifecycleResult.diffFile.readText().contains("+destroy"))
            assertEquals("actions=[CLICK,SET_TEXT]\n", semanticsBaseline.readText())
            assertEquals("attach\nresume\npause\ndestroy\n", lifecycleBaseline.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    /** 完全匹配时保留候选并清除上一次失败留下的过期差异。 */
    @Test
    fun exactMatchClearsStaleDiff() {
        /** 本测试独占目录。 */
        val root = Files.createTempDirectory("pixel-reviewed-match").toFile()
        try {
            /** 已审阅基线。 */
            val baseline = File(root, "source/frame.txt").apply {
                checkNotNull(parentFile).mkdirs()
                writeText("ARGB=FF102030\n")
            }
            /** 复用同一报告前缀以验证过期差异清理。 */
            val reportStem = File(root, "build/frame")
            ReviewedGoldenVerifier.compare(baseline, "ARGB=FF102031\n", reportStem)
            /** 第二次完全匹配的比较结果。 */
            val result = ReviewedGoldenVerifier.compare(baseline, baseline.readText(), reportStem)

            assertTrue(result.matches)
            assertTrue(result.candidateFile.exists())
            assertFalse(result.diffFile.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
