package com.purride.pixelui.internal

import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode

internal class RenderSemantics(
    child: RenderBox? = null,
    private var label: String,
    private var role: PixelSemanticRole,
    private var enabled: Boolean,
    private var focused: Boolean,
) : SingleChildRenderObject() {
    init {
        setRenderObjectChild(child)
    }

    fun updateSemantics(label: String, role: PixelSemanticRole, enabled: Boolean, focused: Boolean) {
        if (this.label == label && this.role == role && this.enabled == enabled && this.focused == focused) return
        this.label = label
        this.role = role
        this.enabled = enabled
        this.focused = focused
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        renderChild?.layout(constraints)
        size = renderChild?.size ?: RenderSize(constraints.maxWidth, constraints.maxHeight)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        renderChild?.paint(context, offsetX, offsetY)
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) = renderChild?.collectClickTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) = renderChild?.collectPagerTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) = renderChild?.collectListTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) = renderChild?.collectScrollbarTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) = renderChild?.collectTextInputTargets(offsetX, offsetY, targets) ?: Unit
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) = renderChild?.collectSliderTargets(offsetX, offsetY, targets) ?: Unit

    override fun collectSemantics(offsetX: Int, offsetY: Int, nodes: MutableList<PixelSemanticsNode>) {
        nodes += PixelSemanticsNode(
            label = label,
            role = role,
            enabled = enabled,
            focused = focused,
            left = offsetX,
            top = offsetY,
            width = size.width,
            height = size.height,
        )
        renderChild?.collectSemantics(offsetX, offsetY, nodes)
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
