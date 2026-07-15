package com.purride.pixelbenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.openDetails
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.prepareListScroll
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.runAnimation
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.scrollList
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.showOverlay
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.startScenario
import com.purride.pixelbenchmark.PixelBenchmarkJourneys.enterText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 为消费者 Release 构建生成启动与关键用户旅程 profile 规则。 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PixelBaselineProfileGenerator {
    /** 把 ART 方法与类使用情况采集为可审阅 profile 规则的官方测试规则。 */
    @get:Rule
    val baselineProfileRule: BaselineProfileRule = BaselineProfileRule()

    /** 在任何 Home、启动或输入动作前拒绝未显式授权的测试设备。 */
    @Before
    fun requireAuthorizedDevice() {
        BenchmarkDeviceHolder.requireAuthorizedDevice()
    }

    /** 只把首屏路径同时采集到 Baseline Profile 与 Startup Profile。 */
    @Test
    fun startup() {
        baselineProfileRule.collect(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startScenario("startup", "STARTUP READY")
        }
    }

    /** 采集五个运行时关键旅程，同时避免污染 Startup Profile 子集。 */
    @Test
    fun criticalUserJourneys() {
        baselineProfileRule.collect(
            packageName = PixelBenchmarkJourneys.TargetPackage,
            includeInStartupProfile = false,
        ) {
            startScenario("list_scroll", "ROW 0000")
            prepareListScroll()
            scrollList()
            startScenario("text_input", "BENCHMARK INPUT")
            enterText()
            startScenario("animation", "ANIMATE")
            runAnimation()
            startScenario("page_transition", "OPEN DETAILS")
            openDetails()
            startScenario("overlay", "SHOW OVERLAY")
            showOverlay()
        }
    }
}
