package com.purride.pixellauncherv2.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MainActivity.renderCurrentFrame] 必须是纯读边界（ADR-0001 阶段 1）。
 *
 * render 曾在提交帧之前调用 `refreshDataHealthState()` 采样六项系统能力，并轮询
 * `mediaPlaybackRepository.current()` 写回 state。后果是任何触发重绘的路径都会隐式访问
 * repository 与平台能力，render 调用次数改变可观察副作用次数，状态变更来源无法追踪，
 * 后续 coordinator 拆分也无法建立清晰的所有权。
 *
 * 目标不变量：数据/能力刷新产生事件并先提交 state，render 只读取已提交快照——
 * 连续调用两次 render 不访问 repository、不改变 state。
 *
 * MainActivity 依赖 Android 运行时，JVM 测试无法直接实例化后连续调用 render，
 * 因此按仓库既有契约测试口径（见 [MainActivityRequestCodeContractTest]）对
 * `renderCurrentFrame` 的方法体做源码扫描：禁止出现 repository 引用、能力采样入口
 * 与任何 `state =` 写入。真机行为验收由 instrumentation/人工链路承担。
 */
class MainActivityRenderPurityContractTest {

    /** render 方法体内禁止出现的引用：repository 访问、能力采样与派生刷新入口。 */
    private val forbiddenReferences = listOf(
        "Repository",
        "refreshDataHealthState",
        "refreshDerivedUiState",
        "checkSelfPermission",
        "contentResolver",
    )

    @Test
    fun renderCurrentFrameDoesNotTouchRepositoriesOrCapabilities() {
        val body = renderCurrentFrameBody()
        val violations = forbiddenReferences.filter { reference -> body.contains(reference) }
        assertEquals(
            "renderCurrentFrame 必须保持纯读：数据/能力刷新只能发生在明确入口，不能藏在渲染路径里",
            emptyList<String>(),
            violations,
        )
    }

    @Test
    fun renderCurrentFrameDoesNotWriteState() {
        val body = renderCurrentFrameBody()
        val stateWrites = statementLevelStateAssignments(body)
        assertTrue(
            "renderCurrentFrame 只能投影已提交的 state 快照，不能写 state（发现 $stateWrites 处赋值）",
            stateWrites == 0,
        )
        assertTrue(
            "renderCurrentFrame 不能调用 reducer（LauncherStateTransitions）",
            !body.contains("LauncherStateTransitions"),
        )
    }

    /**
     * 统计语句级的 `state =` 赋值。具名实参（如 `update(state = uiState)`）位于括号内，
     * 通过圆括号深度区分：深度 0 的匹配才是对 Activity state 属性的写入。
     */
    private fun statementLevelStateAssignments(body: String): Int {
        var parenDepth = 0
        var count = 0
        val matches = Regex("""\bstate\s*=(?!=)""").findAll(body).map { it.range.first }.toSet()
        body.forEachIndexed { index, char ->
            when (char) {
                '(' -> parenDepth++
                ')' -> parenDepth--
            }
            if (index in matches && parenDepth == 0) {
                count++
            }
        }
        return count
    }

    /** 用括号配对从源码截取 renderCurrentFrame 的完整方法体。 */
    private fun renderCurrentFrameBody(): String {
        val source = java.io.File(".").canonicalFile
            .let { cwd -> if (cwd.name == "app") cwd else cwd.resolve("app") }
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()
        val signature = "private fun renderCurrentFrame()"
        val start = source.indexOf(signature)
        assertTrue("MainActivity 中必须存在 renderCurrentFrame", start >= 0)
        val bodyStart = source.indexOf('{', start)
        var depth = 0
        var index = bodyStart
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(bodyStart, index + 1)
                    }
                }
            }
            index++
        }
        throw AssertionError("renderCurrentFrame 方法体括号不配对")
    }
}
