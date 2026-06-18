package com.purride.pixellauncherv2.launcher

/** 渲染层使用的固定字体像素尺寸。UI 组件按需直接引用，不再通过全局设置项控制。 */
enum class PixelFontSize(val px: Int) {
    PX_8(8),
    PX_10(10),
    PX_12(12),
}

enum class PixelFontStyle {
    MONO,
    PROP,
}

object PixelFontCatalog {

    val defaultFontStyle: PixelFontStyle = PixelFontStyle.PROP

    fun fontStyleOptions(): List<PixelFontStyle> = PixelFontStyle.entries

    fun styleLabel(style: PixelFontStyle): String {
        return when (style) {
            PixelFontStyle.MONO -> "MONO"
            PixelFontStyle.PROP -> "PROP"
        }
    }
}
