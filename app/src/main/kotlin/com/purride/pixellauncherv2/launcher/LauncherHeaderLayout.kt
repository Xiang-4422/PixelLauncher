package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.GlyphStyle
import kotlin.math.max

object LauncherHeaderLayout {

    const val horizontalPadding = 2

    val rowY: Int
        get() = 0

    val textOffsetY: Int
        get() = 0

    val headerTextY: Int
        get() = rowY + textOffsetY

    val dividerY: Int
        get() = headerTextY + GlyphStyle.UI_SMALL_10.cellHeight + 1

    val contentTop: Int
        get() = dividerY + 1

    val firstContentItemTop: Int
        get() = contentTop + max(3, GlyphStyle.UI_SMALL_10.cellHeight / 3)

    val titleGap: Int
        get() = max(2, GlyphStyle.UI_SMALL_10.narrowAdvanceWidth / 2)
}
