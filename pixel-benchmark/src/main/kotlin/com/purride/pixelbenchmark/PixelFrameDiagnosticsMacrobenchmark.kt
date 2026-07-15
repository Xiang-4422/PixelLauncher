package com.purride.pixelbenchmark

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.enterText
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.prepareListScroll
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.scrollList
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.startScenario
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 为真实硬件 Host 帧输出 build/layout/paint/submit Perfetto slice 的独立归因基准。 */
@OptIn(ExperimentalMetricApi::class)
@LargeTest
@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class PixelFrameDiagnosticsMacrobenchmark {
    /** 负责单次归因旅程和 Perfetto trace 留存的官方 Macrobenchmark 规则。 */
    @get:Rule
    val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    /** 在任何启动、Home 或输入动作前拒绝未授权设备。 */
    @Before
    fun requireAuthorizedDevice() {
        BenchmarkDeviceHolder.requireAuthorizedDevice()
    }

    /** 运行一次真实列表滚动并只在该独立场景开启 Host 分阶段诊断。 */
    @Test
    fun listScrollPhaseTrace() {
        benchmarkRule.measureRepeated(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = ReleaseCompilationMode,
            iterations = TraceIterations,
            setupBlock = {
                killProcess()
                SystemClock.sleep(ProcessTerminationSettlingMillis)
                startScenario(
                    scenario = "list_scroll",
                    sentinel = "ROW 0000",
                    frameDiagnosticsEnabled = true,
                )
                prepareListScroll()
            },
        ) {
            scrollList()
        }
    }

    /** 运行一次真实文本输入并输出 Host build、layout、paint 与提交阶段的独立归因 trace。 */
    @Test
    fun textInputPhaseTrace() {
        benchmarkRule.measureRepeated(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = ReleaseCompilationMode,
            iterations = TextTraceIterations,
            setupBlock = {
                killProcess()
                SystemClock.sleep(ProcessTerminationSettlingMillis)
                startScenario(
                    scenario = "text_input",
                    sentinel = "BENCHMARK INPUT",
                    frameDiagnosticsEnabled = true,
                )
            },
        ) {
            enterText()
        }
    }

    /** 保存只用于根因归因、不参与正式 baseline 批准的稳定配置。 */
    private companion object {
        /** 要求归因 APK 与正式候选一样使用已打包的 Baseline Profile。 */
        val ReleaseCompilationMode: CompilationMode = CompilationMode.Partial(BaselineProfileMode.Require)

        /** 单次 trace 足以获得同一旅程的连续 Host 阶段分布。 */
        const val TraceIterations: Int = 1

        /** 文本输入归因保留十轮，用于捕获正式门槛中偶发的高尾绘制帧。 */
        const val TextTraceIterations: Int = 10

        /** 等待旧目标进程 cgroup 和任务状态完成清理的时长。 */
        const val ProcessTerminationSettlingMillis: Long = 100L
    }
}
