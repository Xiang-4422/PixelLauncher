package com.purride.pixelui.internal

/**
 * legacy 文本相关 support 的装配结果。
 *
 * 这层把文本和文本输入 support 对齐成一个小的装配单元，
 * 让默认 factory 不必在一个方法里同时背所有 support 的创建细节。
 */
internal data class LegacyTextSupportAssembly(
    val textRenderSupport: PixelTextRenderSupport,
    val textFieldRenderSupport: PixelTextFieldRenderSupport,
)
