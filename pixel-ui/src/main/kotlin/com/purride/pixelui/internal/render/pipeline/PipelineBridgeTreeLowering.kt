package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelFillMaxHeightElement
import com.purride.pixelui.internal.legacy.PixelFillMaxWidthElement
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisSize
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPaddingElement
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSizeElement
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextNode

/**
 * 把当前 bridge/legacy 兼容节点树降到最小新渲染管线。
 *
 * 第一版只覆盖 `Text + Surface` 及其外围的单 child box 包装，
 * 目的是先证明新 pipeline 已经能承接真实页面的一条最小链路。
 */
internal class PipelineBridgeTreeLowering(
    private val defaultTextRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    /**
     * 尝试把 bridge 根节点降成新渲染树根。
     */
    fun lower(root: BridgeRenderNode): RenderBox? {
        return lowerNode(root)
    }

    /**
     * 递归降单个 bridge/legacy 节点。
     */
    private fun lowerNode(node: LegacyRenderNode): RenderBox? {
        return when (node) {
            is PixelTextNode -> lowerText(node)
            is PixelSurfaceNode -> lowerSurface(node)
            is PixelBoxNode -> lowerBox(node)
            is PixelRowNode -> lowerRow(node)
            is PixelColumnNode -> lowerColumn(node)
            else -> null
        }
    }

    /**
     * 把文本节点降成 `RenderText`。
     */
    private fun lowerText(node: PixelTextNode): RenderBox? {
        if (!PipelineTreeCapabilityChecker.canLower(node)) {
            return null
        }
        val modifierInfo = resolveSupportedModifierInfo(node.modifier) ?: return null
        return RenderText(
            text = node.text,
            style = node.style,
            textAlign = node.textAlign,
            textDirection = node.textDirection,
            defaultTextRasterizer = node.style.textRasterizer ?: defaultTextRasterizer,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            occupyFullWidth = needsFullWidthForAlignment(node),
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
        )
    }

    /**
     * 把表面节点降成 `RenderSurface`。
     */
    private fun lowerSurface(node: PixelSurfaceNode): RenderBox? {
        if (!PipelineTreeCapabilityChecker.canLower(node)) {
            return null
        }
        val modifierInfo = resolveSupportedModifierInfo(node.modifier) ?: return null
        val child = node.child?.let(::lowerNode) ?: null
        return RenderSurface(
            child = child,
            fillTone = node.fillTone,
            borderTone = node.borderTone,
            alignment = node.alignment,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            outerPaddingLeft = modifierInfo.paddingLeft,
            outerPaddingTop = modifierInfo.paddingTop,
            outerPaddingRight = modifierInfo.paddingRight,
            outerPaddingBottom = modifierInfo.paddingBottom,
            contentPaddingLeft = node.padding,
            contentPaddingTop = node.padding,
            contentPaddingRight = node.padding,
            contentPaddingBottom = node.padding,
            onClick = modifierInfo.onClick,
        )
    }

    /**
     * 把单 child box 节点降成不绘制背景的 `RenderSurface`。
     */
    private fun lowerBox(node: PixelBoxNode): RenderBox? {
        if (!PipelineTreeCapabilityChecker.canLower(node)) {
            return null
        }
        val modifierInfo = resolveSupportedModifierInfo(node.modifier) ?: return null
        val child = node.children.singleOrNull()?.let(::lowerNode)
        return RenderSurface(
            child = child,
            fillTone = null,
            borderTone = null,
            alignment = node.alignment,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            outerPaddingLeft = modifierInfo.paddingLeft,
            outerPaddingTop = modifierInfo.paddingTop,
            outerPaddingRight = modifierInfo.paddingRight,
            outerPaddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
        )
    }

    /**
     * 把首批可支持的 row 节点降成最小 flex 对象。
     */
    private fun lowerRow(node: PixelRowNode): RenderBox? {
        if (!PipelineTreeCapabilityChecker.canLower(node)) {
            return null
        }
        val modifierInfo = resolveSupportedModifierInfo(node.modifier) ?: return null
        val children = node.children.map { child -> lowerNode(child) ?: return null }
        return RenderFlex(
            direction = FlexDirection.HORIZONTAL,
            children = children,
            spacing = node.spacing,
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
        )
    }

    /**
     * 把首批可支持的 column 节点降成最小 flex 对象。
     */
    private fun lowerColumn(node: PixelColumnNode): RenderBox? {
        if (!PipelineTreeCapabilityChecker.canLower(node)) {
            return null
        }
        val modifierInfo = resolveSupportedModifierInfo(node.modifier) ?: return null
        val children = node.children.map { child -> lowerNode(child) ?: return null }
        return RenderFlex(
            direction = FlexDirection.VERTICAL,
            children = children,
            spacing = node.spacing,
            mainAxisSize = node.mainAxisSize,
            mainAxisAlignment = node.mainAxisAlignment,
            crossAxisAlignment = node.crossAxisAlignment,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
        )
    }

    /**
     * 只接受第一版新 pipeline 明确支持的 modifier 集合。
     */
    private fun resolveSupportedModifierInfo(modifier: PixelModifier): PixelModifierInfo? {
        val supported = modifier.elements.all { element ->
            element is PixelPaddingElement ||
                element is PixelSizeElement ||
                element is PixelFillMaxWidthElement ||
                element is PixelFillMaxHeightElement ||
                element is PixelClickableElement
        }
        if (!supported) {
            return null
        }
        return PixelModifierSupport.resolve(modifier)
    }

    /**
     * 对齐方式需要占满整行时，直接让新 pipeline 也输出整行宽度。
     */
    private fun needsFullWidthForAlignment(node: PixelTextNode): Boolean {
        return when (node.textAlign) {
            com.purride.pixelui.internal.legacy.PixelTextAlign.CENTER -> true
            com.purride.pixelui.internal.legacy.PixelTextAlign.START -> node.textDirection == com.purride.pixelui.TextDirection.RTL
            com.purride.pixelui.internal.legacy.PixelTextAlign.END -> node.textDirection == com.purride.pixelui.TextDirection.LTR
        }
    }
}
