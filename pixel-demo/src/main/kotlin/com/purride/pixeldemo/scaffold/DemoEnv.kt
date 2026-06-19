package com.purride.pixeldemo.scaffold

import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixeldemo.app.DemoTextRasterizers
import com.purride.pixeldemo.settings.DemoAppSettings

class DemoEnv(
    val hostView: PixelHostView,
    val rasterizers: DemoTextRasterizers,
    val vsync: PixelTickerProvider,
    val applyPreferredProfile: (PixelHostProfilePreference) -> Unit,
    val navigator: DemoNavigator,
    var currentSettings: DemoAppSettings = DemoAppSettings(),
    val applySettings: (DemoAppSettings) -> Unit,
)
