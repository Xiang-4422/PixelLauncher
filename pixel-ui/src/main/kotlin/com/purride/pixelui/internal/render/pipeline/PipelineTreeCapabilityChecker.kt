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
        return canLowerNode(root)
    }

    /**
     * 递归判断单个兼容节点是否在首批支持范围内。
     */
    private fun canLowerNode(node: LegacyRenderNode): Boolean {
        return when (node) {
            is PixelTextNode -> supportsText(node)
            is PixelSurfaceNode -> supportsSurface(node)
            is PixelBoxNode -> supportsBox(node)
            is PixelRowNode -> supportsRow(node)
            is PixelColumnNode -> supportsColumn(node)
            else -> false
        }
    }

    /**
     * 单行、clip 溢出文本可直接进入第一版 pipeline。
     */
    private fun supportsText(node: PixelTextNode): Boolean {
        return !node.softWrap &&
            node.maxLines == 1 &&
            node.overflow == PixelTextOverflow.CLIP &&
            '\n' !in node.text &&
            supportsModifier(node.modifier)
    }

    /**
     * Surface 仅在自身 modifier 和子节点都可识别时进入 pipeline。
     */
    private fun supportsSurface(node: PixelSurfaceNode): Boolean {
        return supportsModifier(node.modifier) &&
            (node.child == null || canLowerNode(node.child))
    }

    /**
     * Box 目前只接单 child 壳，多个子节点仍然整树回退。
     */
    private fun supportsBox(node: PixelBoxNode): Boolean {
        return node.children.size <= 1 &&
            supportsModifier(node.modifier) &&
            node.children.all(::canLowerNode)
    }

    /**
     * Row 目前只接基础排布，不接权重和更复杂语义。
     */
    private fun supportsRow(node: PixelRowNode): Boolean {
        return supportsFlex(
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
        ) &&
            supportsModifier(node.modifier) &&
            node.children.all(::canLowerNode)
    }

    /**
     * Column 目前和 Row 共享同一组基础排布能力边界。
     */
    private fun supportsColumn(node: PixelColumnNode): Boolean {
        return supportsFlex(
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
        ) &&
            supportsModifier(node.modifier) &&
            node.children.all(::canLowerNode)
    }

    /**
     * 第一版只接受明确支持的 modifier 子集。
     */
    private fun supportsModifier(modifier: PixelModifier): Boolean {
        return modifier.elements.all { element ->
            element is PixelPaddingElement ||
                element is PixelSizeElement ||
                element is PixelFillMaxWidthElement ||
                element is PixelFillMaxHeightElement ||
                element is PixelClickableElement
        }
    }

    /**
     * 第一版 flex 仅接基础主轴/交叉轴排布，不接权重与更复杂布局语义。
     */
    private fun supportsFlex(
        mainAxisSize: PixelMainAxisSize,
        mainAxisAlignment: PixelMainAxisAlignment,
        crossAxisAlignment: PixelCrossAxisAlignment,
    ): Boolean {
        return mainAxisSize in setOf(PixelMainAxisSize.MIN, PixelMainAxisSize.MAX) &&
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
    }
}
