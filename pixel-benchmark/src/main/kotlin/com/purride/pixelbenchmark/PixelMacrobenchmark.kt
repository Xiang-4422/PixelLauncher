package com.purride.pixelbenchmark

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.enterText
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.openDetails
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.prepareListScroll
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.runAnimation
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.scrollList
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.showOverlay
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.startScenario
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 在接近 Release 的构建中覆盖 M6-2 七个关键用户旅程的端到端基准。 */
@OptIn(ExperimentalMacrobenchmarkApi::class, ExperimentalMetricApi::class)
@LargeTest
@RunWith(AndroidJUnit4::class)
class PixelMacrobenchmark {
    /** 负责编译模式控制、指标采集和 trace 留存的官方 Macrobenchmark 规则。 */
    @get:Rule
    val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    /** 在 Macrobenchmark 规则可能执行 Home、启动或输入动作前拒绝未授权设备。 */
    @Before
    fun requireAuthorizedDevice() {
        BenchmarkDeviceHolder.requireAuthorizedDevice()
    }

    /** 测量进程未驻留时的启动；API 29+ 记录 TTID，API 24–28 记录启动旅程的 gfxinfo 帧。 */
    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            metrics = startupMetrics(),
            compilationMode = ReleaseCompilationMode,
            startupMode = StartupMode.COLD,
            iterations = StartupIterations,
            setupBlock = { pressHome() },
        ) {
            startScenario("startup", "STARTUP READY")
        }
    }

    /** 测量目标进程驻留时的启动；API 29+ 记录 TTID，API 24–28 记录启动旅程的 gfxinfo 帧。 */
    @Test
    fun hotStartup() {
        benchmarkRule.measureRepeated(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            metrics = startupMetrics(),
            compilationMode = ReleaseCompilationMode,
            startupMode = StartupMode.HOT,
            iterations = StartupIterations,
            setupBlock = { startScenario("startup", "STARTUP READY"); pressHome() },
        ) {
            startScenario("startup", "STARTUP READY")
        }
    }

    /** 测量懒列表重复物理滚动期间的 build、layout、paint 与 Canvas 帧。 */
    @Test
    fun listScroll() {
        measureFrames(
            scenario = "list_scroll",
            sentinel = "ROW 0000",
            prepareJourney = { prepareListScroll() },
        ) {
            scrollList()
        }
    }

    /** 测量 Android 文本编辑、段落布局、选区和重绘帧。 */
    @Test
    fun textInput() = measureFrames("text_input", "BENCHMARK INPUT") { enterText() }

    /** 测量 Host ticker 投递和 retained 隐式动画帧。 */
    @Test
    fun animation() = measureFrames("animation", "ANIMATE") { runAnimation() }

    /** 测量生产 Navigator 的 push、页面组合与转场动画。 */
    @Test
    fun pageTransition() = measureFrames("page_transition", "OPEN DETAILS") { openDetails() }

    /** 测量生产模态路由的组合与 Android 绘制提交。 */
    @Test
    fun overlay() = measureFrames("overlay", "SHOW OVERLAY") { showOverlay() }

    /** 使用统一的 Release 编译和 trace 配置运行一个会产生帧的旅程。 */
    private fun measureFrames(
        scenario: String,
        sentinel: String,
        prepareJourney: MacrobenchmarkScope.() -> Unit = {},
        journey: () -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            metrics = frameMetrics(),
            compilationMode = ReleaseCompilationMode,
            iterations = FrameIterations,
            setupBlock = {
                // 每次帧测量先只终止目标包，隔离上一场景的 IME、窗口和 retained 进程状态。
                killProcess()
                // API 29 可能在 PID 消失后继续异步清理 cgroup；计时区外等待，避免新启动命中旧终止竞态。
                SystemClock.sleep(ProcessTerminationSettlingMillis)
                startScenario(scenario, sentinel)
                prepareJourney()
            },
        ) {
            journey()
        }
    }

    /** API 29+ 使用 trace 推导的 TTID，API 24–28 使用官方 gfxinfo 帧时序兼容指标。 */
    private fun startupMetrics(): List<Metric> = if (supportsTraceFrameMetrics()) {
        listOf(StartupTimingMetric())
    } else {
        listOf(FrameTimingGfxInfoMetric())
    }

    /** API 29+ 使用 Perfetto UI/RenderThread 帧时序，API 24–28 使用官方 gfxinfo 指标。 */
    private fun frameMetrics(): List<Metric> = if (supportsTraceFrameMetrics()) {
        listOf(FrameTimingMetric())
    } else {
        listOf(FrameTimingGfxInfoMetric())
    }

    /** 返回当前平台是否内置能够稳定产出帧 slice 的 Perfetto。 */
    private fun supportsTraceFrameMetrics(): Boolean = Build.VERSION.SDK_INT >= TraceFrameMetricMinApi

    /** 保存兼顾噪声抑制和本地执行时长的稳定迭代配置。 */
    private companion object {
        /** 要求测量前已打包 Baseline Profile 的生产编译模式。 */
        val ReleaseCompilationMode: CompilationMode = CompilationMode.Partial(BaselineProfileMode.Require)

        /** 冷启动和热启动分布的重复次数。 */
        const val StartupIterations: Int = 10

        /** 每个关键产帧用户旅程的重复次数。 */
        const val FrameIterations: Int = 10

        /** API 29 首次内置 Perfetto；更旧的 AVD 内核可能未启用 FTRACE。 */
        const val TraceFrameMetricMinApi: Int = Build.VERSION_CODES.Q

        /** 帧旅程 setup 中等待旧进程 cgroup 与任务状态完全收敛的时长。 */
        const val ProcessTerminationSettlingMillis: Long = 100L
    }
}
