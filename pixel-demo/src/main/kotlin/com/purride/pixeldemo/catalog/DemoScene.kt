package com.purride.pixeldemo.catalog

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
    val initialProfile: PixelHostProfilePreference? get() = null
    val initialPalette: PixelPalette? get() = null
    val initialTheme: ThemeData? get() = null
    val pagerGesturePolicy: PagerGesturePolicy? get() = null
    fun build(env: DemoEnv): Widget
}
