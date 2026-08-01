package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Icon
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.PixelSystemIcons
import com.purride.pixelui.Widget

/**
 * 把当前正文字号映射到行内系统图标规格。
 *
 * 15px 规格只用于独立大按钮和 Showcase；普通单行控件最高使用 11px，避免撑高现有布局。
 */
internal fun launcherInlineIconSize(fontSizePx: Int): PixelSystemIconSize = when {
    fontSizePx <= 8 -> PixelSystemIconSize.SMALL
    fontSizePx <= 10 -> PixelSystemIconSize.MEDIUM
    else -> PixelSystemIconSize.LARGE
}

/** 使用 Launcher 已解析的主题颜色绘制一个无独立语义的系统图标。 */
internal fun launcherSystemIcon(
    icon: PixelSystemIcon,
    size: PixelSystemIconSize,
    color: PixelColor,
    key: Any? = null,
): Widget = Icon(
    icon = PixelSystemIcons.tinted(icon = icon, size = size, color = color),
    key = key,
)
