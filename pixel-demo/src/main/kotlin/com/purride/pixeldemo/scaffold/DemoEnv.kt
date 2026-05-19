package com.purride.pixeldemo.scaffold

import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.PixelHostView
import com.purride.pixeldemo.app.DemoTextRasterizers

class DemoEnv(
    val hostView: PixelHostView,
    val rasterizers: DemoTextRasterizers,
    val applyPreferredProfile: (ScreenProfile) -> Unit,
    val navigator: DemoNavigator,
)
