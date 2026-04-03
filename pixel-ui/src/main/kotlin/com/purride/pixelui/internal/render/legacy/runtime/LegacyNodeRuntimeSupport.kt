package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer

/**
 * 负责 legacy runtime 内部节点级测量和渲染协作。
 *
 * 这层把 modifier-aware measure 和 node render delegation 从 bundle 本体里拿出来，
 * 让 bundle 更接近只负责装配和根级入口。
 */
internal class LegacyNodeRuntimeSupport {
    private var assembly: LegacySupportAssembly? = null

    /**
     * 绑定当前 runtime 使用的 support assembly。
     */
    fun bind(assembly: LegacySupportAssembly) {
        this.assembly = assembly
    }

    /**
     * 测量单个 legacy 节点。
     */
    fun measure(
        node: LegacyRenderNode,
        constraints: PixelConstraints,
    ): PixelSize {
        val boundAssembly = requireNotNull(assembly) {
            "LegacyNodeRuntimeSupport must be bound before measure() is used."
        }
        return boundAssembly.measureSupport.measure(
            node = node,
            constraints = constraints,
            modifierInfo = PixelModifierSupport.resolve(node.modifier),
        )
    }

    /**
     * 渲染单个 legacy 节点。
     */
    fun renderNode(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val boundAssembly = requireNotNull(assembly) {
            "LegacyNodeRuntimeSupport must be bound before renderNode() is used."
        }
        boundAssembly.nodeRenderSupport.render(
            node = node,
            bounds = bounds,
            constraints = constraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }
}
