package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelShape
import com.purride.pixeldesign.ProductAppearance
import com.purride.pixeldesign.ProductPixelCatalog
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixeldesign.ProductThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 共享像素规格下普通锁屏与凭据页的自适应几何测试。 */
class LockscreenAdaptiveLayoutTest {
    /** Titan 2 方屏在全部共享点大小下都必须保持凭据控件完整可见。 */
    @Test
    fun allSupportedDotSizesKeepCredentialLayoutsInsideSquareViewport() {
        ProductPixelCatalog.supportedDotSizePxOptions.forEach { dotSizePx ->
            /** 当前点大小在 Titan 2 物理画布上生成的逻辑尺寸。 */
            val logicalSize = lockscreenLogicalSize(1436, 1440, dotSizePx)
            /** 当前图案页布局。 */
            val pattern = patternCredentialLayout(logicalSize.first, logicalSize.second)
            /** 当前 PIN 页布局。 */
            val pin = pinCredentialLayout(logicalSize.first, logicalSize.second)
            /** 当前密码页布局。 */
            val password = passwordCredentialLayout(logicalSize.first, logicalSize.second)

            assertTrue(pattern.patternLeft >= 0)
            assertTrue(pattern.patternLeft + pattern.patternSize <= pattern.logicalWidth)
            assertTrue(pattern.patternTop + pattern.patternSize <= pattern.feedbackTop)
            assertTrue(pattern.emergencyTop + pattern.emergencyHeight <= pattern.logicalHeight)
            assertEquals(12, pin.keys.size)
            assertTrue(pin.keys.all { key -> key.left >= 0 && key.left + key.width <= pin.logicalWidth })
            assertTrue(pin.keys.all { key -> key.top >= 0 && key.top + key.height <= pin.emergencyTop })
            assertTrue(password.inputAction.left >= 0)
            assertTrue(password.inputAction.left + password.inputAction.width <= password.logicalWidth)
            assertTrue(
                password.emergencyAction.top + password.emergencyAction.height <=
                    password.logicalHeight,
            )
        }
    }

    /** AUTO 必须只跟随当前 SystemUI 明暗，同时保留全部像素与主题家族字段。 */
    @Test
    fun sharedAppearanceResolvesAutoWithoutChangingPixelSettings() {
        /** 模拟 Launcher 发布的完整共享外观。 */
        val product = ProductAppearance(
            pixelShape = PixelShape.CIRCLE,
            dotSizePx = 16,
            pixelGapEnabled = true,
            themeFamily = ProductThemeFamily.CITRUS,
            themeMode = ProductThemeMode.AUTO,
        )

        /** 日间 SystemUI 解析结果。 */
        val day = product.resolveLockscreenAppearance(systemInDarkMode = false)
        /** 夜间 SystemUI 解析结果。 */
        val night = product.resolveLockscreenAppearance(systemInDarkMode = true)

        assertEquals(ProductThemeBrightness.LIGHT, day.brightness)
        assertEquals(ProductThemeBrightness.DARK, night.brightness)
        assertEquals(PixelShape.CIRCLE, night.pixelShape)
        assertEquals(16, night.dotSizePx)
        assertEquals(true, night.pixelGapEnabled)
        assertEquals(ProductThemeFamily.CITRUS, night.themeFamily)
    }
}
