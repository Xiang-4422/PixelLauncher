package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelErrorBoundary
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.services.PixelClock
import com.purride.pixelui.services.PixelErrorEvent
import com.purride.pixelui.services.PixelErrorPhase
import com.purride.pixelui.services.PixelErrorRecoveryResult
import com.purride.pixelui.services.PixelErrorReporter
import com.purride.pixelui.services.PixelLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证 runtime 在纯 JVM 环境使用注入服务发布结构化错误。 */
class PixelUiRuntimeServicesTest {
    /** build 异常被 ErrorBoundary 恢复后也会进入统一结构化 reporter。 */
    @Test
    fun recoveredBuildFailureReportsRecoveredResult() {
        /** 捕获恢复事件的列表。 */
        val events = mutableListOf<PixelErrorEvent>()
        /** build 阶段模拟的原始异常。 */
        val failure = IllegalStateException("recoverable build")
        /** 只使用 fake service 的 runtime。 */
        val runtime = PixelUiRuntime(
            onVisualUpdate = { },
            clock = FixedClock(nanoTime = 66L),
            errorReporter = PixelErrorReporter(events::add),
            logger = PixelLogger.None,
        )

        runtime.render(
            root = PixelErrorBoundary(child = ThrowingBuildWidget(failure)),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(1, events.size)
        assertSame(failure, events.single().cause)
        assertEquals(PixelErrorPhase.BUILD, events.single().phase)
        assertEquals(PixelErrorRecoveryResult.RECOVERED, events.single().recoveryResult)
        assertTrue(events.single().context.widgetType.orEmpty().contains("ThrowingBuildWidget"))
        runtime.dispose()
    }

    /** render 异常被 ErrorBoundary 恢复后发布 RECOVERED 结果并保留原始 cause。 */
    @Test
    fun renderRecoveryReportsStructuredResult() {
        /** 捕获恢复事件的列表。 */
        val events = mutableListOf<PixelErrorEvent>()
        /** layout 阶段模拟的原始异常。 */
        val failure = IllegalStateException("layout failed")
        /** 只使用 fake service 的 runtime。 */
        val runtime = PixelUiRuntime(
            onVisualUpdate = { },
            clock = FixedClock(nanoTime = 77L),
            errorReporter = PixelErrorReporter(events::add),
            logger = PixelLogger.None,
        )

        runtime.render(
            root = PixelErrorBoundary(child = ThrowingRenderWidget(failure)),
            logicalWidth = 8,
            logicalHeight = 8,
        )

        assertEquals(1, events.size)
        assertSame(failure, events.single().cause)
        assertEquals(PixelErrorPhase.RENDER, events.single().phase)
        assertEquals(PixelErrorRecoveryResult.RECOVERED, events.single().recoveryResult)
        assertEquals(77L, events.single().timestampNanos)
        runtime.dispose()
    }

    /** 构建异常保留 cause、阶段、Widget 上下文和 Engine 时间。 */
    @Test
    fun buildFailureReportsStructuredEventWithoutAndroidServices() {
        /** 测试期固定单调时间。 */
        val clock = FixedClock(nanoTime = 42_000L)
        /** 捕获 reporter 收到的事件。 */
        val events = mutableListOf<PixelErrorEvent>()
        /** 应由 runtime 原样重新抛出的构建异常。 */
        val failure = IllegalStateException("build failed")
        /** 只依赖 fake service 的 runtime。 */
        val runtime = PixelUiRuntime(
            onVisualUpdate = { },
            clock = clock,
            errorReporter = PixelErrorReporter(events::add),
            logger = PixelLogger.None,
        )

        /** 捕获 render 重新抛出的原始异常。 */
        val thrown = runCatching {
            runtime.render(ThrowingBuildWidget(failure), logicalWidth = 8, logicalHeight = 8)
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(1, events.size)
        assertSame(failure, events.single().cause)
        assertEquals(PixelErrorPhase.BUILD, events.single().phase)
        assertEquals(PixelErrorRecoveryResult.NOT_ATTEMPTED, events.single().recoveryResult)
        assertEquals(42_000L, events.single().timestampNanos)
        assertTrue(events.single().context.widgetType.orEmpty().contains("ThrowingBuildWidget"))
        runtime.dispose()
    }

    /** reporter 自身失败不会覆盖原始引擎异常。 */
    @Test
    fun reporterFailureDoesNotMaskOriginalFailure() {
        /** 原始构建异常。 */
        val original = IllegalArgumentException("original")
        /** reporter 模拟后端不可用。 */
        val reporter = PixelErrorReporter { throw IllegalStateException("reporter") }
        /** 使用 fake service 的 runtime。 */
        val runtime = PixelUiRuntime(
            onVisualUpdate = { },
            clock = FixedClock(nanoTime = 1L),
            errorReporter = reporter,
            logger = PixelLogger.None,
        )

        /** 捕获 runtime 对调用方公开的异常。 */
        val thrown = runCatching {
            runtime.render(ThrowingBuildWidget(original), logicalWidth = 8, logicalHeight = 8)
        }.exceptionOrNull()

        assertSame(original, thrown)
        runtime.dispose()
    }

    /** build 时始终抛出指定异常的测试 Widget。 */
    private class ThrowingBuildWidget(
        /** 需要抛出的原始异常。 */
        private val failure: RuntimeException,
    ) : StatelessWidget() {
        /** 模拟 retained build 失败。 */
        override fun build(context: BuildContext): Widget {
            throw failure
        }
    }

    /** 在 layout 阶段抛错的最小 render widget。 */
    private class ThrowingRenderWidget(
        /** layout 时需要抛出的异常。 */
        private val failure: RuntimeException,
    ) : LeafRenderObjectWidget() {
        /** 创建只属于当前测试的 render box。 */
        override fun createRenderObject(context: BuildContext): RenderObject {
            return ThrowingRenderBox(failure)
        }
    }

    /** 在 layout 阶段抛错、paint 不执行工作的 render box。 */
    private class ThrowingRenderBox(
        /** layout 时需要抛出的异常。 */
        private val failure: RuntimeException,
    ) : RenderBox() {
        /** 模拟 render layout 失败。 */
        override fun layout(constraints: RenderConstraints) {
            throw failure
        }

        /** 测试不会到达 paint；保留确定性空实现。 */
        override fun paint(context: PaintContext, offsetX: Int, offsetY: Int): Unit = Unit
    }

    /** 仅提供固定单调纳秒的 fake 时钟。 */
    private class FixedClock(
        /** 固定单调纳秒。 */
        private val nanoTime: Long,
    ) : PixelClock {
        /** 把固定纳秒换算为毫秒。 */
        override fun uptimeMillis(): Long = nanoTime / 1_000_000L

        /** 返回固定单调纳秒。 */
        override fun nanoTime(): Long = nanoTime

        /** 测试不使用墙上时间，返回零。 */
        override fun currentTimeMillis(): Long = 0L
    }
}
