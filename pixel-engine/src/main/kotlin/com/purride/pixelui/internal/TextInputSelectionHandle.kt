package com.purride.pixelui

import com.purride.pixelui.internal.PixelArtifactInternalApi

/**
 * 文本选区拖动手柄的逻辑端点。
 *
 * 该类型由 runtime 的选区手势和 testing artifact 共同使用，不依赖 Android 触摸事件实现。
 */
@PixelArtifactInternalApi
public enum class TextInputSelectionHandle {
    /** 选区规范化后的起始端点。 */
    START,

    /** 选区规范化后的结束端点。 */
    END,
}
