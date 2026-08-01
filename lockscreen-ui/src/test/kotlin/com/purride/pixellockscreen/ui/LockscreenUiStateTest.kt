package com.purride.pixellockscreen.ui

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证静态锁屏状态边界、透明渲染和横竖屏布局规则。 */
class LockscreenUiStateTest {
    /** 电量边界值 0 和 100 均可用，越界值必须立即拒绝。 */
    @Test
    fun batteryPercentAcceptsOnlyClosedPercentageRange() {
        assertEquals(0, state(batteryPercent = 0).batteryPercent)
        assertEquals(100, state(batteryPercent = 100).batteryPercent)
        assertFails { state(batteryPercent = -1) }
        assertFails { state(batteryPercent = 101) }
    }

    /** 时间、日期和解锁提示必须包含可展示内容。 */
    @Test
    fun requiredDisplayTextRejectsBlankValues() {
        assertFails { state(timeText = " ") }
        assertFails { state(dateText = "") }
        assertFails { state(unlockHint = "\t") }
    }

    /** 生物识别状态必须拒绝缺失传感器、超长消息和多行系统提示。 */
    @Test
    fun biometricStateRejectsContradictoryInputs() {
        assertFails {
            LockscreenBiometricUiState(
                modality = LockscreenBiometricModality.NONE,
                phase = LockscreenBiometricPhase.SCANNING,
            )
        }
        assertFails {
            LockscreenBiometricUiState(
                modality = LockscreenBiometricModality.FINGERPRINT,
                phase = LockscreenBiometricPhase.ERROR,
                messageText = "A\nB",
            )
        }
        assertFails {
            LockscreenBiometricUiState(
                modality = LockscreenBiometricModality.FINGERPRINT,
                phase = LockscreenBiometricPhase.ERROR,
                messageText = "X".repeat(161),
            )
        }
    }

    /** StrongAuth 即使没有可用传感器也必须具有独立可见状态。 */
    @Test
    fun strongAuthStateRemainsVisibleWithoutBiometricEnrollment() {
        /** 当前系统要求强凭据解锁的状态。 */
        val biometric = LockscreenBiometricUiState(
            modality = LockscreenBiometricModality.NONE,
            phase = LockscreenBiometricPhase.STRONG_AUTH_REQUIRED,
            messageText = "DEVICE RESTARTED",
        )
        assertTrue(biometric.isVisible)
    }

    /** 信任提示必须保持阶段与单行文字一致。 */
    @Test
    fun securityNoticeRejectsInvalidMessages() {
        assertFails {
            LockscreenSecurityNoticeUiState(
                phase = LockscreenSecurityNoticePhase.TRUSTED,
                messageText = "",
            )
        }
        assertFails {
            LockscreenSecurityNoticeUiState(
                phase = LockscreenSecurityNoticePhase.NONE,
                messageText = "TRUSTED",
            )
        }
        assertFails {
            LockscreenSecurityNoticeUiState(
                phase = LockscreenSecurityNoticePhase.TRUST_ERROR,
                messageText = "A\nB",
            )
        }
    }

    /** 通知和媒体摘要必须保持唯一、有界、单行且不接受不可展示状态。 */
    @Test
    fun contentStatesRejectUnsafeInputs() {
        assertFails {
            state().copy(
                notifications = List(4) { index ->
                    LockscreenNotificationUiState("KEY-$index", "APP")
                },
            )
        }
        assertFails {
            state().copy(
                notifications = listOf(
                    LockscreenNotificationUiState("KEY", "APP"),
                    LockscreenNotificationUiState("KEY", "OTHER"),
                ),
            )
        }
        assertFails { LockscreenNotificationUiState("KEY", "APP\nNAME") }
        assertFails { LockscreenMediaUiState(isVisible = true, titleText = "") }
    }

