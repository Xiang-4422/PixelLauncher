package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelColorMode

internal fun checkColorParamInMono(
    widgetName: String,
    paramName: String,
    color: PixelColor?,
    colorMode: PixelColorMode,
) {
    if (color != null && colorMode == PixelColorMode.Mono) {
        throw IllegalStateException(
            "$widgetName.$paramName is set but colorMode is Mono — color params require Color mode.",
        )
    }
}
