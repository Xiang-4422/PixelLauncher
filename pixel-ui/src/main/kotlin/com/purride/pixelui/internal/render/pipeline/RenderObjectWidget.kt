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
    override val key: Any? = null,
) : Widget {
    /**
     * 为当前 widget 创建对应的 retained element。
     */
    open fun createElement(): Element {
        return RenderObjectElement(this)
    }

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
internal open class RenderObjectElement(
    widget: RenderObjectWidget,
) : Element(widget) {
    protected lateinit var renderObject: RenderObject

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

/**
 * Flutter 风格单 child render object widget 的内部基础协议。
 */
internal abstract class SingleChildRenderObjectWidget(
    val child: Widget?,
    key: Any? = null,
) : RenderObjectWidget(key = key) {
    /**
     * 创建单 child render object element。
     */
    override fun createElement(): RenderObjectElement {
        return SingleChildRenderObjectElement(this)
    }
}

/**
 * 单 child render object widget 对应的 retained element。
 */
internal class SingleChildRenderObjectElement(
    widget: SingleChildRenderObjectWidget,
) : RenderObjectElement(widget) {
    private var child: Element? = null

    /**
     * 同步 widget 配置，并更新唯一子 element 与 render object 子节点。
     */
    override fun performRebuild() {
        super.performRebuild()
        child = owner.updateChild(
            parent = this,
            current = child,
            newWidget = (widget as SingleChildRenderObjectWidget).child,
        )
        syncRenderObjectChild()
    }

    /**
     * 遍历唯一子 element。
     */
    override fun visitChildren(visitor: (Element) -> Unit) {
        child?.let(visitor)
    }

    /**
     * 把 child element 找到的 render object 挂接到当前 render object。
     */
    private fun syncRenderObjectChild() {
        val renderObjectWithChild = renderObject as? RenderObjectWithChild
            ?: error("SingleChildRenderObjectWidget 必须创建 RenderObjectWithChild。")
        renderObjectWithChild.setRenderObjectChild(child?.findRenderObject())
    }
}

/**
 * Flutter 风格多 child render object widget 的内部基础协议。
 */
internal abstract class MultiChildRenderObjectWidget(
    val children: List<Widget>,
    key: Any? = null,
) : RenderObjectWidget(key = key) {
    /**
     * 创建多 child render object element。
     */
    override fun createElement(): RenderObjectElement {
        return MultiChildRenderObjectElement(this)
    }
}

/**
 * 多 child render object widget 对应的 retained element。
 */
internal class MultiChildRenderObjectElement(
    widget: MultiChildRenderObjectWidget,
) : RenderObjectElement(widget) {
    private val childSlot = MultiChildElementSlot()

    /**
     * 同步 widget 配置，并更新多个 child elements 与 render object children。
     */
    override fun performRebuild() {
        super.performRebuild()
        childSlot.update(
            owner = owner,
            parent = this,
            newWidgets = (widget as MultiChildRenderObjectWidget).children,
        )
        syncRenderObjectChildren()
    }

    /**
     * 遍历多个 child elements。
     */
    override fun visitChildren(visitor: (Element) -> Unit) {
        childSlot.visit(visitor)
    }

    /**
     * 把 child elements 找到的 render objects 挂接到当前 render object。
     */
    private fun syncRenderObjectChildren() {
        val renderObjectWithChildren = renderObject as? RenderObjectWithChildren
            ?: error("MultiChildRenderObjectWidget 必须创建 RenderObjectWithChildren。")
        renderObjectWithChildren.setRenderObjectChildren(
            children = childSlot.elements.mapNotNull(Element::findRenderObject),
        )
    }
}
