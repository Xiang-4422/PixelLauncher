package com.purride.pixelui.internal

/**
 * retained element tree 的统一渲染入口。
 *
 * 当前默认实现直接消费 retained element tree 上的 render object root，
 * 不再提供其他后端切换。
 */
internal fun interface ElementTreeRenderer {
    /**
     * 渲染已经解析好的 retained element tree 请求。
     */
    fun render(request: ElementTreeRenderRequest): PixelRenderResult

    /**
     * 释放 renderer 内部资源，例如缓存的渲染结果和 buffer 池条目。
     * 默认空实现，便于现有 SAM 实现继续编译。
     */
    fun dispose() = Unit
}
