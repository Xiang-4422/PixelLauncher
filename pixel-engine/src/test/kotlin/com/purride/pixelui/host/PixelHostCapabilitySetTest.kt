package com.purride.pixelui.host

import com.purride.pixelui.PixelCapabilityResult
import com.purride.pixelui.PixelCapabilityValueResult
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelHostServices
import com.purride.pixelui.PixelNavigateBackAction
import com.purride.pixelui.PixelOpenAppSettingsAction
import com.purride.pixelui.PixelOpenUriAction
import com.purride.pixelui.PixelRequestPermissionAction
import com.purride.pixelui.PixelSystemActionCapability
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
