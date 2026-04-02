package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelFillMaxHeightElement
import com.purride.pixelui.internal.legacy.PixelFillMaxWidthElement
import com.purride.pixelui.internal.legacy.PixelMainAxisSize
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelModifierElement
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelPaddingElement
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelPositionedNode
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSizeElement
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode
import com.purride.pixelui.internal.legacy.toSurfaceNode
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import kotlin.math.max

internal data class PixelSize(
    val width: Int,
    val height: Int,
)

internal data class PixelRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
) {
    val right: Int
        get() = left + width

    val bottom: Int
        get() = top + height

    fun contains(x: Int, y: Int): Boolean {
        return x in left until right && y in top until bottom
    }

    fun inset(paddingLeft: Int, paddingTop: Int, paddingRight: Int, paddingBottom: Int): PixelRect {
        val nextLeft = (left + paddingLeft).coerceAtMost(right)
        val nextTop = (top + paddingTop).coerceAtMost(bottom)
        val nextRight = (right - paddingRight).coerceAtLeast(nextLeft)
        val nextBottom = (bottom - paddingBottom).coerceAtLeast(nextTop)
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }

    fun translate(deltaX: Int, deltaY: Int): PixelRect {
        return PixelRect(
            left = left + deltaX,
            top = top + deltaY,
            width = width,
            height = height,
        )
    }

    fun intersect(other: PixelRect): PixelRect? {
        val nextLeft = max(left, other.left)
        val nextTop = max(top, other.top)
        val nextRight = minOf(right, other.right)
        val nextBottom = minOf(bottom, other.bottom)
        if (nextRight <= nextLeft || nextBottom <= nextTop) {
            return null
        }
        return PixelRect(
            left = nextLeft,
            top = nextTop,
            width = nextRight - nextLeft,
            height = nextBottom - nextTop,
        )
    }
}

internal data class PixelConstraints(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    fun shrink(
        paddingLeft: Int,
        paddingTop: Int,
        paddingRight: Int,
        paddingBottom: Int,
    ): PixelConstraints {
        return PixelConstraints(
            maxWidth = (maxWidth - paddingLeft - paddingRight).coerceAtLeast(0),
            maxHeight = (maxHeight - paddingTop - paddingBottom).coerceAtLeast(0),
        )
    }
}

internal data class PixelClickTarget(
    val bounds: PixelRect,
    val onClick: () -> Unit,
)

internal data class PixelPagerTarget(
    val bounds: PixelRect,
    val axis: PixelAxis,
    val state: PixelPagerState,
    val controller: PixelPagerController,
    val onPageChanged: ((Int) -> Unit)?,
)

internal data class PixelListTarget(
    val bounds: PixelRect,
    val viewportHeightPx: Int,
    val contentHeightPx: Int,
    val state: com.purride.pixelui.state.PixelListState,
    val controller: com.purride.pixelui.state.PixelListController,
)

internal data class PixelTextInputTarget(
    val bounds: PixelRect,
    val state: com.purride.pixelui.state.PixelTextFieldState,
    val controller: com.purride.pixelui.state.PixelTextFieldController,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val action: PixelTextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
)

internal data class PixelRenderResult(
    val buffer: PixelBuffer,
    val clickTargets: List<PixelClickTarget>,
    val pagerTargets: List<PixelPagerTarget>,
    val listTargets: List<PixelListTarget>,
    val textInputTargets: List<PixelTextInputTarget>,
)

