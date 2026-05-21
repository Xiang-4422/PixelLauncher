package com.purride.pixeldemo.catalog

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelPalette
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.ThemeData
import com.purride.pixelui.Widget
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixeldemo.scaffold.DemoEnv

interface DemoScene {
    val id: String
    val title: String
    val description: String
    /** null = 跟随全局设置，非 null = 强制覆盖（如 ColorMode demo 固定 Color 模式）。 */
    val colorMode: PixelColorMode? get() = null
    val initialProfile: PixelHostProfilePreference? get() = null
    val initialPalette: PixelPalette? get() = null
    val initialTheme: ThemeData? get() = null
    val pagerGesturePolicy: PagerGesturePolicy? get() = null
    /** true = build() 返回完整屏幕布局，DemoActivity 不套 DemoScaffold。 */
    val isFullScreen: Boolean get() = false
    fun build(env: DemoEnv): Widget
}
