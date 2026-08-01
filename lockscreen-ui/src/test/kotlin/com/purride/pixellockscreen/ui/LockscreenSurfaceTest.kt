package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelShape
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁屏全屏底色的纯 JVM 合同测试。 */
class LockscreenSurfaceTest {
    /** 八个主题的日夜底色都必须不透明，才能完全遮住系统壁纸。 */
    @Test
    fun everyThemeSurfaceIsOpaque() {
        ProductThemeFamily.entries.forEach { family ->
            ProductThemeBrightness.entries.forEach { brightness ->
                /** 当前主题变体的最小锁屏外观。 */
                val appearance = LockscreenAppearance(
                    pixelShape = PixelShape.SQUARE,
                    dotSizePx = 12,
                    pixelGapEnabled = false,
                    themeFamily = family,
                    brightness = brightness,
                )

                assertEquals(0xFF, appearance.surfaceColor().argb ushr 24)
            }
        }
    }
}
