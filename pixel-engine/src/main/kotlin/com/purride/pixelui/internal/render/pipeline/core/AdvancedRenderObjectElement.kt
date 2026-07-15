@file:OptIn(com.purride.pixelui.advanced.PixelExperimentalApi::class)

package com.purride.pixelui.internal

import com.purride.pixelui.advanced.PixelMultiChildRenderObjectWidget
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderObjectWidget
import com.purride.pixelui.advanced.PixelSingleChildRenderObjectWidget

/**
 * Internal retained Element that owns a consumer-defined advanced render object and its adapter.
 */
internal open class AdvancedRenderObjectElement(
    widget: PixelRenderObjectWidget,
) : Element(widget), DirectRenderObjectElement {
    /** 公开 `AdvancedRenderObjectElement` 的 `advancedRenderObject` 配置或运行值。
 *
 * Public consumer-defined object updated when the immutable Widget configuration changes.
 */
    protected lateinit var advancedRenderObject: PixelRenderObject

    /** 公开 `AdvancedRenderObjectElement` 的 `renderObjectAdapter` 配置或运行值。
 *
 * Internal pipeline adapter returned to retained-tree rendering and diagnostics.
 */
    protected lateinit var renderObjectAdapter: RenderObject

    /** Creates the public object and its internal pipeline adapter during initial mount. */
    override fun mount(parent: Element?, owner: BuildOwner) {
        super.mount(parent = parent, owner = owner)
        /** Strongly typed Widget active at mount time. */
        val advancedWidget = widget as PixelRenderObjectWidget
        advancedRenderObject = advancedWidget.createRenderObject(this)
        renderObjectAdapter = AdvancedRenderObjectAdapterFactory.create(
            widget = advancedWidget,
            advancedRenderObject = advancedRenderObject,
        )
    }

    /** Returns the internal adapter consumed by the retained render pipeline. */
    override fun findRenderObject(): RenderObject {
        return renderObjectAdapter
    }

    /** Synchronizes rebuilt immutable configuration into the retained public render object. */
    override fun performRebuild() {
        (widget as PixelRenderObjectWidget).updateRenderObject(
            context = this,
            renderObject = advancedRenderObject,
        )
    }

    /** Detaches the adapter and public object when this retained Element is unmounted. */
    override fun onUnmount() {
        if (::renderObjectAdapter.isInitialized) {
            renderObjectAdapter.detach()
        }
    }
}

/** Internal retained Element that reconciles one child for an advanced Widget. */
internal class AdvancedSingleChildRenderObjectElement(
    widget: PixelSingleChildRenderObjectWidget,
) : AdvancedRenderObjectElement(widget) {
    /** Current retained child Element, or null while the Widget has no child. */
    private var childElement: Element? = null

    /** Updates configuration, reconciles the child Element, and synchronizes the adapter protocol. */
    override fun performRebuild() {
        super.performRebuild()
        /** Current immutable advanced Widget after reconciliation. */
        val advancedWidget = widget as PixelSingleChildRenderObjectWidget
        childElement = owner.updateChild(
            parent = this,
            current = childElement,
            newWidget = advancedWidget.child,
        )
        synchronizeRenderObjectChild()
    }

    /** Visits the current direct child Element when present. */
    override fun visitChildren(visitor: (Element) -> Unit) {
        childElement?.let(visitor)
    }

    /** Releases the terminal advanced-child reference after its Element has unmounted. */
    override fun clearChildReferences() {
        childElement = null
    }

    /** Transfers the child's internal render object into the adapter's single-child protocol. */
    private fun synchronizeRenderObjectChild() {
        /** Adapter protocol guaranteed by [AdvancedRenderObjectAdapterFactory]. */
        val childProtocol = renderObjectAdapter as? RenderObjectWithChild
            ?: error("Advanced single-child adapter must implement RenderObjectWithChild.")
        childProtocol.setRenderObjectChild(childElement?.findRenderObject())
    }
}

/** Internal retained Element that reconciles ordered children for an advanced Widget. */
internal class AdvancedMultiChildRenderObjectElement(
    widget: PixelMultiChildRenderObjectWidget,
) : AdvancedRenderObjectElement(widget) {
    /** Slot that owns ordered retained child Elements across Widget rebuilds. */
    private val childSlot = MultiChildElementSlot()

    /** Updates configuration, reconciles child Elements, and synchronizes the adapter protocol. */
    override fun performRebuild() {
        super.performRebuild()
        /** Current immutable advanced Widget after reconciliation. */
        val advancedWidget = widget as PixelMultiChildRenderObjectWidget
        childSlot.update(
            owner = owner,
            parent = this,
            newWidgets = advancedWidget.children,
        )
        synchronizeRenderObjectChildren()
    }

    /** Visits all current direct child Elements in retained order. */
    override fun visitChildren(visitor: (Element) -> Unit) {
        childSlot.visit(visitor)
    }

    /** Releases all terminal advanced sibling references after their teardown completes. */
    override fun clearChildReferences() {
        childSlot.clearReferences()
    }

    /** Transfers internal child render objects into the adapter's ordered child protocol. */
    private fun synchronizeRenderObjectChildren() {
        /** Adapter protocol guaranteed by [AdvancedRenderObjectAdapterFactory]. */
        val childrenProtocol = renderObjectAdapter as? RenderObjectWithChildren
            ?: error("Advanced multi-child adapter must implement RenderObjectWithChildren.")
        childrenProtocol.setRenderObjectChildren(
            children = childSlot.elements.mapNotNull(Element::findRenderObject),
        )
    }
}
