package com.purride.pixelui.internal

import com.purride.pixelcore.AxisBufferComposer
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelFillMaxHeightElement
import com.purride.pixelui.internal.legacy.PixelFillMaxWidthElement
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
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
import com.purride.pixelui.internal.legacy.PixelTextAlign
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelTextNode
import com.purride.pixelui.internal.legacy.PixelWeightElement
import com.purride.pixelui.internal.legacy.toSurfaceNode
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import kotlin.math.max
import kotlin.math.roundToInt

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

internal data class PixelTextLayoutLine(
    val text: String,
    val width: Int,
)

internal data class PixelTextLayout(
    val lines: List<PixelTextLayoutLine>,
    val width: Int,
    val height: Int,
    val lineHeight: Int,
    val lineSpacing: Int,
    val rasterizer: PixelTextRasterizer,
)

internal class PixelRenderRuntime(
    private val textRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) {
    private val buildRuntime = RetainedBuildRuntime(onVisualUpdate = { })

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

    fun render(
        root: Widget,
        logicalWidth: Int,
        logicalHeight: Int,
    ): PixelRenderResult {
        val legacyRoot = buildRuntime.resolveLegacyTree(root)
            ?: error("当前 Widget 树没有生成可渲染的 legacy node。")
        return render(
            root = legacyRoot,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
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
                    textAlignNeedsFullWidth(node) &&
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
                    maxWidth = positionedMaxWidth(node, innerConstraints),
                    maxHeight = positionedMaxHeight(node, innerConstraints),
                )
                val childSize = measure(node.child, childConstraints)
                PixelSize(
                    width = positionedWidth(node, innerConstraints, childSize),
                    height = positionedHeight(node, innerConstraints, childSize),
                )
            }

            is PixelRowNode -> {
                val children = measureRowChildren(node, innerConstraints)
                val childrenWidth = if (node.children.any { childWeight(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
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
                val children = measureColumnChildren(node, innerConstraints)
                val childrenHeight = if (node.children.any { childWeight(it) > 0f } || node.mainAxisSize == PixelMainAxisSize.MAX) {
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
                val textRasterizer = node.resolveTextRasterizer()
                val displayText = node.state.text.ifEmpty { node.placeholder.ifEmpty { " " } }
                val textWidth = textRasterizer.measureText(displayText)
                val textHeight = textRasterizer.measureHeight(displayText)
                PixelSize(
                    width = textWidth + (node.style.padding * 2) + if (node.state.isFocused) 2 else 0,
                    height = textHeight + (node.style.padding * 2),
                )
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

            is PixelBoxNode -> renderBox(
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

            is PixelRowNode -> renderRow(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelColumnNode -> renderColumn(
                node = node,
                bounds = paddedBounds,
                constraints = innerConstraints,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelPagerNode -> renderPager(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelListNode -> renderList(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelSingleChildScrollViewNode -> renderSingleChildScrollView(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                clickTargets = clickTargets,
                pagerTargets = pagerTargets,
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )

            is PixelTextFieldNode -> renderTextField(
                node = node,
                bounds = paddedBounds,
                buffer = buffer,
                textInputTargets = textInputTargets,
            )

            is CustomDraw -> Unit
        }
    }

    private fun renderText(
        node: PixelTextNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
    ) {
        val layout = layoutText(
            node = node,
            maxWidth = bounds.width,
        )
        var cursorY = bounds.top
        layout.lines.forEach { line ->
            if (line.text.isNotEmpty()) {
                val lineX = when (node.textAlign) {
                    PixelTextAlign.START -> when (node.textDirection) {
                        TextDirection.LTR -> bounds.left
                        TextDirection.RTL -> bounds.left + (bounds.width - line.width).coerceAtLeast(0)
                    }
                    PixelTextAlign.CENTER -> bounds.left + ((bounds.width - line.width).coerceAtLeast(0) / 2)
                    PixelTextAlign.END -> when (node.textDirection) {
                        TextDirection.LTR -> bounds.left + (bounds.width - line.width).coerceAtLeast(0)
                        TextDirection.RTL -> bounds.left
                    }
                }
                layout.rasterizer.drawText(
                    buffer = buffer,
                    text = line.text,
                    x = lineX,
                    y = cursorY,
                    value = node.style.tone.value,
                )
            }
            cursorY += layout.lineHeight + layout.lineSpacing
        }
    }

    private fun PixelTextNode.resolveTextRasterizer(): PixelTextRasterizer {
        return style.textRasterizer ?: this@PixelRenderRuntime.textRasterizer
    }

    private fun layoutText(
        node: PixelTextNode,
        maxWidth: Int,
    ): PixelTextLayout {
        val rasterizer = node.resolveTextRasterizer()
        val constrainedWidth = maxWidth.coerceAtLeast(0)
        val effectiveMaxLines = node.maxLines.coerceAtLeast(1)
        val lineHeight = rasterizer.measureHeight(" ")
        val lineSpacing = node.style.lineSpacing.coerceAtLeast(0)
        val sourceLines = node.text.ifEmpty { "" }.split('\n')
        val laidOutLines = mutableListOf<String>()

        sourceLines.forEach { sourceLine ->
            if (laidOutLines.size >= effectiveMaxLines) {
                return@forEach
            }

            if (node.softWrap && constrainedWidth > 0) {
                appendWrappedLines(
                    sourceLine = sourceLine,
                    maxWidth = constrainedWidth,
                    maxLines = effectiveMaxLines,
                    target = laidOutLines,
                    rasterizer = rasterizer,
                )
            } else {
                laidOutLines += sourceLine
            }
        }

        if (laidOutLines.isEmpty()) {
            laidOutLines += ""
        }

        val visibleLines = laidOutLines.take(effectiveMaxLines).toMutableList()
        val truncatedByLineCount = laidOutLines.size > effectiveMaxLines
        visibleLines.indices.forEach { index ->
            val shouldEllipsize = truncatedByLineCount && index == visibleLines.lastIndex
            visibleLines[index] = fitTextToWidth(
                text = visibleLines[index],
                maxWidth = constrainedWidth,
                overflow = node.overflow,
                rasterizer = rasterizer,
                forceEllipsis = shouldEllipsize,
            )
        }

        val measuredLines = visibleLines.map { line ->
            PixelTextLayoutLine(
                text = line,
                width = rasterizer.measureText(line),
            )
        }
        val measuredWidth = measuredLines.maxOfOrNull { it.width } ?: 0
        val width = if (constrainedWidth > 0) {
            measuredWidth.coerceAtMost(constrainedWidth)
        } else {
            measuredWidth
        }
        val height = if (measuredLines.isEmpty()) {
            0
        } else {
            (measuredLines.size * lineHeight) + ((measuredLines.size - 1) * lineSpacing)
        }

        return PixelTextLayout(
            lines = measuredLines,
            width = width,
            height = height,
            lineHeight = lineHeight,
            lineSpacing = lineSpacing,
            rasterizer = rasterizer,
        )
    }

    private fun appendWrappedLines(
        sourceLine: String,
        maxWidth: Int,
        maxLines: Int,
        target: MutableList<String>,
        rasterizer: PixelTextRasterizer,
    ) {
        if (target.size >= maxLines) {
            return
        }
        if (sourceLine.isEmpty()) {
            target += ""
            return
        }

        var currentLine = StringBuilder()
        sourceLine.forEach { character ->
            val candidate = currentLine.toString() + character
            if (rasterizer.measureText(candidate) <= maxWidth || currentLine.isEmpty()) {
                currentLine.append(character)
            } else {
                target += currentLine.toString()
                if (target.size >= maxLines) {
                    return
                }
                currentLine = StringBuilder().append(character)
            }
        }

        if (target.size < maxLines) {
            target += currentLine.toString()
        }
    }

    private fun fitTextToWidth(
        text: String,
        maxWidth: Int,
        overflow: PixelTextOverflow,
        rasterizer: PixelTextRasterizer,
        forceEllipsis: Boolean,
    ): String {
        if (maxWidth <= 0 || text.isEmpty()) {
            return ""
        }
        if (rasterizer.measureText(text) <= maxWidth && !forceEllipsis) {
            return text
        }

        if (overflow == PixelTextOverflow.ELLIPSIS || forceEllipsis) {
            val ellipsis = "..."
            if (rasterizer.measureText(ellipsis) > maxWidth) {
                return clipTextToWidth(text, maxWidth, rasterizer)
            }

            val builder = StringBuilder(text)
            while (builder.isNotEmpty() &&
                rasterizer.measureText(builder.toString() + ellipsis) > maxWidth
            ) {
                builder.deleteCharAt(builder.lastIndex)
            }
            return if (builder.isEmpty()) {
                clipTextToWidth(ellipsis, maxWidth, rasterizer)
            } else {
                builder.toString() + ellipsis
            }
        }

        return clipTextToWidth(
            text = text,
            maxWidth = maxWidth,
            rasterizer = rasterizer,
        )
    }

    private fun clipTextToWidth(
        text: String,
        maxWidth: Int,
        rasterizer: PixelTextRasterizer,
    ): String {
        if (maxWidth <= 0) {
            return ""
        }

        val builder = StringBuilder()
        text.forEach { character ->
            val candidate = builder.toString() + character
            if (rasterizer.measureText(candidate) <= maxWidth) {
                builder.append(character)
            } else {
                return builder.toString()
            }
        }
        return builder.toString()
    }

    private fun PixelTextFieldNode.resolveTextRasterizer(): PixelTextRasterizer {
        return style.textStyle.textRasterizer ?: this@PixelRenderRuntime.textRasterizer
    }

    private fun renderSurface(
        node: PixelSurfaceNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
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
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

    private fun renderBox(
        node: PixelBoxNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        node.children.forEach { child ->
            if (child is PixelPositionedNode) {
                renderPositionedChild(
                    node = child,
                    outerBounds = bounds,
                    outerConstraints = constraints,
                    buffer = buffer,
                    clickTargets = clickTargets,
                    pagerTargets = pagerTargets,
                    listTargets = listTargets,
                    textInputTargets = textInputTargets,
                )
            } else {
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
                    listTargets = listTargets,
                    textInputTargets = textInputTargets,
                )
            }
        }
    }

    private fun renderPositionedChild(
        node: PixelPositionedNode,
        outerBounds: PixelRect,
        outerConstraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childConstraints = PixelConstraints(
            maxWidth = positionedMaxWidth(node, outerConstraints),
            maxHeight = positionedMaxHeight(node, outerConstraints),
        )
        val childSize = measure(node.child, childConstraints)
        val width = positionedWidth(node, outerConstraints, childSize).coerceAtLeast(0)
        val height = positionedHeight(node, outerConstraints, childSize).coerceAtLeast(0)
        val left = when {
            node.left != null -> outerBounds.left + node.left
            node.right != null -> outerBounds.right - node.right - width
            else -> outerBounds.left
        }
        val top = when {
            node.top != null -> outerBounds.top + node.top
            node.bottom != null -> outerBounds.bottom - node.bottom - height
            else -> outerBounds.top
        }
        val childBounds = PixelRect(
            left = left,
            top = top,
            width = width.coerceAtMost(outerBounds.right - left),
            height = height.coerceAtMost(outerBounds.bottom - top),
        )
        renderNode(
            node = node.child,
            bounds = childBounds,
            constraints = childConstraints,
            buffer = buffer,
            clickTargets = clickTargets,
            pagerTargets = pagerTargets,
            listTargets = listTargets,
            textInputTargets = textInputTargets,
        )
    }

    private fun positionedMaxWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null ->
                (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> constraints.maxWidth
        }
    }

    private fun positionedMaxHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null ->
                (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> constraints.maxHeight
        }
    }

    private fun positionedWidth(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.width != null -> node.width
            node.left != null && node.right != null ->
                (constraints.maxWidth - node.left - node.right).coerceAtLeast(0)
            else -> childSize.width
        }
    }

    private fun positionedHeight(
        node: PixelPositionedNode,
        constraints: PixelConstraints,
        childSize: PixelSize,
    ): Int {
        return when {
            node.height != null -> node.height
            node.top != null && node.bottom != null ->
                (constraints.maxHeight - node.top - node.bottom).coerceAtLeast(0)
            else -> childSize.height
        }
    }

    private fun renderRow(
        node: PixelRowNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childSizes = measureRowChildren(node, constraints)
        val contentWidth = childSizes.sumOf { it.width } + (max(0, node.children.size - 1) * node.spacing)
        val horizontalMainAxis = mainAxisArrangement(
            containerStart = bounds.left,
            containerExtent = bounds.width,
            contentExtent = contentWidth,
            spacing = node.spacing,
            childCount = childSizes.size,
            alignment = node.mainAxisAlignment,
        )
        var cursorX = horizontalMainAxis.start
        node.children.zip(childSizes).forEach { (child, childSize) ->
            val childHeight = if (node.crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) {
                bounds.height
            } else {
                childSize.height
            }
            val childBounds = PixelRect(
                left = cursorX,
                top = crossAxisStart(
                    containerStart = bounds.top,
                    containerExtent = bounds.height,
                    childExtent = childHeight,
                    alignment = node.crossAxisAlignment,
                ),
                width = childSize.width,
                height = childHeight,
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
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )
            cursorX += childSize.width + horizontalMainAxis.spacingAfterChild
        }
    }

    private fun renderColumn(
        node: PixelColumnNode,
        bounds: PixelRect,
        constraints: PixelConstraints,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val childSizes = measureColumnChildren(node, constraints)
        val contentHeight = childSizes.sumOf { it.height } + (max(0, node.children.size - 1) * node.spacing)
        val verticalMainAxis = mainAxisArrangement(
            containerStart = bounds.top,
            containerExtent = bounds.height,
            contentExtent = contentHeight,
            spacing = node.spacing,
            childCount = childSizes.size,
            alignment = node.mainAxisAlignment,
        )
        var cursorY = verticalMainAxis.start
        node.children.zip(childSizes).forEach { (child, childSize) ->
            val childWidth = if (node.crossAxisAlignment == PixelCrossAxisAlignment.STRETCH) {
                bounds.width
            } else {
                childSize.width
            }
            val childBounds = PixelRect(
                left = crossAxisStart(
                    containerStart = bounds.left,
                    containerExtent = bounds.width,
                    childExtent = childWidth,
                    alignment = node.crossAxisAlignment,
                ),
                top = cursorY,
                width = childWidth,
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
                listTargets = listTargets,
                textInputTargets = textInputTargets,
            )
            cursorY += childSize.height + verticalMainAxis.spacingAfterChild
        }
    }

    private fun measureRowChildren(
        node: PixelRowNode,
        constraints: PixelConstraints,
    ): List<PixelSize> {
        val sizes = MutableList(node.children.size) { PixelSize(width = 0, height = 0) }
        val spacingWidth = max(0, node.children.size - 1) * node.spacing
        var occupiedWidth = 0
        var totalWeight = 0f

        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight > 0f) {
                totalWeight += weight
            } else {
                val childSize = measure(child, constraints)
                sizes[index] = childSize
                occupiedWidth += childSize.width
            }
        }

        val remainingWidth = (constraints.maxWidth - spacingWidth - occupiedWidth).coerceAtLeast(0)
        if (totalWeight <= 0f) {
            return sizes
        }

        var assignedWidth = 0
        var weightedSeen = 0
        val weightedCount = node.children.count { childWeight(it) > 0f }
        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight <= 0f) {
                return@forEachIndexed
            }
            val fit = childFlexFit(child)
            weightedSeen += 1
            val allocatedWidth = if (weightedSeen == weightedCount) {
                remainingWidth - assignedWidth
            } else {
                ((remainingWidth * (weight / totalWeight)).toInt()).coerceAtLeast(0)
            }
            assignedWidth += allocatedWidth
            val measured = measure(
                child,
                PixelConstraints(
                    maxWidth = allocatedWidth,
                    maxHeight = constraints.maxHeight,
                ),
            )
            sizes[index] = PixelSize(
                width = if (fit == PixelFlexFit.TIGHT) {
                    allocatedWidth
                } else {
                    measured.width.coerceAtMost(allocatedWidth)
                },
                height = measured.height,
            )
        }
        return sizes
    }

    private fun measureColumnChildren(
        node: PixelColumnNode,
        constraints: PixelConstraints,
    ): List<PixelSize> {
        val sizes = MutableList(node.children.size) { PixelSize(width = 0, height = 0) }
        val spacingHeight = max(0, node.children.size - 1) * node.spacing
        var occupiedHeight = 0
        var totalWeight = 0f

        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight > 0f) {
                totalWeight += weight
            } else {
                val childSize = measure(child, constraints)
                sizes[index] = childSize
                occupiedHeight += childSize.height
            }
        }

        val remainingHeight = (constraints.maxHeight - spacingHeight - occupiedHeight).coerceAtLeast(0)
        if (totalWeight <= 0f) {
            return sizes
        }

        var assignedHeight = 0
        var weightedSeen = 0
        val weightedCount = node.children.count { childWeight(it) > 0f }
        node.children.forEachIndexed { index, child ->
            val weight = childWeight(child)
            if (weight <= 0f) {
                return@forEachIndexed
            }
            val fit = childFlexFit(child)
            weightedSeen += 1
            val allocatedHeight = if (weightedSeen == weightedCount) {
                remainingHeight - assignedHeight
            } else {
                ((remainingHeight * (weight / totalWeight)).toInt()).coerceAtLeast(0)
            }
            assignedHeight += allocatedHeight
            val measured = measure(
                child,
                PixelConstraints(
                    maxWidth = constraints.maxWidth,
                    maxHeight = allocatedHeight,
                ),
            )
            sizes[index] = PixelSize(
                width = measured.width,
                height = if (fit == PixelFlexFit.TIGHT) {
                    allocatedHeight
                } else {
                    measured.height.coerceAtMost(allocatedHeight)
                },
            )
        }
        return sizes
    }

    private fun childWeight(node: LegacyRenderNode): Float {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.weight
            ?.coerceAtLeast(0f)
            ?: 0f
    }

    private fun childFlexFit(node: LegacyRenderNode): PixelFlexFit {
        return node.modifier.elements
            .filterIsInstance<PixelWeightElement>()
            .lastOrNull()
            ?.fit
            ?: PixelFlexFit.TIGHT
    }

    /**
     * `Row/Column` 当前阶段只处理交叉轴上的起点偏移。
     *
     * 这样可以先把最常见的顶部/居中/底部对齐补齐，而不用提前把
     * 更复杂的主轴排布和多段对齐语义一起做进来。
     */
    private fun crossAxisStart(
        containerStart: Int,
        containerExtent: Int,
        childExtent: Int,
        alignment: PixelCrossAxisAlignment,
    ): Int {
        val remaining = (containerExtent - childExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelCrossAxisAlignment.START -> containerStart
            PixelCrossAxisAlignment.CENTER -> containerStart + (remaining / 2)
            PixelCrossAxisAlignment.END -> containerStart + remaining
            PixelCrossAxisAlignment.STRETCH -> containerStart
        }
    }

    /**
     * 主轴排布当前只处理整体内容块的起点偏移。
     *
     * 先提供 `START / CENTER / END` 三档，让线性布局具备最基础的
     * 主轴排布能力，后面再考虑 `spaceBetween` 这类更复杂规则。
     */
    private data class MainAxisArrangement(
        val start: Int,
        val spacingAfterChild: Int,
    )

    private fun mainAxisArrangement(
        containerStart: Int,
        containerExtent: Int,
        contentExtent: Int,
        spacing: Int,
        childCount: Int,
        alignment: PixelMainAxisAlignment,
    ): MainAxisArrangement {
        val remaining = (containerExtent - contentExtent).coerceAtLeast(0)
        return when (alignment) {
            PixelMainAxisAlignment.START -> MainAxisArrangement(
                start = containerStart,
                spacingAfterChild = spacing,
            )
            PixelMainAxisAlignment.CENTER -> MainAxisArrangement(
                start = containerStart + (remaining / 2),
                spacingAfterChild = spacing,
            )
            PixelMainAxisAlignment.END -> MainAxisArrangement(
                start = containerStart + remaining,
                spacingAfterChild = spacing,
            )
            PixelMainAxisAlignment.SPACE_BETWEEN -> {
                if (childCount <= 1) {
                    MainAxisArrangement(
                        start = containerStart,
                        spacingAfterChild = spacing,
                    )
                } else {
                    MainAxisArrangement(
                        start = containerStart,
                        spacingAfterChild = spacing + (remaining / (childCount - 1)),
                    )
                }
            }
            PixelMainAxisAlignment.SPACE_AROUND -> {
                if (childCount <= 0) {
                    MainAxisArrangement(
                        start = containerStart,
                        spacingAfterChild = spacing,
                    )
                } else {
                    val unit = remaining / childCount
                    MainAxisArrangement(
                        start = containerStart + (unit / 2),
                        spacingAfterChild = spacing + unit,
                    )
                }
            }
            PixelMainAxisAlignment.SPACE_EVENLY -> {
                val slotCount = childCount + 1
                val unit = if (slotCount <= 0) 0 else remaining / slotCount
                MainAxisArrangement(
                    start = containerStart + unit,
                    spacingAfterChild = spacing + unit,
                )
            }
        }
    }

    private fun renderPager(
        node: PixelPagerNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
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
            onPageChanged = node.onPageChanged,
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
        translateListTargets(
            targets = anchorPageResult.listTargets,
            parentBounds = bounds,
            pageShiftX = anchorShiftX,
            pageShiftY = anchorShiftY,
            into = listTargets,
        )
        translateTextInputTargets(
            targets = anchorPageResult.textInputTargets,
            parentBounds = bounds,
            pageShiftX = anchorShiftX,
            pageShiftY = anchorShiftY,
            into = textInputTargets,
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
            translateListTargets(
                targets = result.listTargets,
                parentBounds = bounds,
                pageShiftX = adjacentShiftX,
                pageShiftY = adjacentShiftY,
                into = listTargets,
            )
            translateTextInputTargets(
                targets = result.textInputTargets,
                parentBounds = bounds,
                pageShiftX = adjacentShiftX,
                pageShiftY = adjacentShiftY,
                into = textInputTargets,
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

    private fun renderList(
        node: PixelListNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val viewportWidth = bounds.width.coerceAtLeast(1)
        val viewportHeight = bounds.height.coerceAtLeast(1)
        val itemConstraints = PixelConstraints(
            maxWidth = viewportWidth,
            maxHeight = viewportHeight,
        )
        val itemSizes = node.items.map { child -> measure(child, itemConstraints) }
        val contentHeight = itemSizes.sumOf { size -> size.height } + (max(0, itemSizes.size - 1) * node.spacing)
        val itemTopOffsets = IntArray(itemSizes.size)
        var nextItemTop = 0
        itemSizes.forEachIndexed { index, size ->
            itemTopOffsets[index] = nextItemTop
            nextItemTop += size.height + node.spacing
        }
        node.state.itemTopOffsetsPx = itemTopOffsets
        node.state.itemHeightsPx = itemSizes.map { it.height }.toIntArray()
        node.controller.sync(
            state = node.state,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
        )

        listTargets += PixelListTarget(
            bounds = bounds,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
            state = node.state,
            controller = node.controller,
        )

        // 列表先在独立局部缓冲中绘制，再整体贴回父缓冲。
        // 这样既能复用现有节点渲染逻辑，也能天然得到视口裁剪效果。
        val listBuffer = PixelBuffer(width = viewportWidth, height = viewportHeight).apply { clear() }
        val listClickTargets = mutableListOf<PixelClickTarget>()
        val listPagerTargets = mutableListOf<PixelPagerTarget>()
        val nestedListTargets = mutableListOf<PixelListTarget>()
        val listTextInputTargets = mutableListOf<PixelTextInputTarget>()
        var cursorY = -node.state.scrollOffsetPx.roundToInt()

        node.items.zip(itemSizes).forEach { (child, childSize) ->
            val childBounds = PixelRect(
                left = 0,
                top = cursorY,
                width = childSize.width,
                height = childSize.height,
            )
            if (childBounds.bottom > 0 && childBounds.top < viewportHeight) {
                renderNode(
                    node = child,
                    bounds = childBounds,
                    constraints = PixelConstraints(
                        maxWidth = viewportWidth,
                        maxHeight = childSize.height,
                    ),
                    buffer = listBuffer,
                    clickTargets = listClickTargets,
                    pagerTargets = listPagerTargets,
                    listTargets = nestedListTargets,
                    textInputTargets = listTextInputTargets,
                )
            }
            cursorY += childSize.height + node.spacing
        }

        translateTargets(
            targets = listClickTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = clickTargets,
        )
        translatePagerTargets(
            targets = listPagerTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = pagerTargets,
        )
        translateListTargets(
            targets = nestedListTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = listTargets,
        )
        translateTextInputTargets(
            targets = listTextInputTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = textInputTargets,
        )

        buffer.blit(
            source = listBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    private fun renderSingleChildScrollView(
        node: PixelSingleChildScrollViewNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        clickTargets: MutableList<PixelClickTarget>,
        pagerTargets: MutableList<PixelPagerTarget>,
        listTargets: MutableList<PixelListTarget>,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val viewportWidth = bounds.width.coerceAtLeast(1)
        val viewportHeight = bounds.height.coerceAtLeast(1)
        val childConstraints = PixelConstraints(
            maxWidth = viewportWidth,
            maxHeight = SCROLL_AXIS_UNBOUNDED_MAX,
        )
        val childSize = measure(node.child, childConstraints)
        val contentHeight = childSize.height
        node.state.itemTopOffsetsPx = intArrayOf(0)
        node.state.itemHeightsPx = intArrayOf(childSize.height)
        node.controller.sync(
            state = node.state,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
        )

        listTargets += PixelListTarget(
            bounds = bounds,
            viewportHeightPx = viewportHeight,
            contentHeightPx = contentHeight,
            state = node.state,
            controller = node.controller,
        )

        val scrollBuffer = PixelBuffer(width = viewportWidth, height = viewportHeight).apply { clear() }
        val scrollClickTargets = mutableListOf<PixelClickTarget>()
        val scrollPagerTargets = mutableListOf<PixelPagerTarget>()
        val nestedListTargets = mutableListOf<PixelListTarget>()
        val scrollTextInputTargets = mutableListOf<PixelTextInputTarget>()
        val childBounds = PixelRect(
            left = 0,
            top = -node.state.scrollOffsetPx.roundToInt(),
            width = childSize.width,
            height = childSize.height,
        )

        renderNode(
            node = node.child,
            bounds = childBounds,
            constraints = childConstraints,
            buffer = scrollBuffer,
            clickTargets = scrollClickTargets,
            pagerTargets = scrollPagerTargets,
            listTargets = nestedListTargets,
            textInputTargets = scrollTextInputTargets,
        )

        translateTargets(
            targets = scrollClickTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = clickTargets,
        )
        translatePagerTargets(
            targets = scrollPagerTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = pagerTargets,
        )
        translateListTargets(
            targets = nestedListTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = listTargets,
        )
        translateTextInputTargets(
            targets = scrollTextInputTargets,
            parentBounds = bounds,
            pageShiftX = 0,
            pageShiftY = 0,
            into = textInputTargets,
        )

        buffer.blit(
            source = scrollBuffer,
            destX = bounds.left,
            destY = bounds.top,
        )
    }

    private fun renderTextField(
        node: PixelTextFieldNode,
        bounds: PixelRect,
        buffer: PixelBuffer,
        textInputTargets: MutableList<PixelTextInputTarget>,
    ) {
        val borderTone = if (!node.enabled) {
            node.style.disabledBorderTone ?: node.style.borderTone
        } else if (node.readOnly) {
            node.style.readOnlyBorderTone ?: node.style.borderTone
        } else if (node.state.isFocused) {
            node.style.focusedBorderTone ?: node.style.borderTone
        } else {
            node.style.borderTone
        }
        buffer.fillRect(
            left = bounds.left,
            top = bounds.top,
            rectWidth = bounds.width,
            rectHeight = bounds.height,
            value = node.style.fillTone.value,
        )
        borderTone?.let { tone ->
            buffer.drawRect(
                left = bounds.left,
                top = bounds.top,
                rectWidth = bounds.width,
                rectHeight = bounds.height,
                value = tone.value,
            )
        }

        val displayText = node.state.text.ifEmpty { node.placeholder }
        val displayStyle = if (!node.enabled) {
            if (node.state.text.isEmpty()) {
                node.style.disabledPlaceholderStyle
            } else {
                node.style.disabledTextStyle
            }
        } else if (node.state.text.isEmpty()) {
            node.style.placeholderStyle
        } else {
            node.style.textStyle
        }
        val displayRasterizer = displayStyle.textRasterizer ?: textRasterizer
        val contentMaxWidth = (bounds.width - (node.style.padding * 2)).coerceAtLeast(0)
        val visibleDisplayText = clipTextToWidth(
            text = displayText,
            maxWidth = contentMaxWidth,
            rasterizer = displayRasterizer,
        )
        val textHeight = displayRasterizer.measureHeight(visibleDisplayText.ifEmpty { " " })
        val textY = bounds.top + ((bounds.height - textHeight).coerceAtLeast(0) / 2)
        val textX = bounds.left + node.style.padding
        if (visibleDisplayText.isNotEmpty()) {
            displayRasterizer.drawText(
                buffer = buffer,
                text = visibleDisplayText,
                x = textX,
                y = textY,
                value = displayStyle.tone.value,
            )
        }

        if (node.enabled && !node.readOnly && node.state.isFocused) {
            val visibleText = node.state.text.take(node.state.selectionStart.coerceAtMost(node.state.text.length))
            val clippedVisibleText = clipTextToWidth(
                text = visibleText,
                maxWidth = contentMaxWidth,
                rasterizer = node.style.textStyle.textRasterizer ?: textRasterizer,
            )
            val cursorX = textX + (node.style.textStyle.textRasterizer ?: textRasterizer).measureText(clippedVisibleText)
            val cursorTop = bounds.top + node.style.padding
            val cursorHeight = (bounds.height - (node.style.padding * 2)).coerceAtLeast(1)
            buffer.fillRect(
                left = cursorX.coerceAtMost(bounds.right - 1),
                top = cursorTop,
                rectWidth = 1,
                rectHeight = cursorHeight,
                value = node.style.cursorTone.value,
            )
        }

        if (node.enabled) {
            textInputTargets += PixelTextInputTarget(
                bounds = bounds,
                state = node.state,
                controller = node.controller,
                readOnly = node.readOnly,
                autofocus = node.autofocus,
                action = node.textInputAction,
                onChanged = node.onChanged,
                onSubmitted = node.onSubmitted,
            )
        }
    }

    private fun renderPagerPage(
        page: LegacyRenderNode,
        pageWidth: Int,
        pageHeight: Int,
    ): PixelRenderResult {
        val pageBuffer = PixelBuffer(width = pageWidth, height = pageHeight).apply { clear() }
        val pageClickTargets = mutableListOf<PixelClickTarget>()
        val pagePagerTargets = mutableListOf<PixelPagerTarget>()
        val pageListTargets = mutableListOf<PixelListTarget>()
        val pageTextInputTargets = mutableListOf<PixelTextInputTarget>()
        renderNode(
            node = page,
            bounds = PixelRect(left = 0, top = 0, width = pageWidth, height = pageHeight),
            constraints = PixelConstraints(maxWidth = pageWidth, maxHeight = pageHeight),
            buffer = pageBuffer,
            clickTargets = pageClickTargets,
            pagerTargets = pagePagerTargets,
            listTargets = pageListTargets,
            textInputTargets = pageTextInputTargets,
        )
        return PixelRenderResult(
            buffer = pageBuffer,
            clickTargets = pageClickTargets,
            pagerTargets = pagePagerTargets,
            listTargets = pageListTargets,
            textInputTargets = pageTextInputTargets,
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
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += PixelClickTarget(
                        bounds = translatedBounds,
                        onClick = target.onClick,
                    )
                }
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
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }

    private fun translateListTargets(
        targets: List<PixelListTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelListTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }

    private fun translateTextInputTargets(
        targets: List<PixelTextInputTarget>,
        parentBounds: PixelRect,
        pageShiftX: Int,
        pageShiftY: Int,
        into: MutableList<PixelTextInputTarget>,
    ) {
        targets.forEach { target ->
            target.bounds
                .translate(
                    deltaX = parentBounds.left + pageShiftX,
                    deltaY = parentBounds.top + pageShiftY,
                )
                .intersect(parentBounds)
                ?.let { translatedBounds ->
                    into += target.copy(bounds = translatedBounds)
                }
        }
    }

    private fun alignedBounds(
        outerBounds: PixelRect,
        childSize: PixelSize,
        alignment: PixelAlignment,
    ): PixelRect {
        val clampedWidth = childSize.width.coerceAtMost(outerBounds.width)
        val clampedHeight = childSize.height.coerceAtMost(outerBounds.height)
        val centeredLeft = outerBounds.left + ((outerBounds.width - childSize.width).coerceAtLeast(0) / 2)
        val endLeft = outerBounds.left + (outerBounds.width - childSize.width).coerceAtLeast(0)
        val centeredTop = outerBounds.top + ((outerBounds.height - childSize.height).coerceAtLeast(0) / 2)
        val bottomTop = outerBounds.top + (outerBounds.height - childSize.height).coerceAtLeast(0)
        return when (alignment) {
            PixelAlignment.TOP_START -> PixelRect(
                left = outerBounds.left,
                top = outerBounds.top,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.TOP_CENTER -> PixelRect(
                left = centeredLeft,
                top = outerBounds.top,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.TOP_END -> PixelRect(
                left = endLeft,
                top = outerBounds.top,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.CENTER_START -> PixelRect(
                left = outerBounds.left,
                top = centeredTop,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.CENTER -> PixelRect(
                left = centeredLeft,
                top = centeredTop,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.CENTER_END -> PixelRect(
                left = endLeft,
                top = centeredTop,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.BOTTOM_START -> PixelRect(
                left = outerBounds.left,
                top = bottomTop,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.BOTTOM_CENTER -> PixelRect(
                left = centeredLeft,
                top = bottomTop,
                width = clampedWidth,
                height = clampedHeight,
            )

            PixelAlignment.BOTTOM_END -> PixelRect(
                left = endLeft,
                top = bottomTop,
                width = clampedWidth,
                height = clampedHeight,
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

    private fun textAlignNeedsFullWidth(node: PixelTextNode): Boolean {
        return when (node.textAlign) {
            PixelTextAlign.CENTER -> true
            PixelTextAlign.START -> node.textDirection == TextDirection.RTL
            PixelTextAlign.END -> node.textDirection == TextDirection.LTR
        }
    }
}
