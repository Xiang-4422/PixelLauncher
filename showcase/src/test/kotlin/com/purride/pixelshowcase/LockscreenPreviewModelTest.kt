package com.purride.pixelshowcase

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.PinCredentialFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 验证离线锁屏预览覆盖范围和截图输入保持稳定。 */
class LockscreenPreviewModelTest {
    /** 默认截图使用固定 09:41，且不读取系统充电或时间状态。 */
    @Test
    fun defaultConfigurationBuildsStableState() {
        /** 默认配置转换出的锁屏状态。 */
        val state = LockscreenPreviewConfiguration().toUiState()

        assertEquals("09:41", state.timeText)
        assertEquals("SATURDAY, AUGUST 1", state.dateText)
        assertEquals(50, state.batteryPercent)
        assertFalse(state.isCharging)
    }

    /** 主题循环必须依次覆盖共享目录的全部八个家族并回到起点。 */
    @Test
    fun familyCyclingCoversAllEightThemes() {
        /** 从首个主题开始收集一整轮结果。 */
        var family = ProductThemeFamily.entries.first()
        /** 一整轮主题循环中实际访问的家族。 */
        val visited = linkedSetOf<ProductThemeFamily>()
        repeat(ProductThemeFamily.entries.size) {
            visited += family
            family = cyclePreviewFamily(family, step = 1)
        }

        assertEquals(8, visited.size)
        assertEquals(ProductThemeFamily.entries.toSet(), visited)
        assertEquals(ProductThemeFamily.entries.first(), family)
        assertEquals(ProductThemeFamily.entries.last(), cyclePreviewFamily(family, step = -1))
    }

    /** 离线预览只暴露具体日夜亮度，并完整覆盖电量、方向和背景样本。 */
    @Test
    fun previewOptionsCoverRequiredStaticMatrix() {
        assertEquals(setOf(10, 50, 92), LockscreenPreviewBattery.entries.map { it.percent }.toSet())
        assertEquals(2, ProductThemeBrightness.entries.size)
        assertEquals(2, LockscreenPreviewOrientation.entries.size)
        assertEquals(4, LockscreenPreviewBackground.entries.size)
        assertEquals(3, LockscreenPreviewScene.entries.size)
        assertEquals(4, PatternCredentialFeedback.entries.size)
        assertEquals(4, PinCredentialFeedback.entries.size)
    }

    /** 图案预览反馈必须保持固定文字且不包含任何路径数据。 */
    @Test
    fun patternPreviewBuildsControllableSafeState() {
        PatternCredentialFeedback.entries.forEach { feedback ->
            /** 当前反馈转换出的图案 UI 状态。 */
            val state = LockscreenPreviewConfiguration(patternFeedback = feedback).toPatternUiState()
            assertEquals("DRAW PATTERN", state.promptText)
            assertEquals(feedback, state.feedback)
        }
    }

    /** PIN 预览反馈必须保持固定文字且只携带非敏感输入长度。 */
    @Test
    fun pinPreviewBuildsControllableLengthOnlyState() {
        PinCredentialFeedback.entries.forEach { feedback ->
            /** 当前反馈转换出的 PIN UI 状态。 */
            val state = LockscreenPreviewConfiguration(pinFeedback = feedback).toPinUiState()
            assertEquals("ENTER PIN", state.promptText)
            assertEquals(4, state.inputLength)
            assertEquals(feedback, state.feedback)
        }
    }

    /** 背景循环必须支持向前和向后闭环，便于单手快速比较。 */
    @Test
    fun backgroundCyclingWrapsInBothDirections() {
        assertEquals(
            LockscreenPreviewBackground.LIGHT,
            cyclePreviewBackground(LockscreenPreviewBackground.HIGH_CONTRAST, step = 1),
        )
        assertEquals(
            LockscreenPreviewBackground.HIGH_CONTRAST,
            cyclePreviewBackground(LockscreenPreviewBackground.LIGHT, step = -1),
        )
    }
}
