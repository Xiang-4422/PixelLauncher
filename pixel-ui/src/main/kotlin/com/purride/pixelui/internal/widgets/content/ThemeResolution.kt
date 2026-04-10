package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.Theme

/**
 * 解析当前 build context 中可见的主题。
 */
internal fun BuildContext.resolveTheme(explicit: PixelThemeData?): PixelThemeData {
    return explicit ?: Theme.maybeOf(this) ?: PixelThemeData.Default
}
