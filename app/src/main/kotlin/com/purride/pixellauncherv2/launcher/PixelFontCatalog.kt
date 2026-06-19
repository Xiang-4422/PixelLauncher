package com.purride.pixellauncherv2.launcher

/** 渲染层使用的固定字体像素尺寸。UI 组件按需直接引用，不再通过全局设置项控制。 */
enum class PixelFontSize(val px: Int) {
    PX_8(8),
    PX_10(10),
    PX_12(12),
}

object PixelFontCatalog {

    val defaultUiFontSize: PixelFontSize = PixelFontSize.PX_10

    fun fontSizeOptions(): List<PixelFontSize> = PixelFontSize.entries

    fun sizeLabel(size: PixelFontSize): String = "${size.px}PX"
}
