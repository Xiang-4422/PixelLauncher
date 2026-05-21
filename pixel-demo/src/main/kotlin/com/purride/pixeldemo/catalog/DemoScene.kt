package com.purride.pixeldemo.catalog

import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.Widget
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixeldemo.scaffold.DemoEnv

interface DemoScene {
    val id: String
    val title: String
    val description: String
    val initialProfile: PixelHostProfilePreference? get() = null
    val pagerGesturePolicy: PagerGesturePolicy? get() = null
    /** true = build() 返回完整屏幕布局，DemoActivity 不套 DemoScaffold。 */
    val isFullScreen: Boolean get() = false
    fun build(env: DemoEnv): Widget
}
