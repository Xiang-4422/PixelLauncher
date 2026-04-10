package com.purride.pixelui.internal

import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelFillMaxHeightElement
import com.purride.pixelui.internal.legacy.PixelFillMaxWidthElement
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisSize
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPaddingElement
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSizeElement
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextNode

/**
 * 显式维护“当前 bridge tree 是否可以整树走新 pipeline”的能力判断。
 *
 * 第一阶段我们不做 mixed subtree 渲染，因此判断结果只有两种：
 * - 整棵树全部支持，允许进入 pipeline
 * - 任意节点不支持，整棵树回退到现有 bridge + legacy
 */
internal object PipelineTreeCapabilityChecker {
    /**
     * 判断当前 bridge 根节点是否能够完整降到新 pipeline。
     */
    fun canLower(root: BridgeRenderNode): Boolean {
        return inspect(root).supported
    }

    /**
     * 返回当前 bridge 根节点的能力检查结果。
     */
    fun inspect(root: BridgeRenderNode): PipelineCapabilityReport {
        return inspectNode(root)
    }

    /**
     * 递归判断单个兼容节点是否在首批支持范围内。
     */
    private fun inspectNode(node: LegacyRenderNode): PipelineCapabilityReport {
        return when (node) {
            is PixelTextNode -> inspectText(node)
            is PixelSurfaceNode -> inspectSurface(node)
            is PixelBoxNode -> inspectBox(node)
            is PixelRowNode -> inspectRow(node)
            is PixelColumnNode -> inspectColumn(node)
            else -> PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_NODE_TYPE)
        }
    }

    /**
     * 单行、clip 溢出文本可直接进入第一版 pipeline。
     */
    private fun inspectText(node: PixelTextNode): PipelineCapabilityReport {
        if (node.softWrap || node.maxLines != 1 || node.overflow != PixelTextOverflow.CLIP || '\n' in node.text) {
            return PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_TEXT_LAYOUT)
        }
        return inspectModifier(node.modifier)
    }

    /**
     * Surface 仅在自身 modifier 和子节点都可识别时进入 pipeline。
     */
    private fun inspectSurface(node: PixelSurfaceNode): PipelineCapabilityReport {
        val modifierReport = inspectModifier(node.modifier)
        if (!modifierReport.supported) {
            return modifierReport
        }
        return node.child?.let(::inspectNode) ?: PipelineCapabilityReport.supported()
    }

    /**
     * Box 目前只接单 child 壳，多个子节点仍然整树回退。
     */
    private fun inspectBox(node: PixelBoxNode): PipelineCapabilityReport {
        if (node.children.size > 1) {
            return PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_MULTI_CHILD_BOX)
        }
        val modifierReport = inspectModifier(node.modifier)
        if (!modifierReport.supported) {
            return modifierReport
        }
        return inspectChildren(node.children)
    }

    /**
     * Row 目前只接基础排布，不接权重和更复杂语义。
     */
    private fun inspectRow(node: PixelRowNode): PipelineCapabilityReport {
        val flexReport = inspectFlex(
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
        )
        if (!flexReport.supported) {
            return flexReport
        }
        val modifierReport = inspectModifier(node.modifier)
        if (!modifierReport.supported) {
            return modifierReport
        }
        return inspectChildren(node.children)
    }

    /**
     * Column 目前和 Row 共享同一组基础排布能力边界。
     */
    private fun inspectColumn(node: PixelColumnNode): PipelineCapabilityReport {
        val flexReport = inspectFlex(
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
        )
        if (!flexReport.supported) {
            return flexReport
        }
        val modifierReport = inspectModifier(node.modifier)
        if (!modifierReport.supported) {
            return modifierReport
        }
        return inspectChildren(node.children)
    }

    /**
     * 第一版只接受明确支持的 modifier 子集。
     */
    private fun inspectModifier(modifier: PixelModifier): PipelineCapabilityReport {
        val supported = modifier.elements.all { element ->
            element is PixelPaddingElement ||
                element is PixelSizeElement ||
                element is PixelFillMaxWidthElement ||
                element is PixelFillMaxHeightElement ||
                element is PixelClickableElement
        }
        return if (supported) {
            PipelineCapabilityReport.supported()
        } else {
            PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_MODIFIER)
        }
    }

    /**
     * 第一版 flex 仅接基础主轴/交叉轴排布，不接权重与更复杂布局语义。
     */
    private fun inspectFlex(
        mainAxisSize: PixelMainAxisSize,
        mainAxisAlignment: PixelMainAxisAlignment,
        crossAxisAlignment: PixelCrossAxisAlignment,
    ): PipelineCapabilityReport {
        val supported = mainAxisSize in setOf(PixelMainAxisSize.MIN, PixelMainAxisSize.MAX) &&
            mainAxisAlignment in setOf(
                PixelMainAxisAlignment.START,
                PixelMainAxisAlignment.CENTER,
                PixelMainAxisAlignment.END,
                PixelMainAxisAlignment.SPACE_BETWEEN,
                PixelMainAxisAlignment.SPACE_AROUND,
                PixelMainAxisAlignment.SPACE_EVENLY,
            ) &&
            crossAxisAlignment in setOf(
                PixelCrossAxisAlignment.START,
                PixelCrossAxisAlignment.CENTER,
                PixelCrossAxisAlignment.END,
                PixelCrossAxisAlignment.STRETCH,
            )
        return if (supported) {
            PipelineCapabilityReport.supported()
        } else {
            PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_FLEX_LAYOUT)
        }
    }

    /**
     * 逐个检查子节点，返回第一个不支持原因。
     */
    private fun inspectChildren(children: List<LegacyRenderNode>): PipelineCapabilityReport {
        children.forEach { child ->
            val report = inspectNode(child)
            if (!report.supported) {
                return report
            }
        }
        return PipelineCapabilityReport.supported()
    }
}

/**
 * 描述一棵兼容节点树能否整树走新 pipeline 的结果。
 */
internal data class PipelineCapabilityReport(
    val supported: Boolean,
    val reason: PipelineUnsupportedReason? = null,
) {
    companion object {
        fun supported(): PipelineCapabilityReport {
            return PipelineCapabilityReport(supported = true)
        }

        fun unsupported(reason: PipelineUnsupportedReason): PipelineCapabilityReport {
            return PipelineCapabilityReport(
                supported = false,
                reason = reason,
            )
        }
    }
}

/**
 * 当前整树回退到旧渲染链的主要原因分类。
 */
internal enum class PipelineUnsupportedReason {
    UNSUPPORTED_NODE_TYPE,
    UNSUPPORTED_TEXT_LAYOUT,
    UNSUPPORTED_MODIFIER,
    UNSUPPORTED_FLEX_LAYOUT,
    UNSUPPORTED_MULTI_CHILD_BOX,
}
