package com.purride.pixelui.internal

/**
 * retained element tree 的统一渲染入口。
 *
 * 当前运行时会在这里做整树级分流：
 * - 完全受支持的树走新 pipeline renderer
 * - 其余树回退到 bridge + legacy renderer
 *
 * retained runtime 只依赖这个接口，不直接依赖后续具体渲染后端。
 */
internal fun interface ElementTreeRenderer {
    /**
     * 渲染已经解析好的 retained element tree 请求。
     */
    fun render(request: ElementTreeRenderRequest): PixelRenderResult
}
