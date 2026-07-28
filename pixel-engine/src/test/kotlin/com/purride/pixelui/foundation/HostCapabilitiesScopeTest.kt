package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Builder
import com.purride.pixelui.Container
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.TextDirection
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** 校验 HostCapabilities 作用域查找：显式注入优先，headless 场景回到确定性默认快照。 */
class HostCapabilitiesScopeTest {
    /**
     * `maybeOf` 区分“没有宿主作用域”，`of` 给出可预测的 headless 默认值。
     *
     * headless 默认不是历史兼容层：离屏渲染、直接 render 测试和未注入能力的嵌入宿主都依赖它
     * 得到一份确定的 English/LTR/1x 快照，而不是抛异常。
     */
    @Test
    fun capabilityLookupPrefersExplicitScopeAndFallsBackToHeadlessDefault() {
        /** 探针观察到的最新一次显式继承快照。 */
        var explicitSnapshot: HostCapabilitiesData? = HostCapabilitiesData.Default
        /** 探针观察到的最新一次生效快照。 */
        var effectiveSnapshot: HostCapabilitiesData? = null
        /** 在两帧环境之间保持 retained 身份不变的稳定探针。 */
        val probe = Builder(key = "capability-probe") { context ->
            explicitSnapshot = HostCapabilities.maybeOf(context)
            effectiveSnapshot = HostCapabilities.of(context)
            Container(width = 1, height = 1, fillColor = PixelColor.Transparent)
        }
        /** 仅用于重建继承环境的 retained 测试 runtime。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(probe, logicalWidth = 1, logicalHeight = 1)
            assertNull(explicitSnapshot)
            assertSame(HostCapabilitiesData.Default, effectiveSnapshot)

            /** 非默认快照，用于证明每个访问器都返回显式继承的值。 */
            val rtlSnapshot = HostCapabilitiesData(
                locales = listOf(PixelLocale("ar")),
                layoutDirection = TextDirection.RTL,
                textScaleFactor = 1.5f,
                highContrast = true,
                density = 2f,
                refreshRateHz = 120f,
            )
            tester.pumpWidget(
                HostCapabilities(
                    data = rtlSnapshot,
                    child = probe,
                    key = "capability-scope",
                ),
                logicalWidth = 1,
                logicalHeight = 1,
            )
            assertSame(rtlSnapshot, explicitSnapshot)
            assertSame(rtlSnapshot, effectiveSnapshot)
        } finally {
            tester.dispose()
        }
    }
}
