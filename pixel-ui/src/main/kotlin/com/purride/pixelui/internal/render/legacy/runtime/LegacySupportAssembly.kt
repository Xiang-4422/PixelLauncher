package com.purride.pixelui.internal

/**
 * legacy support 内部 wiring 的装配结果。
 *
 * 这层把 text、text field、layout、viewport、measure、node render、root render
 * 这些协作对象对齐到一个 assembly，避免 bundle 初始化阶段继续分散构造细节。
 */
internal data class LegacySupportAssembly(
    val textRenderSupport: PixelTextRenderSupport,
    val textFieldRenderSupport: PixelTextFieldRenderSupport,
    val layoutRenderSupport: PixelLayoutRenderSupport,
    val viewportRenderSupport: PixelViewportRenderSupport,
    val measureSupport: PixelMeasureSupport,
    val nodeRenderSupport: PixelNodeRenderSupport,
    val rootRenderSupport: PixelRootRenderSupport,
)
