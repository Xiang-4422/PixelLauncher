package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelMainAxisSize
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode
import com.purride.pixelui.internal.legacy.toSurfaceNode
import kotlin.math.max

/**
 * 负责把 legacy 节点按类型分发到对应的测量逻辑。
 */
internal class PixelMeasureDispatch(
    private val measureNode: (LegacyRenderNode, PixelConstraints) -> PixelSize,
    private val textRenderSupport: PixelTextRenderSupport,
    private val textFieldRenderSupport: PixelTextFieldRenderSupport,
    private val layoutMeasureSupport: PixelLayoutMeasureSupport,
) {
    /**
     * 按节点类型测量内容尺寸，不包含外层 modifier padding/fill 处理。
     */
    fun measureContent(
        node: LegacyRenderNode,
        innerConstraints: PixelConstraints,
    ): PixelSize {
        return when (node) {
            is PixelTextNode -> textRenderSupport.layoutText(
                node = node,
                maxWidth = innerConstraints.maxWidth,
            ).let { layout ->
                val measuredWidth = if (
                    textRenderSupport.textAlignNeedsFullWidth(node) &&
                    innerConstraints.maxWidth > 0 &&
                    innerConstraints.maxWidth > layout.width
                ) {
                    innerConstraints.maxWidth
                } else {
                    layout.width
                }
                PixelSize(width = measuredWidth, height = layout.height)
            }

            is PixelSurfaceNode -> {
                val childSize = node.child?.let { child -> measureNode(child, innerConstraints) } ?: PixelSize(width = 0, height = 0)
                PixelSize(
                    width = childSize.width + (node.padding * 2),
                    height = childSize.height + (node.padding * 2),
                )
            }

            is PixelButtonNode -> measureNode(
                node.toSurfaceNode(),
                innerConstraints,
            )

            is PixelBoxNode -> {
                val children = node.children.map { child -> measureNode(child, innerConstraints) }
                PixelSize(
                    width = children.maxOfOrNull { it.width } ?: 0,
                    height = children.maxOfOrNull { it.height } ?: 0,
                )
            }

            is PixelPositionedNode -> {
                val childConstraints = PixelConstraints(
                    maxWidth = layoutMeasureSupport.measurePositionedMaxWidth(node, innerConstraints),
                    maxHeight = layoutMeasureSupport.measurePositionedMaxHeight(node, innerConstraints),
                )
                val childSize = measureNode(node.child, childConstraints)
                PixelSize(
                    width = layoutMeasureSupport.measurePositionedWidth(node, innerConstraints, childSize),
                    height = layoutMeasureSupport.measurePositionedHeight(node, innerConstraints, childSize),
                )
            }

            is PixelRowNode -> {
                val children = layoutMeasureSupport.measureRowChildren(node, innerConstraints)
                val childrenWidth = if (node.children.any { layoutMeasureSupport.childWeightOf(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
                    innerConstraints.maxWidth
                } else {
                    children.sumOf { it.width } + (max(0, children.size - 1) * node.spacing)
                }
                PixelSize(
                    width = childrenWidth,
                    height = children.maxOfOrNull { it.height } ?: 0,
                )
            }

            is PixelColumnNode -> {
                val children = layoutMeasureSupport.measureColumnChildren(node, innerConstraints)
                val childrenHeight = if (node.children.any { layoutMeasureSupport.childWeightOf(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
                    innerConstraints.maxHeight
                } else {
                    children.sumOf { it.height } + (max(0, children.size - 1) * node.spacing)
                }
                PixelSize(
                    width = children.maxOfOrNull { it.width } ?: 0,
                    height = childrenHeight,
                )
            }

            is PixelPagerNode -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

            is PixelListNode -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

            is PixelSingleChildScrollViewNode -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

            is PixelTextFieldNode -> textFieldRenderSupport.measure(node)

            is CustomDraw -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

            else -> PixelSize(width = innerConstraints.maxWidth, height = innerConstraints.maxHeight)
        }
    }
}
