package com.purride.pixelui.internal

/**
 * legacy 文本之外其余 support 的装配结果。
 *
 * 这层承接布局、视口、节点运行时三段装配结果，
 * 让默认 factory 可以按“文本 support”和“其余 support”两段装配。
 */
internal data class LegacyStructureSupportAssembly(
    val layoutSupportAssembly: LegacyLayoutSupportAssembly,
    val viewportSupportAssembly: LegacyViewportSupportAssembly,
    val nodeSupportAssembly: LegacyNodeSupportAssembly,
) {
    /**
     * 对外暴露布局渲染 support，兼容当前调用侧。
     */
    val layoutRenderSupport: PixelLayoutRenderSupport
        get() = layoutSupportAssembly.layoutRenderSupport

    /**
     * 对外暴露布局测量 support，兼容当前调用侧。
     */
    val layoutMeasureSupport: PixelLayoutMeasureSupport
        get() = layoutSupportAssembly.layoutMeasureSupport

    /**
     * 对外暴露 viewport 渲染 support，兼容当前调用侧。
     */
    val viewportRenderSupport: PixelViewportRenderSupport
        get() = viewportSupportAssembly.viewportRenderSupport

    /**
     * 对外暴露节点测量 support，兼容当前调用侧。
     */
    val measureSupport: PixelMeasureSupport
        get() = nodeSupportAssembly.measureSupport

    /**
     * 对外暴露节点渲染 support，兼容当前调用侧。
     */
    val nodeRenderSupport: PixelNodeRenderSupport
        get() = nodeSupportAssembly.nodeRenderSupport

    /**
     * 对外暴露根渲染 support，兼容当前调用侧。
     */
    val rootRenderSupport: PixelRootRenderSupport
        get() = nodeSupportAssembly.rootRenderSupport
}
