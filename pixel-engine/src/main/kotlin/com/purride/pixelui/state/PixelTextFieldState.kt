package com.purride.pixelui.state

/**
 * 通用文本输入状态。
 *
 * 第一版先只收敛最关键的几项：
 * 1. 当前文本
 * 2. 当前选区
 * 3. 当前是否聚焦
 *
 * 暂时不做组合输入、选择手柄和光标闪烁节拍，这些后续可以继续扩展。
 */
public class PixelTextFieldState(
    initialText: String = "",
    selectionStart: Int = initialText.length,
    selectionEnd: Int = selectionStart,
) {
    public var text: String = initialText
        internal set

    public var selectionStart: Int = selectionStart.coerceIn(0, initialText.length)
        internal set

    public var selectionEnd: Int = selectionEnd.coerceIn(this.selectionStart, initialText.length)
        internal set

    public var isFocused: Boolean = false
        internal set

    /**
     * IME composition 区段。-1 表示当前没有 composing 文本（IME 已提交所有按键）。
     *
     * 范围对应 [text] 的字符 index；end 是 exclusive。
     * 由宿主侧（PixelTextInputBridge / 测试）通过 [PixelTextFieldController.updateComposition]
     * 写入；RenderSurface 在聚焦且区段非空时绘制下划线。
     */
    public var compositionStart: Int = -1
        internal set

    public var compositionEnd: Int = -1
        internal set

    internal var focusRequested: Boolean = false
    internal var blurRequested: Boolean = false
    internal var autofocusConsumed: Boolean = false
}
