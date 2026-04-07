package com.purride.pixelui.internal

/**
 * legacy support 内部 wiring 的装配结果。
 *
 * 这层把 text 和 structure 两段装配结果收拢到一起，
 * 同时通过兼容 getter 继续向 bundle 暴露稳定的 support 入口。
 */
internal data class LegacySupportAssembly(
    val textSupportAssembly: LegacyTextSupportAssembly,
    val structureSupportAssembly: LegacyStructureSupportAssembly,
) {
    /**
     * 对外暴露文本渲染 support，兼容当前调用侧。
     */
    val textRenderSupport: PixelTextRenderSupport
        get() = textSupportAssembly.textRenderSupport

    /**
     * 对外暴露文本输入渲染 support，兼容当前调用侧。
     */
    val textFieldRenderSupport: PixelTextFieldRenderSupport
        get() = textSupportAssembly.textFieldRenderSupport

    /**
     * 对外暴露布局渲染 support，兼容当前调用侧。
     */
    val layoutRenderSupport: PixelLayoutRenderSupport
        get() = structureSupportAssembly.layoutRenderSupport

    /**
     * 对外暴露 viewport 渲染 support，兼容当前调用侧。
     */
    val viewportRenderSupport: PixelViewportRenderSupport
        get() = structureSupportAssembly.viewportRenderSupport

    /**
     * 对外暴露节点测量 support，兼容当前调用侧。
     */
    val measureSupport: PixelMeasureSupport
        get() = structureSupportAssembly.measureSupport

    /**
     * 对外暴露节点渲染 support，兼容当前调用侧。
     */
    val nodeRenderSupport: PixelNodeRenderSupport
        get() = structureSupportAssembly.nodeRenderSupport

    /**
     * 对外暴露根渲染 support，兼容当前调用侧。
     */
    val rootRenderSupport: PixelRootRenderSupport
        get() = structureSupportAssembly.rootRenderSupport
}
