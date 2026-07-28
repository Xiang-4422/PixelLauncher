package com.purride.pixelui.host

import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelCapabilityValueResult
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelHostServices
import com.purride.pixelui.PixelNavigateBackAction
import com.purride.pixelui.PixelOpenAppSettingsAction
import com.purride.pixelui.PixelOpenUriAction
import com.purride.pixelui.PixelRequestPermissionAction
import com.purride.pixelui.PixelImeCapability
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.PixelSystemActionCapability
import com.purride.pixelui.PixelTextEditingSession
import com.purride.pixelui.PixelTextEditingValue
import com.purride.pixelui.PixelTextInputRequest
import com.purride.pixelui.PixelTypedSystemAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证聚焦 Host capability 的支持、缺失与失败结果。 */
class PixelHostCapabilitySetTest {
    /** 空 capability 集合对每条可选能力都返回明确不支持。 */
    @Test
    fun emptySetReturnsExplicitUnsupportedResults() {
        /** 显式空 Host capability 集合。 */
        val capabilities = PixelHostCapabilitySet.Empty

        assertTrue(
            capabilities.dispatchSystemAction(PixelNavigateBackAction) is
                PixelCapabilityResult.Unsupported,
        )
        assertTrue(capabilities.requestBack() is PixelCapabilityResult.Unsupported)
        assertTrue(capabilities.hideTextInput() is PixelCapabilityResult.Unsupported)
        assertTrue(capabilities.announce("hello") is PixelCapabilityResult.Unsupported)
        assertTrue(capabilities.readClipboardText() is PixelCapabilityValueResult.Unsupported)
        assertTrue(capabilities.restoreState("screen") is PixelCapabilityValueResult.Unsupported)
        /** 确认 widget 访问入口属于稳定公开 API。 */
        assertEquals("PixelHostServices", PixelHostServices::class.simpleName)
    }

    /** 每种类型安全系统动作保持自身类型和 payload，不依赖调用方拼接字符串。 */
    @Test
    fun typedSystemActionsReachCapabilityWithoutStringProtocol() {
        /** 记录 capability 收到的封闭动作对象。 */
        val received = mutableListOf<PixelTypedSystemAction>()
        /** 始终成功并记录动作的 Host capability。 */
        val systemActions = PixelSystemActionCapability { action ->
            received += action
            PixelCapabilityResult.Handled
        }
        /** 只安装系统动作能力的集合。 */
        val capabilities = PixelHostCapabilitySet(systemActions = systemActions)
        /** URI 动作。 */
        val openUri = PixelOpenUriAction("https://example.test")
        /** 设置动作。 */
        val openSettings = PixelOpenAppSettingsAction("com.example.test")
        /** 权限动作。 */
        val requestPermission = PixelRequestPermissionAction("android.permission.CAMERA")

        assertSame(PixelCapabilityResult.Handled, capabilities.dispatchSystemAction(openUri))
        assertSame(PixelCapabilityResult.Handled, capabilities.dispatchSystemAction(openSettings))
        assertSame(PixelCapabilityResult.Handled, capabilities.dispatchSystemAction(requestPermission))
        assertSame(
            PixelCapabilityResult.Handled,
            capabilities.dispatchSystemAction(PixelNavigateBackAction),
        )
        assertEquals(
            listOf(openUri, openSettings, requestPermission, PixelNavigateBackAction),
            received,
        )
    }

    /** IME capability 收到的会话保留完整配置、编辑状态和会话身份。 */
    @Test
    fun imeSessionsCarryRequestValueAndSessionIdentity() {
        /** 记录 capability 收到的完整会话。 */
        val shown = mutableListOf<PixelTextEditingSession>()
        /** 记录 capability 收到的同步会话。 */
        val updated = mutableListOf<PixelTextEditingSession>()
        /** 只安装输入法能力的集合。 */
        val capabilities = PixelHostCapabilitySet(
            ime = object : PixelImeCapability {
                override fun showTextInput(session: PixelTextEditingSession) {
                    shown += session
                }

                override fun updateTextInput(session: PixelTextEditingSession) {
                    updated += session
                }

                override fun hideTextInput(): Unit = Unit
            },
        )
        /** 两个逻辑上不同字段各自的会话身份。 */
        val firstId = Any()
        val secondId = Any()
        /** 携带 composition 的完整编辑状态。 */
        val value = PixelTextEditingValue(
            text = "\uD83D\uDE42AB",
            selectionStart = 2,
            selectionEnd = 3,
            compositionStart = 2,
            compositionEnd = 4,
        )
        /** 数字面板配置，用来确认 request 不会在边界上被裁剪。 */
        val request = PixelTextInputRequest(text = value.text, inputType = PixelInputType.NUMBER)

        assertSame(
            PixelCapabilityResult.Handled,
            capabilities.showTextInput(PixelTextEditingSession(firstId, request, value)),
        )
        assertSame(
            PixelCapabilityResult.Handled,
            capabilities.updateTextInput(PixelTextEditingSession(firstId, request, value)),
        )
        assertSame(
            PixelCapabilityResult.Handled,
            capabilities.showTextInput(PixelTextEditingSession(secondId, request, value)),
        )

        assertEquals(listOf(firstId, secondId), shown.map(PixelTextEditingSession::id))
        assertEquals(listOf(firstId), updated.map(PixelTextEditingSession::id))
        assertEquals(PixelInputType.NUMBER, shown.first().request.inputType)
        assertEquals(2, shown.first().value.compositionStart)
        assertEquals(4, shown.first().value.compositionEnd)
    }

    /** capability 抛错被转换为结构化 Failed，不穿透到 widget 调用方。 */
    @Test
    fun capabilityFailureIsContained() {
        /** capability 模拟的原始失败。 */
        val failure = IllegalStateException("host unavailable")
        /** 会抛出原始失败的系统动作 capability。 */
        val capabilities = PixelHostCapabilitySet(
            systemActions = PixelSystemActionCapability { throw failure },
        )

        /** 捕获转换后的失败结果。 */
        val result = capabilities.dispatchSystemAction(PixelNavigateBackAction)
        assertTrue(result is PixelCapabilityResult.Failed)
        assertSame(failure, (result as PixelCapabilityResult.Failed).cause)
    }
}
