package com.purride.pixelshowcase

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.LockscreenBiometricModality
import com.purride.pixellockscreen.ui.LockscreenBiometricPhase
import com.purride.pixellockscreen.ui.LockscreenSecurityNoticePhase
import com.purride.pixellockscreen.ui.PasswordCredentialFeedback
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
        assertEquals(listOf("FLASHLIGHT", "CAMERA"), state.quickActions.map { it.labelText })
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
        assertEquals(4, LockscreenPreviewScene.entries.size)
        assertEquals(4, PatternCredentialFeedback.entries.size)
        assertEquals(4, PinCredentialFeedback.entries.size)
        assertEquals(4, PasswordCredentialFeedback.entries.size)
        assertEquals(4, LockscreenBiometricModality.entries.size)
        assertEquals(7, LockscreenBiometricPhase.entries.size)
        assertEquals(4, LockscreenSecurityNoticePhase.entries.size)
        assertEquals(5, LockscreenPreviewContent.entries.size)
        assertEquals(2, LockscreenPreviewDisplayMode.entries.size)
    }

    /** AOD 预览必须启用固定防烧屏偏移，交互预览保持原点。 */
    @Test
    fun displayModeBuildsStableAmbientState() {
        /** 固定 AOD 配置转换出的低功耗状态。 */
        val ambient = LockscreenPreviewConfiguration(
            displayMode = LockscreenPreviewDisplayMode.AOD,
        ).toUiState().ambient
        assertEquals(true, ambient.isAmbient)
        assertEquals(2, ambient.burnInOffsetX)
        assertEquals(-1, ambient.burnInOffsetY)

        /** 默认交互锁屏不允许携带 AOD 偏移。 */
        val active = LockscreenPreviewConfiguration().toUiState().ambient
        assertFalse(active.isAmbient)
        assertEquals(0, active.burnInOffsetX)
        assertEquals(0, active.burnInOffsetY)
    }

    /** 内容预览必须覆盖空、公开、隐私替代、媒体和混合最大状态。 */
    @Test
    fun contentPreviewCoversPrivacyAndMediaMatrix() {
        LockscreenPreviewContent.entries.forEach { content ->
            /** 当前离线内容样本转换出的普通锁屏状态。 */
            val state = LockscreenPreviewConfiguration(content = content).toUiState()
            when (content) {
                LockscreenPreviewContent.EMPTY -> {
                    assertEquals(0, state.notifications.size)
                    assertFalse(state.media.isVisible)
                }
                LockscreenPreviewContent.PUBLIC_NOTIFICATION ->
                    assertFalse(state.notifications.single().isRedacted)
                LockscreenPreviewContent.REDACTED_NOTIFICATION ->
                    assertEquals(true, state.notifications.single().isRedacted)
                LockscreenPreviewContent.MEDIA -> assertEquals(true, state.media.isVisible)
                LockscreenPreviewContent.MIXED -> {
                    assertEquals(3, state.notifications.size)
                    assertEquals(true, state.media.isVisible)
                }
            }
        }
    }

    /** 信任状态预览必须为每个活跃阶段提供稳定且可见的离线文字。 */
    @Test
    fun securityNoticePreviewCoversAllVisiblePhases() {
        LockscreenSecurityNoticePhase.entries.forEach { phase ->
            /** 当前信任阶段转换出的普通锁屏状态。 */
            val state = LockscreenPreviewConfiguration(
                securityNoticePhase = phase,
            ).toUiState()

            assertEquals(phase, state.securityNotice.phase)
            assertEquals(
                phase != LockscreenSecurityNoticePhase.NONE,
                state.securityNotice.isVisible,
            )
        }
    }

    /** 生物识别预览必须自动维护传感器与认证阶段的合法组合。 */
    @Test
    fun biometricPreviewKeepsValidStateCombinations() {
        /** 从无传感器切换到采集阶段后的合法配置。 */
        val scanning = LockscreenPreviewConfiguration()
            .withBiometricPhase(LockscreenBiometricPhase.SCANNING)
        assertEquals(LockscreenBiometricModality.FINGERPRINT, scanning.biometricModality)
        assertEquals("SCANNING", scanning.toUiState().biometric.messageText)

        /** 移除传感器后必须回到不可用阶段。 */
        val unavailable = scanning.withBiometricModality(LockscreenBiometricModality.NONE)
        assertEquals(LockscreenBiometricPhase.UNAVAILABLE, unavailable.biometricPhase)
        assertFalse(unavailable.toUiState().biometric.isVisible)

        /** StrongAuth 无需伪造一个已注册传感器。 */
        val strongAuth = LockscreenPreviewConfiguration()
            .withBiometricPhase(LockscreenBiometricPhase.STRONG_AUTH_REQUIRED)
        assertEquals(LockscreenBiometricModality.NONE, strongAuth.biometricModality)
        assertEquals("USE PIN AFTER RESTART", strongAuth.toUiState().biometric.messageText)
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

    /** 密码预览必须只携带长度、焦点、入口和可控反馈。 */
    @Test
    fun passwordPreviewBuildsControllableLengthOnlyState() {
        PasswordCredentialFeedback.entries.forEach { feedback ->
            /** 当前反馈转换出的密码 UI 状态。 */
            val state = LockscreenPreviewConfiguration(
                passwordFeedback = feedback,
            ).toPasswordUiState()
            assertEquals("ENTER PASSWORD", state.promptText)
            assertEquals(8, state.inputLength)
            assertEquals(feedback, state.feedback)
            assertEquals(true, state.hasInputFocus)
            assertEquals(true, state.isImeSwitcherVisible)
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
