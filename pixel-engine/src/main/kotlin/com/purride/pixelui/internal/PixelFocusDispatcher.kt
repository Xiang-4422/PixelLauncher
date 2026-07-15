package com.purride.pixelui.internal

import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent

/**
 * Android Host 与 runtime 之间最小化的焦点输入分发 SPI。
 *
 * 具体焦点树所有权继续封装在 runtime 内部，平台适配层只能提交规范化后的按键和文本事件。
 */
public interface PixelFocusDispatcher {
    /** 将一个规范化按键事件分发到当前焦点链。 */
    public fun dispatchKeyEvent(event: PixelKeyEvent): Boolean

    /** 将完整 Unicode 文本输入分发到当前焦点链。 */
    public fun dispatchTextInputEvent(event: PixelTextInputEvent): Boolean
}
