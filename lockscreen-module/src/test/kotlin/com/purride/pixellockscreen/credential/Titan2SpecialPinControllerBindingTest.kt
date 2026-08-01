package com.purride.pixellockscreen.credential

import com.purride.pixellockscreen.ui.PinCredentialFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Titan 2 特殊数字安全页的模式和脱敏展示边界测试。 */
class Titan2SpecialPinControllerBindingTest {
    /** 只允许 APK 中已经确认的四张 SIM 与 AntiTheft 枚举名。 */
    @Test
    fun nativeModeNamesResolveOnlySupportedSpecialPages() {
        assertEquals(
            Titan2SpecialPinMode.SIM_1,
            Titan2SpecialPinMode.fromNativeName("SimPinPukMe1"),
        )
        assertEquals(
            Titan2SpecialPinMode.ANTI_THEFT,
            Titan2SpecialPinMode.fromNativeName("AntiTheft"),
        )
        assertNull(Titan2SpecialPinMode.fromNativeName("PIN"))
        assertNull(Titan2SpecialPinMode.fromNativeName("SimPin"))
    }

    /** 多行特殊页消息必须变为有界单行，内部 AntiTheft 占位符不得展示。 */
    @Test
    fun specialMessageSanitizerKeepsOnlyVisibleSystemText() {
        /** 清理后的超长系统消息。 */
        val sanitized = sanitizeSpecialSecurityMessage(" A\nB  ${"X".repeat(200)} ")
        assertEquals(160, sanitized.length)
        assertEquals(false, '\n' in sanitized)
        assertEquals("", sanitizeSpecialSecurityMessage("AntiTheft Noneed Print Text"))
    }

    /** SIM 异步校验必须禁用像素键盘且不伪造错误或失败次数。 */
    @Test
    fun checkingSnapshotUsesNativeProgressOnly() {
        /** 当前原生 SIM 服务正在校验的脱敏状态。 */
        val state = specialPinUiState(
            mode = Titan2SpecialPinMode.SIM_2,
            snapshot = Titan2SpecialPinSnapshot(
                inputLength = 0,
                messageText = "",
                checking = true,
            ),
        )
        assertEquals("UNLOCK SIM 2", state.promptText)
        assertEquals("CHECKING", state.feedbackText)
        assertEquals(PinCredentialFeedback.CHECKING, state.feedback)
    }
}
