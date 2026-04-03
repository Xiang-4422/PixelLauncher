package com.purride.pixelui.internal

/**
 * retained element tree 的统一渲染入口。
 *
 * 当前由 bridge 层实现，把 element tree 解析成 bridge tree 再交给 legacy renderer。
 * retained runtime 只依赖这个接口，不直接依赖 bridge graph 细节。
 */
internal fun interface ElementTreeRenderer {
    /**
     * 渲染已经解析好的 retained element tree 请求。
     */
    fun render(request: ElementTreeRenderRequest): PixelRenderResult
}
