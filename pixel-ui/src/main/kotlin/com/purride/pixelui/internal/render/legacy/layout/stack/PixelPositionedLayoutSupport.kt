package com.purride.pixelui.internal

import com.purride.pixelui.internal.legacy.PixelPositionedNode

/**
 * 负责 legacy positioned 子节点的尺寸与坐标计算。
 */
internal class PixelPositionedLayoutSupport {
    /**
     * 计算 positioned 子项可用的最大宽度。
     */
    fun maxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null -> (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> constraints.maxWidth
        }
    }

    /**
     * 计算 positioned 子项可用的最大高度。
     */
    fun maxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null -> (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> constraints.maxHeight
        }
    }

    /**
     * 计算 positioned 子项最终宽度。
     */
    fun width(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null -> (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> childSize.width
        }
    }

    /**
     * 计算 positioned 子项最终高度。
     */
    fun height(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null -> (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> childSize.height
        }
    }
}
