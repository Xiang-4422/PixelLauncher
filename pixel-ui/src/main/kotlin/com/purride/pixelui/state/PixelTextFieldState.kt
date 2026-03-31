package com.purride.pixelui.state

/**
 * 通用文本输入状态。
 *
 * 第一版输入法仍通过宿主桥接，所以这里先保留文本和选区，
 * 后续再补光标闪烁、组合输入和选择手柄等更复杂状态。
 */
data class PixelTextFieldState(
    val text: String = "",
    val selectionStart: Int = text.length,
    val selectionEnd: Int = selectionStart,
)