    /** 媒体、公开通知与隐私替代通知应共同完成透明布局。 */
    @Test
    fun contentCardsRenderOnTransparentCanvas() {
        /** 包含媒体和三条通知的最大合法内容状态。 */
        val contentState = state().copy(
            media = LockscreenMediaUiState(
                isVisible = true,
                titleText = "PIXEL SONG",
                artistText = "ARTIST",
                isPlaying = true,
            ),
            notifications = listOf(
                LockscreenNotificationUiState("ONE", "MESSAGES", "HELLO"),
                LockscreenNotificationUiState("TWO", "MAIL", isRedacted = true),
                LockscreenNotificationUiState("THREE", "CALENDAR", "MEETING"),
            ),
        )
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    child = buildLockscreenScene(
                        LockscreenSceneRequest(
                            state = contentState,
                            family = ProductThemeFamily.ARCADE,
                            brightness = ProductThemeBrightness.DARK,
                            isLandscape = false,
                        ),
                    ),
                    width = LOCKSCREEN_PORTRAIT_WIDTH,
                    height = LOCKSCREEN_PORTRAIT_HEIGHT,
                ),
                logicalWidth = LOCKSCREEN_PORTRAIT_WIDTH,
                logicalHeight = LOCKSCREEN_PORTRAIT_HEIGHT,
            )
            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
        } finally {
            tester.dispose()
        }
    }

    /** 信任提示应优先占用安全区域并覆盖所有可见阶段。 */
    @Test
    fun securityNoticePhasesRenderOverBiometricState() {
        LockscreenSecurityNoticePhase.entries
            .filterNot { phase -> phase == LockscreenSecurityNoticePhase.NONE }
            .forEach { phase ->
                /** 当前信任阶段的离屏像素宿主。 */
                val tester = PixelTester()
                try {
                    tester.pumpWidget(
                        mediaRoot(
                            child = buildLockscreenScene(
                                LockscreenSceneRequest(
                                    state = state().copy(
                                        biometric = LockscreenBiometricUiState(
                                            modality = LockscreenBiometricModality.FINGERPRINT,
                                            phase = LockscreenBiometricPhase.SCANNING,
                                        ),
                                        securityNotice = LockscreenSecurityNoticeUiState(
                                            phase = phase,
                                            messageText = "SYSTEM TRUST STATUS",
                                        ),
                                    ),
                                    family = ProductThemeFamily.ARCADE,
                                    brightness = ProductThemeBrightness.DARK,
                                    isLandscape = false,
                                ),
                            ),
                            width = LOCKSCREEN_PORTRAIT_WIDTH,
                            height = LOCKSCREEN_PORTRAIT_HEIGHT,
                        ),
                        logicalWidth = LOCKSCREEN_PORTRAIT_WIDTH,
                        logicalHeight = LOCKSCREEN_PORTRAIT_HEIGHT,
                    )
                    assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
                } finally {
                    tester.dispose()
                }
            }
    }

    /** 大时钟在纵屏使用四倍像素，横屏固定降低为三倍。 */
    @Test
    fun timeScaleFollowsOrientation() {
        assertEquals(4, lockscreenTimeScale(isLandscape = false))
        assertEquals(3, lockscreenTimeScale(isLandscape = true))
    }

    /** 完全相同的静态输入不得触发重绘，状态或方向变化则必须提交新帧。 */
    @Test
    fun sceneRequestSkipsOnlyIdenticalFrames() {
        /** 用于建立去重基线的纵屏请求。 */
        val previous = LockscreenSceneRequest(
            state = state(),
            family = ProductThemeFamily.ARCADE,
            brightness = ProductThemeBrightness.DARK,
            isLandscape = false,
        )

        assertFalse(shouldSubmitLockscreenRequest(previous, previous.copy()))
        assertTrue(shouldSubmitLockscreenRequest(null, previous))
        assertTrue(shouldSubmitLockscreenRequest(previous, previous.copy(isLandscape = true)))
        assertTrue(
            shouldSubmitLockscreenRequest(
                previous,
                previous.copy(state = state(timeText = "21:08")),
            ),
        )
    }

    /** 透明场景必须保留空白角落，同时绘制主题主色和反差描边。 */
    @Test
    fun portraitSceneKeepsTransparentCornersAndOutlinedInk() {
        /** 当前测试使用的共享主题色板。 */
        val palette = ProductThemeCatalog.resolve(
            ProductThemeFamily.ARCADE,
            ProductThemeBrightness.DARK,
        )
        /** 离屏像素宿主。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                mediaRoot(
                    child = buildLockscreenScene(
                        LockscreenSceneRequest(
                            state = state(batteryPercent = 50),
                            family = palette.family,
                            brightness = palette.brightness,
                            isLandscape = false,
                        ),
                    ),
                    width = LOCKSCREEN_PORTRAIT_WIDTH,
                    height = LOCKSCREEN_PORTRAIT_HEIGHT,
                ),
                logicalWidth = LOCKSCREEN_PORTRAIT_WIDTH,
                logicalHeight = LOCKSCREEN_PORTRAIT_HEIGHT,
            )

            assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
            assertTrue(tester.hasPixel(palette.primary))
            assertTrue(tester.hasPixel(palette.background))
            assertTrue(tester.semanticsNodes().all { node -> node.actions.isEmpty() })
        } finally {
            tester.dispose()
        }
    }

    /** 所有可见生物识别阶段都应完成透明像素布局。 */
    @Test
    fun biometricPhasesRenderWithoutReplacingTransparentCanvas() {
        LockscreenBiometricPhase.entries
            .filterNot { phase -> phase == LockscreenBiometricPhase.UNAVAILABLE }
            .forEach { phase ->
                /** 当前阶段使用的合法传感器组合。 */
                val modality = if (phase == LockscreenBiometricPhase.STRONG_AUTH_REQUIRED) {
                    LockscreenBiometricModality.NONE
                } else {
                    LockscreenBiometricModality.FINGERPRINT
                }
                /** 当前阶段的离屏像素宿主。 */
                val tester = PixelTester()
                try {
                    tester.pumpWidget(
                        mediaRoot(
                            child = buildLockscreenScene(
                                LockscreenSceneRequest(
                                    state = state().copy(
                                        biometric = LockscreenBiometricUiState(
                                            modality = modality,
                                            phase = phase,
                                        ),
                                    ),
                                    family = ProductThemeFamily.CRT,
                                    brightness = ProductThemeBrightness.DARK,
                                    isLandscape = false,
                                ),
                            ),
                            width = LOCKSCREEN_PORTRAIT_WIDTH,
                            height = LOCKSCREEN_PORTRAIT_HEIGHT,
                        ),
                        logicalWidth = LOCKSCREEN_PORTRAIT_WIDTH,
                        logicalHeight = LOCKSCREEN_PORTRAIT_HEIGHT,
                    )
                    assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
                } finally {
                    tester.dispose()
                }
            }
    }

    /** 横屏、长日期以及关键电量状态都必须完成布局而不生成交互语义。 */
    @Test
    fun landscapeSceneHandlesLongDateAndBatteryStates() {
        listOf(0, 1, 50, 100).forEach { batteryPercent ->
            listOf(false, true).forEach { isCharging ->
                /** 当前电量和充电组合的离屏宿主。 */
                val tester = PixelTester()
                try {
                    tester.pumpWidget(
                        mediaRoot(
                            child = buildLockscreenScene(
                                LockscreenSceneRequest(
                                    state = state(
                                        dateText = "WEDNESDAY, SEPTEMBER 30, 2026",
                                        batteryPercent = batteryPercent,
                                        isCharging = isCharging,
                                    ),
                                    family = ProductThemeFamily.MIDNIGHT,
                                    brightness = ProductThemeBrightness.LIGHT,
                                    isLandscape = true,
                                ),
                            ),
                            width = LOCKSCREEN_LANDSCAPE_WIDTH,
                            height = LOCKSCREEN_LANDSCAPE_HEIGHT,
                        ),
                        logicalWidth = LOCKSCREEN_LANDSCAPE_WIDTH,
                        logicalHeight = LOCKSCREEN_LANDSCAPE_HEIGHT,
                    )
                    assertEquals(PixelColor.Transparent, tester.pixelAt(0, 0))
                    assertFalse(tester.semanticsNodes().any { node -> node.actions.isNotEmpty() })
                } finally {
                    tester.dispose()
                }
            }
        }
    }

    /** 构造一个可按测试场景覆盖字段的有效静态状态。 */
    private fun state(
        timeText: String = "09:41",
        dateText: String = "SATURDAY, AUGUST 1",
        batteryPercent: Int = 82,
        isCharging: Boolean = false,
        unlockHint: String = "SWIPE UP TO UNLOCK",
    ): LockscreenUiState = LockscreenUiState(
        timeText = timeText,
        dateText = dateText,
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        unlockHint = unlockHint,
    )

    /** 为离屏测试补充 Android PixelHostView 在运行时自动注入的视口环境。 */
    private fun mediaRoot(child: Widget, width: Int, height: Int): Widget = MediaQuery(
        data = MediaQueryData(
            logicalWidth = width,
            logicalHeight = height,
            screenProfile = ScreenProfile(logicalWidth = width, logicalHeight = height, dotSizePx = 1),
        ),
        child = child,
    )

    /** 断言指定状态构造逻辑抛出参数异常。 */
    private fun assertFails(block: () -> Unit) {
        /** 当前异常捕获结果。 */
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
