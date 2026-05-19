package com.purride.pixeldemo.scaffold

import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostView
import com.purride.pixeldemo.app.DemoTextRasterizers

class DemoEnv(
    val hostView: PixelHostView,
    val rasterizers: DemoTextRasterizers,
    val applyPreferredProfile: (PixelHostProfilePreference) -> Unit,
    val navigator: DemoNavigator,
)
