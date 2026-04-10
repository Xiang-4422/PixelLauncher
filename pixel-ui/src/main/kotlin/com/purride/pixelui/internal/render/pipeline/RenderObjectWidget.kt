package com.purride.pixelui.internal

import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.Widget

/**
 * Flutter 风格 render object widget 的内部基础协议。
 *
 * 它是未来长期主线里 `Widget -> Element -> RenderObject` 的关键连接点：
 * - Widget 只保存不可变配置
 * - Element 保存运行时生命周期
 * - RenderObject 负责 layout / paint / hitTest
 */
internal abstract class RenderObjectWidget(
    final override val key: Any? = null,
) : Widget {
    /**
     * 为当前 widget 创建对应的 render object。
     */
    abstract fun createRenderObject(context: InternalBuildContext): RenderObject

    /**
     * 用新的 widget 配置更新既有 render object。
     */
    open fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) = Unit
}

/**
 * 单个 render object widget 对应的 retained element。
 */
internal class RenderObjectElement(
    widget: RenderObjectWidget,
) : Element(widget) {
    private lateinit var renderObject: RenderObject

    /**
     * 挂载时创建 render object，并把后续配置更新交给 rebuild 阶段。
     */
    override fun mount(
        parent: Element?,
        owner: BuildOwner,
    ) {
        super.mount(parent = parent, owner = owner)
        renderObject = (widget as RenderObjectWidget).createRenderObject(this)
    }

    /**
     * 返回当前 element 持有的 render object。
     */
    override fun findRenderObject(): RenderObject {
        return renderObject
    }

    /**
     * 对 render object element 来说，rebuild 意味着同步 widget 配置到 render object。
     */
    override fun performRebuild() {
        (widget as RenderObjectWidget).updateRenderObject(
            context = this,
            renderObject = renderObject,
        )
    }

    /**
     * 卸载 element 时同步释放 render object 生命周期。
     */
    override fun onUnmount() {
        if (::renderObject.isInitialized) {
            renderObject.detach()
        }
    }
}