internal data class PixelModifierInfo(
    val paddingLeft: Int = 0,
    val paddingTop: Int = 0,
    val paddingRight: Int = 0,
    val paddingBottom: Int = 0,
    val fixedWidth: Int? = null,
    val fixedHeight: Int? = null,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

internal class PixelRenderRuntime(
    private val textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    private val textRenderSupport = PixelTextRenderSupport(defaultTextRasterizer = textRasterizer)
    private val textFieldRenderSupport = PixelTextFieldRenderSupport(
        defaultTextRasterizer = textRasterizer,
        textRenderSupport = textRenderSupport,
    )
    private val layoutRenderSupport = PixelLayoutRenderSupport(
        measureNode = ::measure,
        renderNode = ::renderNode,
    )
    private val viewportRenderSupport = PixelViewportRenderSupport(
        measureNode = ::measure,
        renderNode = ::renderNode,
        scrollAxisUnboundedMax = SCROLL_AXIS_UNBOUNDED_MAX,
    )

    companion object {
        /**
         * 纵向滚动容器在滚动轴上的“近似无界”测量上限。
         *
         * 第一版还没有真正的无界约束模型，所以先用一个足够大的逻辑像素值，
         * 让单子节点滚动容器可以测出比视口更高的自然内容高度。
         */
        private const val SCROLL_AXIS_UNBOUNDED_MAX = 4096
    }
    fun render(
        root: LegacyRenderNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val buffer = PixelBuffer(width = logicalWidth, height = logicalHeight)
        buffer.clear()
        val clickTargets = mutableListOf<PixelClickTarget>()
        val pagerTargets = mutableListOf<PixelPagerTarget>()
        val listTargets = mutableListOf<PixelListTarget>()
        val textInputTargets = mutableListOf<PixelTextInputTarget>()
        val rootConstraints = PixelConstraints(
            maxWidth = logicalWidth,
            maxHeight = logicalHeight,
        )
        val measuredRoot = measure(root, rootConstraints)
        val rootBounds = PixelRect(
            left = 0,
            top = 0,
            width = measuredRoot.width.coerceAtMost(logicalWidth),
            height = measuredRoot.height.coerceAtMost(logicalHeight),
        )
        renderNode(
            node = root,
            bounds = rootBounds,
            constraints = rootConstraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
        return PixelRenderResult(
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

    private fun measure(node: LegacyRenderNode, constraints: PixelConstraints): PixelSize {
        val modifierInfo = modifierInfo(node.modifier)
        val innerConstraints = constraints.shrink(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )

        val contentSize = when (node) {
            is PixelTextNode -> layoutText(
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
                val childSize = node.child?.let { child -> measure(child, innerConstraints) } ?: PixelSize(width = 0, height = 0)
                PixelSize(
                    width = childSize.width + (node.padding * 2),
                    height = childSize.height + (node.padding * 2),
                )
            }

            is PixelButtonNode -> measure(
                node = node.toSurfaceNode(),
                constraints = constraints,
            )

            is PixelBoxNode -> {
                val children = node.children.map { child -> measure(child, innerConstraints) }
                PixelSize(
                    width = children.maxOfOrNull { it.width } ?: 0,
                    height = children.maxOfOrNull { it.height } ?: 0,
                )
            }

            is PixelPositionedNode -> {
                val childConstraints = PixelConstraints(
                    maxWidth = layoutRenderSupport.measurePositionedMaxWidth(node, innerConstraints),
                    maxHeight = layoutRenderSupport.measurePositionedMaxHeight(node, innerConstraints),
                )
                val childSize = measure(node.child, childConstraints)
                PixelSize(
                    width = layoutRenderSupport.measurePositionedWidth(node, innerConstraints, childSize),
                    height = layoutRenderSupport.measurePositionedHeight(node, innerConstraints, childSize),
                )
            }

            is PixelRowNode -> {
                val children = layoutRenderSupport.measureRowChildrenForLayout(node, innerConstraints)
                val childrenWidth = if (node.children.any { layoutRenderSupport.childWeightOf(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
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
                val children = layoutRenderSupport.measureColumnChildrenForLayout(node, innerConstraints)
                val childrenHeight = if (node.children.any { layoutRenderSupport.childWeightOf(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
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

            is PixelTextFieldNode -> {
                textFieldRenderSupport.measure(node)
            }

            is CustomDraw -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

            else -> PixelSize(width = innerConstraints.maxWidth, height = innerConstraints.maxHeight)
        }

        val naturalWidth = contentSize.width + modifierInfo.paddingLeft + modifierInfo.paddingRight
        val naturalHeight = contentSize.height + modifierInfo.paddingTop + modifierInfo.paddingBottom
        val measuredWidth = modifierInfo.fixedWidth
            ?: if (modifierInfo.fillMaxWidth) constraints.maxWidth else naturalWidth
        val measuredHeight = modifierInfo.fixedHeight
            ?: if (modifierInfo.fillMaxHeight) constraints.maxHeight else naturalHeight

        return PixelSize(
            width = measuredWidth.coerceIn(0, constraints.maxWidth),
            height = measuredHeight.coerceIn(0, constraints.maxHeight),
        )
    }

    private fun renderNode(
        node: LegacyRenderNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val modifierInfo = modifierInfo(node.modifier)
        modifierInfo.onClick?.let { onClick ->
            clickTargets += PixelClickTarget(bounds = bounds, onClick = onClick)
        }
        val paddedBounds = bounds.inset(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )
        val innerConstraints = constraints.shrink(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )

        when (node) {
            is PixelTextNode -> textRenderSupport.renderText(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
            )

            is PixelSurfaceNode -> layoutRenderSupport.renderSurface(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelButtonNode -> renderNode(
                node = node.toSurfaceNode(),
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelBoxNode -> layoutRenderSupport.renderBox(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelPositionedNode -> Unit

            is PixelRowNode -> layoutRenderSupport.renderRow(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelColumnNode -> layoutRenderSupport.renderColumn(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelPagerNode -> viewportRenderSupport.renderPager(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelListNode -> viewportRenderSupport.renderList(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelSingleChildScrollViewNode -> viewportRenderSupport.renderSingleChildScrollView(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelTextFieldNode -> textFieldRenderSupport.render(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                textInputTargets = textInputTargets,
            )

            is CustomDraw -> Unit
        }
    }

    private fun layoutText(
        node: PixelTextNode,
        maxWidth: Int,
    ): PixelTextLayout {
        return textRenderSupport.layoutText(
            node = node,
            maxWidth = maxWidth,
        )
    }

    private fun modifierInfo(modifier: PixelModifier): PixelModifierInfo {
        var paddingLeft = 0
        var paddingTop = 0
        var paddingRight = 0
        var paddingBottom = 0
        var fixedWidth: Int? = null
        var fixedHeight: Int? = null
        var fillMaxWidth = false
        var fillMaxHeight = false
        var onClick: (() -> Unit)? = null

        modifier.elements.forEach { element ->
            when (element) {
                is PixelPaddingElement -> {
                    paddingLeft += element.left
                    paddingTop += element.top
                    paddingRight += element.right
                    paddingBottom += element.bottom
                }

                is PixelSizeElement -> {
                    fixedWidth = element.width ?: fixedWidth
                    fixedHeight = element.height ?: fixedHeight
                }

                is PixelFillMaxWidthElement -> fillMaxWidth = element.enabled
                is PixelFillMaxHeightElement -> fillMaxHeight = element.enabled
                is PixelClickableElement -> onClick = element.onClick
                else -> Unit
            }
        }

        return PixelModifierInfo(
            paddingLeft = paddingLeft,
            paddingTop = paddingTop,
            paddingRight = paddingRight,
            paddingBottom = paddingBottom,
            fixedWidth = fixedWidth,
            fixedHeight = fixedHeight,
            fillMaxWidth = fillMaxWidth,
            fillMaxHeight = fillMaxHeight,
            onClick = onClick,
        )
    }
}
