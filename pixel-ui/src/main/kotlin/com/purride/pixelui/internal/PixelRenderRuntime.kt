package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelBoxNode
import com.purride.pixelui.PixelClickableElement
import com.purride.pixelui.PixelColumnNode
import com.purride.pixelui.PixelFillMaxHeightElement
import com.purride.pixelui.PixelFillMaxWidthElement
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelModifierElement
import com.purride.pixelui.PixelNode
import com.purride.pixelui.PixelPaddingElement
import com.purride.pixelui.PixelPagerNode
import com.purride.pixelui.PixelRowNode
import com.purride.pixelui.PixelSizeElement
import com.purride.pixelui.PixelSurfaceNode
import com.purride.pixelui.PixelTextNode
import com.purride.pixelui.node.CustomDraw
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
)

internal data class PixelRenderResult(
    val buffer: PixelBuffer,
    val clickTargets: List<PixelClickTarget>,
    val pagerTargets: List<PixelPagerTarget>,
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
    fun render(
        root: PixelNode,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val buffer = PixelBuffer(width = logicalWidth, height = logicalHeight)
        buffer.clear()
        val clickTargets = mutableListOf<PixelClickTarget>()
        val pagerTargets = mutableListOf<PixelPagerTarget>()
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
        )
        return PixelRenderResult(
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
        )
    }

    private fun measure(node: PixelNode, constraints: PixelConstraints): PixelSize {
        val modifierInfo = modifierInfo(node.modifier)
        val innerConstraints = constraints.shrink(
            paddingLeft = modifierInfo.paddingLeft,
            paddingTop = modifierInfo.paddingTop,
            paddingRight = modifierInfo.paddingRight,
            paddingBottom = modifierInfo.paddingBottom,
        )

        val contentSize = when (node) {
            is PixelTextNode -> PixelSize(
                width = node.resolveTextRasterizer().measureText(node.text),
                height = node.resolveTextRasterizer().measureHeight(node.text),
            )

            is PixelSurfaceNode -> {
                val childSize = node.child?.let { child -> measure(child, innerConstraints) } ?: PixelSize(width = 0, height = 0)
                PixelSize(
                    width = childSize.width + (node.padding * 2),
                    height = childSize.height + (node.padding * 2),
                )
            }

            is PixelBoxNode -> {
                val children = node.children.map { child -> measure(child, innerConstraints) }
                PixelSize(
                    width = children.maxOfOrNull { it.width } ?: 0,
                    height = children.maxOfOrNull { it.height } ?: 0,
                )
            }

            is PixelRowNode -> {
                val children = node.children.map { child -> measure(child, innerConstraints) }
                val childrenWidth = children.sumOf { it.width } + (max(0, children.size - 1) * node.spacing)
                PixelSize(
                    width = childrenWidth,
                    height = children.maxOfOrNull { it.height } ?: 0,
                )
            }

            is PixelColumnNode -> {
                val children = node.children.map { child -> measure(child, innerConstraints) }
                val childrenHeight = children.sumOf { it.height } + (max(0, children.size - 1) * node.spacing)
                PixelSize(
                    width = children.maxOfOrNull { it.width } ?: 0,
                    height = childrenHeight,
                )
            }

            is PixelPagerNode -> PixelSize(
                width = innerConstraints.maxWidth,
                height = innerConstraints.maxHeight,
            )

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
        node: PixelNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
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
            is PixelTextNode -> renderText(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
            )

            is PixelSurfaceNode -> renderSurface(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )

            is PixelBoxNode -> renderBox(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )

            is PixelRowNode -> renderRow(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )

            is PixelColumnNode -> renderColumn(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )

            is PixelPagerNode -> renderPager(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )

            is CustomDraw -> Unit
        }
    }

    private fun renderText(
        node: PixelTextNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
    ) {
        node.resolveTextRasterizer().drawText(
            buffer = buffer,
            text = node.text,
            x = bounds.left,
            y = bounds.top,
            value = node.tone.value,
        )
    }

    private fun PixelTextNode.resolveTextRasterizer(): PixelTextRasterizer {
        return textRasterizer ?: this@PixelRenderRuntime.textRasterizer
    }

    private fun renderSurface(
        node: PixelSurfaceNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
    ) {
        buffer.fillRect(
            left = bounds.left,
            top = bounds.top,
            rectWidth = bounds.width,
            rectHeight = bounds.height,
            value = node.fillTone.value,
        )
        node.borderTone?.let { tone ->
            buffer.drawRect(
                left = bounds.left,
                top = bounds.top,
                rectWidth = bounds.width,
                rectHeight = bounds.height,
                value = tone.value,
            )
        }

        val child = node.child ?: return
        val childConstraints = constraints.shrink(
            paddingLeft = node.padding,
            paddingTop = node.padding,
            paddingRight = node.padding,
            paddingBottom = node.padding,
        )
        val innerBounds = bounds.inset(
            paddingLeft = node.padding,
            paddingTop = node.padding,
            paddingRight = node.padding,
            paddingBottom = node.padding,
        )
        val childSize = measure(child, childConstraints)
        val childBounds = alignedBounds(
            outerBounds = innerBounds,
            childSize = childSize,
            alignment = node.alignment,
        )
        renderNode(
            node = child,
            bounds = childBounds,
            constraints = childConstraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
        )
    }

    private fun renderBox(
        node: PixelBoxNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
    ) {
        node.children.forEach { child ->
            val childSize = measure(child, constraints)
            val childBounds = alignedBounds(
                outerBounds = bounds,
                childSize = childSize,
                alignment = node.alignment,
            )
            renderNode(
                node = child,
                bounds = childBounds,
                constraints = constraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )
        }
    }

    private fun renderRow(
        node: PixelRowNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
    ) {
        var cursorX = bounds.left
        node.children.forEach { child ->
            val childSize = measure(child, constraints)
            val childBounds = PixelRect(
                left = cursorX,
                top = bounds.top,
                width = childSize.width,
                height = childSize.height,
            )
            renderNode(
                node = child,
                bounds = childBounds,
                constraints = PixelConstraints(
                    maxWidth = childSize.width,
                    maxHeight = bounds.height,
                ),
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )
            cursorX += childSize.width + node.spacing
        }
    }

    private fun renderColumn(
        node: PixelColumnNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
    ) {
        var cursorY = bounds.top
        node.children.forEach { child ->
            val childSize = measure(child, constraints)
            val childBounds = PixelRect(
                left = bounds.left,
                top = cursorY,
                width = childSize.width,
                height = childSize.height,
            )
            renderNode(
                node = child,
                bounds = childBounds,
                constraints = PixelConstraints(
                    maxWidth = bounds.width,
                    maxHeight = childSize.height,
                ),
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
            )
            cursorY += childSize.height + node.spacing
        }
    }

    private fun renderPager(
        node: PixelPagerNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
    ) {
        node.controller.sync(
            state = node.state,
            axis = node.axis,
            pageCount = node.pages.size.coerceAtLeast(1),
        )
        pagerTargets += PixelPagerTarget(
            bounds = bounds,
            axis = node.axis,
            state = node.state,
            controller = node.controller,
        )

        val snapshot = node.controller.snapshot(node.state)
        val pageWidth = bounds.width.coerceAtLeast(1)
        val pageHeight = bounds.height.coerceAtLeast(1)
        val anchorPage = node.pages.getOrNull(snapshot.anchorPage) ?: return
        val anchorPageResult = renderPagerPage(
            page = anchorPage,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )

        val adjacentPageResult = snapshot.adjacentPage?.let { pageIndex ->
            node.pages.getOrNull(pageIndex)?.let { adjacentPage ->
                renderPagerPage(
                    page = adjacentPage,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                )
            }
        }

        val anchorShiftX = if (snapshot.axis == PixelAxis.HORIZONTAL) {
            snapshot.dragOffsetPx.toInt()
        } else {
            0
        }
        val anchorShiftY = if (snapshot.axis == PixelAxis.VERTICAL) {
            snapshot.dragOffsetPx.toInt()
        } else {
            0
        }
        val adjacentShiftX = when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> if (snapshot.dragOffsetPx > 0f) {
                anchorShiftX - pageWidth
            } else {
                anchorShiftX + pageWidth
            }

            PixelAxis.VERTICAL -> 0
        }
        val adjacentShiftY = when (snapshot.axis) {
            PixelAxis.HORIZONTAL -> 0
            PixelAxis.VERTICAL -> if (snapshot.dragOffsetPx > 0f) {
                anchorShiftY - pageHeight
            } else {
                anchorShiftY + pageHeight
            }
        }

        translateTargets(
            targets = anchorPageResult.clickTargets,
            parentBounds = bounds,
            pageShiftX = anchorShiftX,
            pageShiftY = anchorShiftY,
            into = clickTargets,
        )
        translatePagerTargets(
            targets = anchorPageResult.pagerTargets,
            parentBounds = bounds,
            pageShiftX = anchorShiftX,
            pageShiftY = anchorShiftY,
            into = pagerTargets,
        )
        adjacentPageResult?.let { result ->
            translateTargets(
                targets = result.clickTargets,
                parentBounds = bounds,
                pageShiftX = adjacentShiftX,
                pageShiftY = adjacentShiftY,
                into = clickTargets,
            )
            translatePagerTargets(
                targets = result.pagerTargets,
                parentBounds = bounds,
                pageShiftX = adjacentShiftX,
                pageShiftY = adjacentShiftY,
                into = pagerTargets,
            )
        }

        val composed = AxisBufferComposer.compose(
            primary = anchorPageResult.buffer,
            secondary = adjacentPageResult?.buffer,
            axis = snapshot.axis,
            offsetPx = snapshot.dragOffsetPx,
        )
        buffer.blit(
            source = composed,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    private fun renderPagerPage(
        page: PixelNode,
        pageWidth: Int,
        pageHeight: Int,
    ): PixelRenderResult {
        val pageBuffer = PixelBuffer(width = pageWidth, height = pageHeight).apply { clear() }
        val pageClickTargets = mutableListOf<PixelClickTarget>()
        val pagePagerTargets = mutableListOf<PixelPagerTarget>()
        renderNode(
            node = page,
            bounds = PixelRect(left = 0, top = 0, width = pageWidth, height = pageHeight),
            constraints = PixelConstraints(maxWidth = pageWidth, maxHeight = pageHeight),
            buffer = pageBuffer,
            clickTargets = pageClickTargets,
            pagerTargets = pagePagerTargets,
        )
        return PixelRenderResult(
            buffer = pageBuffer,
            clickTargets = pageClickTargets,
            pagerTargets = pagePagerTargets,
        )
    }

    private fun translateTargets(
        targets: List<PixelClickTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelClickTarget>,
    ) {
        targets.forEach { target ->
            into += PixelClickTarget(
                bounds = target.bounds.translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                ),
                onClick = target.onClick,
            )
        }
    }

    private fun translatePagerTargets(
        targets: List<PixelPagerTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelPagerTarget>,
    ) {
        targets.forEach { target ->
            into += target.copy(
                bounds = target.bounds.translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                ),
            )
        }
    }

    private fun alignedBounds(
        outerBounds: PixelRect,
        childSize: PixelSize,
        alignment: PixelAlignment,
    ): PixelRect {
        return when (alignment) {
            PixelAlignment.TOP_START -> PixelRect(
                left = outerBounds.left,
                top = outerBounds.top,
                width = childSize.width.coerceAtMost(outerBounds.width),
                height = childSize.height.coerceAtMost(outerBounds.height),
            )

            PixelAlignment.CENTER -> PixelRect(
                left = outerBounds.left + ((outerBounds.width - childSize.width).coerceAtLeast(0) / 2),
                top = outerBounds.top + ((outerBounds.height - childSize.height).coerceAtLeast(0) / 2),
                width = childSize.width.coerceAtMost(outerBounds.width),
                height = childSize.height.coerceAtMost(outerBounds.height),
            )
        }
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
