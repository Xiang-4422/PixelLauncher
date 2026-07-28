package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsActions
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * 新渲染管线里的最小表面对象。
 *
 * 负责背景/边框绘制、单 child 承接、尺寸/padding/alignment、点击目标导出。
 */
public class RenderSurface(
    child: RenderBox? = null,
    private var fillColor: PixelColor? = null,
    private var borderColor: PixelColor? = null,
    /** Number of nested pixel-aligned border layers. */
    private var borderWidth: Int = 1,
    /** Stair-step corner radius applied to fill, border, and hard shadow. */
    private var cornerRadius: Int = 0,
    /** Optional hard-edged elevation shadow color. */
    private var shadowColor: PixelColor? = null,
    /** Positive diagonal hard-shadow offset included in this surface's measured size. */
    private var shadowOffset: Int = 0,
    private var alignment: PixelAlignment = PixelAlignment.TOP_START,
    private var explicitWidth: Int? = null,
    private var explicitHeight: Int? = null,
    private var fillMaxWidth: Boolean = false,
    private var fillMaxHeight: Boolean = false,
    private var outerPaddingLeft: Int = 0,
    private var outerPaddingTop: Int = 0,
    private var outerPaddingRight: Int = 0,
    private var outerPaddingBottom: Int = 0,
    private var contentPaddingLeft: Int = 0,
    private var contentPaddingTop: Int = 0,
    private var contentPaddingRight: Int = 0,
    private var contentPaddingBottom: Int = 0,
    private var onClick: (() -> Unit)? = null,
    /** Press micro-state callback exported with this surface's click target. */
    private var onPressedChanged: ((Boolean) -> Unit)? = null,
    /** Hover micro-state callback exported with this surface's click target. */
    private var onHoveredChanged: ((Boolean) -> Unit)? = null,
    private var onLongPress: (() -> Unit)? = null,
    private var onDoubleTap: (() -> Unit)? = null,
    private var onSwipeStart: (() -> Unit)? = null,
    private var onSwipeUpdate: ((Int) -> Unit)? = null,
    private var onSwipeEnd: ((Int) -> Unit)? = null,
    private var onSwipeLeft: (() -> Unit)? = null,
    private var onSwipeRight: (() -> Unit)? = null,
    private var preserveChildMinConstraints: Boolean = false,
    private var tightChildWidth: Boolean = false,
    private var tightChildHeight: Boolean = false,
    private var textInputState: PixelTextFieldState? = null,
    private var textInputController: PixelTextFieldController? = null,
    private var textInputReadOnly: Boolean = false,
    private var textInputAutofocus: Boolean = false,
    private var textInputMinLines: Int = 1,
    private var textInputMaxLines: Int = 1,
    private var textInputType: PixelInputType = PixelInputType.TEXT,
    private var textInputAction: PixelTextInputAction = PixelTextInputAction.DONE,
    private var textInputFocusNode: FocusNode? = null,
    private var textInputOnChanged: ((String) -> Unit)? = null,
    private var textInputOnSubmitted: ((String) -> Unit)? = null,
    private var textInputCursorColor: PixelColor? = null,
    private var textInputCursorVisible: Boolean = true,
    /** 非空文本末尾字形与光标之间的额外像素间隙。 */
    private var textInputCursorGap: Int = 0,
    private var textInputSelectionColor: PixelColor? = null,
    private var textInputCompositionColor: PixelColor? = null,
    private var textInputSelectionHandleColor: PixelColor? = null,
) : SingleChildRenderObject() {
    private var childOffsetX = 0
    private var childOffsetY = 0

    init {
        borderWidth = borderWidth.coerceAtLeast(0)
        cornerRadius = cornerRadius.coerceAtLeast(0)
        shadowOffset = shadowOffset.coerceAtLeast(0).takeIf { shadowColor != null } ?: 0
        textInputMinLines = textInputMinLines.coerceAtLeast(1)
        textInputMaxLines = textInputMaxLines.coerceAtLeast(textInputMinLines)
        textInputCursorGap = textInputCursorGap.coerceAtLeast(0)
        setRenderObjectChild(child)
    }

    /**
     * 更新当前 surface 配置，并触发布局与绘制刷新。
     */
    public fun updateSurface(
        fillColor: PixelColor? = null,
        borderColor: PixelColor? = null,
        borderWidth: Int = 1,
        cornerRadius: Int = 0,
        shadowColor: PixelColor? = null,
        shadowOffset: Int = 0,
        alignment: PixelAlignment,
        explicitWidth: Int? = null,
        explicitHeight: Int? = null,
        fillMaxWidth: Boolean = false,
        fillMaxHeight: Boolean = false,
        outerPaddingLeft: Int = 0,
        outerPaddingTop: Int = 0,
        outerPaddingRight: Int = 0,
        outerPaddingBottom: Int = 0,
        contentPaddingLeft: Int = 0,
        contentPaddingTop: Int = 0,
        contentPaddingRight: Int = 0,
        contentPaddingBottom: Int = 0,
        onClick: (() -> Unit)? = null,
        onPressedChanged: ((Boolean) -> Unit)? = null,
        onHoveredChanged: ((Boolean) -> Unit)? = null,
        onLongPress: (() -> Unit)? = null,
        onDoubleTap: (() -> Unit)? = null,
        onSwipeStart: (() -> Unit)? = null,
        onSwipeUpdate: ((Int) -> Unit)? = null,
        onSwipeEnd: ((Int) -> Unit)? = null,
        onSwipeLeft: (() -> Unit)? = null,
        onSwipeRight: (() -> Unit)? = null,
        preserveChildMinConstraints: Boolean = false,
        tightChildWidth: Boolean = false,
        tightChildHeight: Boolean = false,
        textInputState: PixelTextFieldState? = null,
        textInputController: PixelTextFieldController? = null,
        textInputReadOnly: Boolean = false,
        textInputAutofocus: Boolean = false,
        textInputMinLines: Int = 1,
        textInputMaxLines: Int = 1,
        textInputType: PixelInputType = PixelInputType.TEXT,
        textInputAction: PixelTextInputAction = PixelTextInputAction.DONE,
        textInputFocusNode: FocusNode? = null,
        textInputOnChanged: ((String) -> Unit)? = null,
        textInputOnSubmitted: ((String) -> Unit)? = null,
        textInputCursorColor: PixelColor? = null,
        textInputCursorVisible: Boolean = true,
        textInputCursorGap: Int = 0,
        textInputSelectionColor: PixelColor? = null,
        textInputCompositionColor: PixelColor? = null,
        textInputSelectionHandleColor: PixelColor? = null,
    ) {
        val coercedMinLines = textInputMinLines.coerceAtLeast(1)
        val coercedMaxLines = textInputMaxLines.coerceAtLeast(coercedMinLines)
        /** Runtime-safe border width used by equality and paint. */
        val coercedBorderWidth = borderWidth.coerceAtLeast(0)
        /** Runtime-safe stair-step radius used by equality and paint. */
        val coercedCornerRadius = cornerRadius.coerceAtLeast(0)
        /** A missing shadow color makes the offset layout-neutral. */
        val coercedShadowOffset = shadowOffset.coerceAtLeast(0).takeIf { shadowColor != null } ?: 0
        /** 负间隙没有排版含义，统一收敛为零。 */
        val coercedCursorGap = textInputCursorGap.coerceAtLeast(0)
        if (
            this.fillColor == fillColor &&
            this.borderColor == borderColor &&
            this.borderWidth == coercedBorderWidth &&
            this.cornerRadius == coercedCornerRadius &&
            this.shadowColor == shadowColor &&
            this.shadowOffset == coercedShadowOffset &&
            this.alignment == alignment &&
            this.explicitWidth == explicitWidth &&
            this.explicitHeight == explicitHeight &&
            this.fillMaxWidth == fillMaxWidth &&
            this.fillMaxHeight == fillMaxHeight &&
            this.outerPaddingLeft == outerPaddingLeft &&
            this.outerPaddingTop == outerPaddingTop &&
            this.outerPaddingRight == outerPaddingRight &&
            this.outerPaddingBottom == outerPaddingBottom &&
            this.contentPaddingLeft == contentPaddingLeft &&
            this.contentPaddingTop == contentPaddingTop &&
            this.contentPaddingRight == contentPaddingRight &&
            this.contentPaddingBottom == contentPaddingBottom &&
            this.onClick == onClick &&
            this.onPressedChanged == onPressedChanged &&
            this.onHoveredChanged == onHoveredChanged &&
            this.onLongPress == onLongPress &&
            this.onDoubleTap == onDoubleTap &&
            this.onSwipeStart == onSwipeStart &&
            this.onSwipeUpdate == onSwipeUpdate &&
            this.onSwipeEnd == onSwipeEnd &&
            this.onSwipeLeft == onSwipeLeft &&
            this.onSwipeRight == onSwipeRight &&
            this.preserveChildMinConstraints == preserveChildMinConstraints &&
            this.tightChildWidth == tightChildWidth &&
            this.tightChildHeight == tightChildHeight &&
            this.textInputState === textInputState &&
            this.textInputController === textInputController &&
            this.textInputReadOnly == textInputReadOnly &&
            this.textInputAutofocus == textInputAutofocus &&
            this.textInputMinLines == coercedMinLines &&
            this.textInputMaxLines == coercedMaxLines &&
            this.textInputType == textInputType &&
            this.textInputAction == textInputAction &&
            this.textInputFocusNode === textInputFocusNode &&
            this.textInputOnChanged == textInputOnChanged &&
            this.textInputOnSubmitted == textInputOnSubmitted &&
            this.textInputCursorColor == textInputCursorColor &&
            this.textInputCursorVisible == textInputCursorVisible &&
            this.textInputCursorGap == coercedCursorGap &&
            this.textInputSelectionColor == textInputSelectionColor &&
            this.textInputCompositionColor == textInputCompositionColor &&
            this.textInputSelectionHandleColor == textInputSelectionHandleColor
        ) {
            return
        }
        this.fillColor = fillColor
        this.borderColor = borderColor
        this.borderWidth = coercedBorderWidth
        this.cornerRadius = coercedCornerRadius
        this.shadowColor = shadowColor
        this.shadowOffset = coercedShadowOffset
        this.alignment = alignment
        this.explicitWidth = explicitWidth
        this.explicitHeight = explicitHeight
        this.fillMaxWidth = fillMaxWidth
        this.fillMaxHeight = fillMaxHeight
        this.outerPaddingLeft = outerPaddingLeft
        this.outerPaddingTop = outerPaddingTop
        this.outerPaddingRight = outerPaddingRight
        this.outerPaddingBottom = outerPaddingBottom
        this.contentPaddingLeft = contentPaddingLeft
        this.contentPaddingTop = contentPaddingTop
        this.contentPaddingRight = contentPaddingRight
        this.contentPaddingBottom = contentPaddingBottom
        this.onClick = onClick
        this.onPressedChanged = onPressedChanged
        this.onHoveredChanged = onHoveredChanged
        this.onLongPress = onLongPress
        this.onDoubleTap = onDoubleTap
        this.onSwipeStart = onSwipeStart
        this.onSwipeUpdate = onSwipeUpdate
        this.onSwipeEnd = onSwipeEnd
        this.onSwipeLeft = onSwipeLeft
        this.onSwipeRight = onSwipeRight
        this.preserveChildMinConstraints = preserveChildMinConstraints
        this.tightChildWidth = tightChildWidth
        this.tightChildHeight = tightChildHeight
        this.textInputState = textInputState
        this.textInputController = textInputController
        this.textInputReadOnly = textInputReadOnly
        this.textInputAutofocus = textInputAutofocus
        this.textInputMinLines = coercedMinLines
        this.textInputMaxLines = coercedMaxLines
        this.textInputType = textInputType
        this.textInputAction = textInputAction
        this.textInputFocusNode = textInputFocusNode
        this.textInputOnChanged = textInputOnChanged
        this.textInputOnSubmitted = textInputOnSubmitted
        this.textInputCursorColor = textInputCursorColor
        this.textInputCursorVisible = textInputCursorVisible
        this.textInputCursorGap = coercedCursorGap
        this.textInputSelectionColor = textInputSelectionColor
        this.textInputCompositionColor = textInputCompositionColor
        this.textInputSelectionHandleColor = textInputSelectionHandleColor
        markNeedsLayout()
        markNeedsPaint()
    }

    /**
     * 只更新文本光标显隐并请求重绘，不让纯闪烁变化触发布局。
     *
     * 该入口只供持有本 retained surface 的 Host 光标调度使用；文本、selection、样式或尺寸
     * 变化仍必须经过 [updateSurface] 的完整同步路径。
     */
    @PixelArtifactInternalApi
    public fun updateTextInputCursorVisibility(visible: Boolean) {
        if (textInputCursorVisible == visible) return
        textInputCursorVisible = visible
        markNeedsPaint()
    }

    /**
     * 按给定约束测量表面尺寸和子节点布局。
     */
    override fun layout(constraints: RenderConstraints) {
        val child = renderChild
        val currentExplicitWidth = explicitWidth
        val currentExplicitHeight = explicitHeight
        /** Diagonal hard-shadow extent participates in layout only when a shadow is painted. */
        val decorationExtent = shadowOffset.takeIf { shadowColor != null } ?: 0
        /** Horizontal space outside the child, including the hard shadow's right extent. */
        val horizontalInsets = outerPaddingLeft + outerPaddingRight +
            contentPaddingLeft + contentPaddingRight + decorationExtent
        /** Vertical space outside the child, including the hard shadow's bottom extent. */
        val verticalInsets = outerPaddingTop + outerPaddingBottom +
            contentPaddingTop + contentPaddingBottom + decorationExtent
        val childMaxWidth = when {
            currentExplicitWidth != null -> (currentExplicitWidth - contentPaddingLeft - contentPaddingRight).coerceAtLeast(0)
            else -> (constraints.maxWidth - horizontalInsets).coerceAtLeast(0)
        }
        val childMaxHeight = when {
            currentExplicitHeight != null -> (currentExplicitHeight - contentPaddingTop - contentPaddingBottom).coerceAtLeast(0)
            else -> (constraints.maxHeight - verticalInsets).coerceAtLeast(0)
        }
        val childMinWidth = when {
            tightChildWidth -> childMaxWidth
            preserveChildMinConstraints && currentExplicitWidth == null -> {
                (constraints.minWidth - horizontalInsets).coerceAtLeast(0).coerceAtMost(childMaxWidth)
            }
            else -> 0
        }
        val childMinHeight = when {
            tightChildHeight -> childMaxHeight
            preserveChildMinConstraints && currentExplicitHeight == null -> {
                (constraints.minHeight - verticalInsets).coerceAtLeast(0).coerceAtMost(childMaxHeight)
            }
            else -> 0
        }
        val childConstraints = RenderConstraints(
            minWidth = childMinWidth,
            maxWidth = childMaxWidth,
            minHeight = childMinHeight,
            maxHeight = childMaxHeight,
        )
        child?.layout(constraints = childConstraints)

        val childWidth = child?.size?.width ?: 0
        val childHeight = child?.size?.height ?: 0
        val measuredWidth = when {
            currentExplicitWidth != null -> currentExplicitWidth +
                outerPaddingLeft + outerPaddingRight + decorationExtent
            fillMaxWidth -> constraints.maxWidth
            else -> childWidth + horizontalInsets
        }
        val measuredHeight = when {
            currentExplicitHeight != null -> currentExplicitHeight +
                outerPaddingTop + outerPaddingBottom + decorationExtent
            fillMaxHeight -> constraints.maxHeight
            else -> childHeight + verticalInsets
        }

        size = RenderSize(
            width = constraints.constrainWidth(measuredWidth),
            height = constraints.constrainHeight(measuredHeight),
        )

        val contentWidth = (
            size.width - outerPaddingLeft - outerPaddingRight -
                contentPaddingLeft - contentPaddingRight - decorationExtent
        ).coerceAtLeast(0)
        val contentHeight = (
            size.height - outerPaddingTop - outerPaddingBottom -
                contentPaddingTop - contentPaddingBottom - decorationExtent
        ).coerceAtLeast(0)

        childOffsetX = outerPaddingLeft + contentPaddingLeft + resolveChildOffsetX(
            availableWidth = contentWidth,
            childWidth = childWidth,
        )
        childOffsetY = outerPaddingTop + contentPaddingTop + resolveChildOffsetY(
            availableHeight = contentHeight,
            childHeight = childHeight,
        )
    }

    /**
     * 把表面和子节点画到目标 buffer。
     */
    override fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        val child = renderChild
        val surfaceLeft = offsetX + outerPaddingLeft
        val surfaceTop = offsetY + outerPaddingTop
        /** Diagonal hard-shadow extent reserved on the surface's right and bottom edges. */
        val decorationExtent = shadowOffset.takeIf { shadowColor != null } ?: 0
        /** Main surface width excludes the measured hard-shadow extension. */
        val surfaceWidth = (
            size.width - outerPaddingLeft - outerPaddingRight - decorationExtent
        ).coerceAtLeast(0)
        /** Main surface height excludes the measured hard-shadow extension. */
        val surfaceHeight = (
            size.height - outerPaddingTop - outerPaddingBottom - decorationExtent
        ).coerceAtLeast(0)

        if (surfaceWidth > 0 && surfaceHeight > 0) {
            shadowColor?.takeIf { decorationExtent > 0 }?.let { color ->
                paintPixelFill(
                    context = context,
                    left = surfaceLeft + decorationExtent,
                    top = surfaceTop + decorationExtent,
                    width = surfaceWidth,
                    height = surfaceHeight,
                    radius = cornerRadius,
                    color = color,
                )
            }
            fillColor?.let { color ->
                paintPixelFill(
                    context = context,
                    left = surfaceLeft,
                    top = surfaceTop,
                    width = surfaceWidth,
                    height = surfaceHeight,
                    radius = cornerRadius,
                    color = color,
                )
            }
            borderColor?.takeIf { borderWidth > 0 }?.let { color ->
                paintPixelBorder(
                    context = context,
                    left = surfaceLeft,
                    top = surfaceTop,
                    width = surfaceWidth,
                    height = surfaceHeight,
                    radius = cornerRadius,
                    thickness = borderWidth,
                    color = color,
                )
            }
        }
        // 选区高亮先画，光标后画（光标盖在选区端点上更显眼）。
        paintTextInputSelection(context, child, offsetX, offsetY)
        child?.paint(
            context = context,
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
        )
        paintTextInputSelectionHandles(context, child, offsetX, offsetY)
        paintTextInputComposition(context, child, offsetX, offsetY)
        paintTextInputCursor(context, child, offsetX, offsetY)
    }

    /** Paints a square or stair-step rounded rectangle without anti-aliasing. */
    private fun paintPixelFill(
        context: PaintContext,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        radius: Int,
        color: PixelColor,
    ) {
        if (width <= 0 || height <= 0) return
        /** Radius constrained to half of the actual integer surface extent. */
        val safeRadius = radius.coerceIn(0, minOf(width, height) / 2)
        if (safeRadius == 0) {
            context.fillRect(left, top, width, height, color)
            return
        }
        repeat(height) { row ->
            /** Symmetric distance from this scanline to its nearest horizontal edge. */
            val edgeDistance = minOf(row, height - 1 - row)
            /** One fewer pixel is removed on each successive stair-step row. */
            val horizontalInset = (safeRadius - edgeDistance - 1).coerceAtLeast(0)
            /** Positive scanline width after both corner insets are applied. */
            val scanlineWidth = (width - horizontalInset * 2).coerceAtLeast(0)
            if (scanlineWidth > 0) {
                context.fillRect(left + horizontalInset, top + row, scanlineWidth, 1, color)
            }
        }
    }

    /** Paints a nested square or stair-step border while preserving an unpainted center. */
    private fun paintPixelBorder(
        context: PaintContext,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        radius: Int,
        thickness: Int,
        color: PixelColor,
    ) {
        if (width <= 0 || height <= 0 || thickness <= 0) return
        /** Layer count clipped before any nested rectangle becomes non-positive. */
        val layerCount = minOf(thickness, (minOf(width, height) + 1) / 2)
        repeat(layerCount) { layer ->
            /** Nested rectangle width for this exact one-pixel outline. */
            val layerWidth = width - layer * 2
            /** Nested rectangle height for this exact one-pixel outline. */
            val layerHeight = height - layer * 2
            /** Radius shrinks with the rectangle so stair steps remain aligned. */
            val layerRadius = (radius - layer).coerceAtLeast(0)
            paintPixelOutline(
                context = context,
                left = left + layer,
                top = top + layer,
                width = layerWidth,
                height = layerHeight,
                radius = layerRadius,
                color = color,
            )
        }
    }

    /** Paints one one-pixel square or stair-step rounded outline. */
    private fun paintPixelOutline(
        context: PaintContext,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        radius: Int,
        color: PixelColor,
    ) {
        if (width <= 0 || height <= 0) return
        /** Square one-pixel borders preserve the historical drawRect output exactly. */
        val safeRadius = radius.coerceIn(0, minOf(width, height) / 2)
        if (safeRadius == 0) {
            context.drawRect(left, top, width, height, color)
            return
        }
        repeat(height) { row ->
            /** Symmetric distance from this scanline to its nearest horizontal edge. */
            val edgeDistance = minOf(row, height - 1 - row)
            /** Current rounded outer span inset. */
            val outerInset = (safeRadius - edgeDistance - 1).coerceAtLeast(0)
            if (row == 0 || row == height - 1) {
                /** Horizontal top or bottom span of the rounded outline. */
                val spanWidth = (width - outerInset * 2).coerceAtLeast(0)
                if (spanWidth > 0) context.fillRect(left + outerInset, top + row, spanWidth, 1, color)
            } else {
                /** Left outline pixel for a middle scanline. */
                context.fillRect(left + outerInset, top + row, 1, 1, color)
                /** Right outline pixel when it differs from the left edge. */
                val right = left + width - 1 - outerInset
                if (right != left + outerInset) context.fillRect(right, top + row, 1, 1, color)
            }
        }
    }

    /**
     * 在文本输入聚焦时绘制 1px 光标。
     *
     * 行为：
     *  - 仅在 `textInputState?.isFocused == true` 且 [textInputCursorColor] 非空时绘制
     *  - 空文本：光标按占位文本的对齐语义绘制
     *  - 非空文本：光标按 selectionStart 的 caret 位置绘制
     *  - 光标高度 = child 当前测量高度
     *  - 可见性由 host frame loop 推进的 blink state 决定
     *  - 不参与 layout（不改 size）；不影响命中测试
     */
    private fun paintTextInputCursor(
        context: PaintContext,
        child: RenderBox?,
        offsetX: Int,
        offsetY: Int,
    ) {
        val state = textInputState ?: return
        val cursorColor = textInputCursorColor ?: return
        if (!state.isFocused) return
        if (!textInputCursorVisible) return
        child ?: return
        val cursorBaseX = offsetX + childOffsetX
        val cursorBaseY = offsetY + childOffsetY
        val caret = (child as? RenderText)?.textInputCaretRect(
            backingText = state.text,
            selectionStart = state.selectionStart,
            trailingCursorGap = textInputCursorGap,
        )
        val fallbackCaret = if (caret == null) resolveTextInputCaret(state.text, state.selectionStart, child) else 0L
        val cursorX = cursorBaseX + (caret?.x ?: caretX(fallbackCaret))
        val cursorY = cursorBaseY + (caret?.y ?: caretY(fallbackCaret))
        val cursorHeight = caret?.height ?: caretHeight(fallbackCaret)
        if (cursorHeight <= 0) return
        context.fillRect(cursorX, cursorY, 1, cursorHeight, cursorColor)
    }

    private fun resolveTextInputCaret(text: String, selectionStart: Int, child: RenderBox): Long {
        if (text.isEmpty()) {
            return packCaret(x = 0, y = 0, height = child.size.height)
        }
        val caretIndex = selectionStart.coerceIn(0, text.length)
        var lineCount = 1
        var lineIndex = 0
        var lineStart = 0
        var index = 0
        while (index < text.length) {
            if (text[index] == '\n') {
                lineCount += 1
                if (index < caretIndex) {
                    lineIndex += 1
                    lineStart = index + 1
                }
            }
            index += 1
        }
        var lineEnd = lineStart
        while (lineEnd < text.length && text[lineEnd] != '\n') {
            lineEnd += 1
        }
        val column = caretIndex - lineStart
        val lineHeight = (child.size.height / lineCount).coerceAtLeast(1)
        val lineLength = (lineEnd - lineStart).coerceAtLeast(1)
        val cursorX = if (lineEnd == lineStart) {
            0
        } else {
            (column.coerceIn(0, lineLength).toLong() * child.size.width / lineLength).toInt()
        }
        return packCaret(
            x = cursorX.coerceIn(0, child.size.width),
            y = lineIndex.coerceIn(0, lineCount - 1) * lineHeight,
            height = lineHeight,
        )
    }

    private fun packCaret(x: Int, y: Int, height: Int): Long {
        return (x.toLong() and 0x1FFFFFL) or
            ((y.toLong() and 0x1FFFFFL) shl 21) or
            ((height.toLong() and 0x1FFFFFL) shl 42)
    }

    private fun caretX(value: Long): Int = (value and 0x1FFFFFL).toInt()

    private fun caretY(value: Long): Int = ((value ushr 21) and 0x1FFFFFL).toInt()

    private fun caretHeight(value: Long): Int = ((value ushr 42) and 0x1FFFFFL).toInt()

    /**
     * 文本输入聚焦时为 IME composition 区段绘制 1px 下划线。
     *
     * V2 行为：
     *  - 仅在 `state.isFocused == true`、`textInputCompositionColor` 非空、
     *    `state.compositionStart >= 0 && state.compositionStart < state.compositionEnd`
     *    且 composition 范围都在 text 内时绘制
     *  - 按 RenderText 的行级文本映射拆分多行区段
     *  - 不参与 layout / 命中测试
     */
    private fun paintTextInputComposition(
        context: PaintContext,
        child: RenderBox?,
        offsetX: Int,
        offsetY: Int,
    ) {
        val state = textInputState ?: return
        val underlineColor = textInputCompositionColor ?: return
        if (!state.isFocused) return
        child ?: return
        val text = state.text
        if (text.isEmpty()) return
        val length = text.length
        val start = state.compositionStart
        val end = state.compositionEnd
        if (start < 0 || end <= start || start >= length) return
        val safeStart = start.coerceIn(0, length)
        val safeEnd = end.coerceIn(safeStart, length)
        if (safeStart >= safeEnd) return
        val baseX = offsetX + childOffsetX
        val baseY = offsetY + childOffsetY
        val rects = (child as? RenderText)?.textRangeRects(safeStart, safeEnd)
        if (!rects.isNullOrEmpty()) {
            rects.forEach { rect ->
                context.fillRect(baseX + rect.x, baseY + rect.y + rect.height - 1, rect.width, 1, underlineColor)
            }
            return
        }
        val textWidth = child.size.width
        val textHeight = child.size.height
        if (textWidth <= 0 || textHeight <= 0) return
        val startX = baseX + (safeStart.toLong() * textWidth / length).toInt()
        val endX = baseX + (safeEnd.toLong() * textWidth / length).toInt()
        context.fillRect(startX, baseY + textHeight - 1, (endX - startX).coerceAtLeast(1), 1, underlineColor)
    }

    /**
     * 文本输入聚焦时绘制非空选区的高亮填充。
     *
     * V2 行为：
     *  - 仅在 `textInputState?.isFocused == true`、selection 非空
     *    （selectionStart < selectionEnd 且都在 text 范围内）且
     *    [textInputSelectionColor] 非空时绘制
     *  - 按 RenderText 的行级文本映射拆分多行区段
     *  - 不参与 layout / 命中测试
     */
    private fun paintTextInputSelection(
        context: PaintContext,
        child: RenderBox?,
        offsetX: Int,
        offsetY: Int,
    ) {
        val state = textInputState ?: return
        val highlight = textInputSelectionColor ?: return
        if (!state.isFocused) return
        child ?: return
        val text = state.text
        if (text.isEmpty()) return
        val length = text.length
        val start = state.selectionStart.coerceIn(0, length)
        val end = state.selectionEnd.coerceIn(start, length)
        if (start >= end) return
        val baseX = offsetX + childOffsetX
        val baseY = offsetY + childOffsetY
        val rects = (child as? RenderText)?.textRangeRects(start, end)
        if (!rects.isNullOrEmpty()) {
            rects.forEach { rect ->
                context.fillRect(baseX + rect.x, baseY + rect.y, rect.width, rect.height, highlight)
            }
            return
        }
        val textWidth = child.size.width
        val textHeight = child.size.height
        if (textWidth <= 0 || textHeight <= 0) return
        val startX = baseX + (start.toLong() * textWidth / length).toInt()
        val endX = baseX + (end.toLong() * textWidth / length).toInt()
        context.fillRect(startX, baseY, (endX - startX).coerceAtLeast(1), textHeight, highlight)
    }

    /**
     * 文本输入选区两端的最小 1px handle。handle 不参与 layout 或命中测试，
     * 交互层使用文本位置映射更新 selection。
     */
    private fun paintTextInputSelectionHandles(
        context: PaintContext,
        child: RenderBox?,
        offsetX: Int,
        offsetY: Int,
    ) {
        val state = textInputState ?: return
        val color = textInputSelectionHandleColor ?: return
        if (!state.isFocused || textInputReadOnly) return
        child ?: return
        val text = state.text
        if (text.isEmpty()) return
        val length = text.length
        val start = state.selectionStart.coerceIn(0, length)
        val end = state.selectionEnd.coerceIn(start, length)
        if (start >= end) return
        val renderText = child as? RenderText
        val startCaret = renderText?.caretRect(start, PixelTextAffinity.DOWNSTREAM)
            ?: unpackFallbackCaret(text, start, child)
        val endCaret = renderText?.caretRect(end, PixelTextAffinity.UPSTREAM)
            ?: unpackFallbackCaret(text, end, child)
        val baseX = offsetX + childOffsetX
        val baseY = offsetY + childOffsetY
        paintSelectionHandle(context, baseX + startCaret.x, baseY + startCaret.y + startCaret.height, color)
        paintSelectionHandle(context, baseX + endCaret.x, baseY + endCaret.y + endCaret.height, color)
    }

    private fun unpackFallbackCaret(text: String, index: Int, child: RenderBox): PixelTextRangeRect {
        val caret = resolveTextInputCaret(text, index, child)
        return PixelTextRangeRect(
            x = caretX(caret),
            y = caretY(caret),
            width = 1,
            height = caretHeight(caret),
        )
    }

    private fun paintSelectionHandle(context: PaintContext, x: Int, y: Int, color: PixelColor) {
        context.fillRect(x, y - 1, 1, 2, color)
        context.fillRect(x - 1, y, 3, 1, color)
    }

    /**
     * 执行表面对象的命中测试。
     */
    override fun hitTest(
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) {
            return
        }
        renderChild?.hitTest(
            localX = localX - childOffsetX,
            localY = localY - childOffsetY,
            result = result,
        )
        if (hasPointerCallback()) {
            result.add(this)
        }
    }

    /**
     * 导出当前表面及其子树里的点击目标。
     */
    override fun collectClickTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelClickTarget>,
    ) {
        if (hasPointerCallback()) {
            targets += PixelClickTarget(
                bounds = PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                onClick = onClick ?: {},
                onPressedChanged = onPressedChanged,
                onHoveredChanged = onHoveredChanged,
                onLongPress = onLongPress,
                onDoubleTap = onDoubleTap,
                onSwipeStart = onSwipeStart,
                onSwipeUpdate = onSwipeUpdate,
                onSwipeEnd = onSwipeEnd,
                onSwipeLeft = onSwipeLeft,
                onSwipeRight = onSwipeRight,
                source = this,
            )
        }
        renderChild?.collectClickTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出当前表面子树里的分页目标。
     */
    override fun collectPagerTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelPagerTarget>,
    ) {
        renderChild?.collectPagerTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出当前表面子树里的列表滚动目标。
     */
    override fun collectListTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelListTarget>,
    ) {
        renderChild?.collectListTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    override fun collectRefreshTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelRefreshTarget>,
    ) {
        renderChild?.collectRefreshTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    /**
     * 导出当前表面及其子树里的文本输入目标。
     */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        val state = textInputState
        val controller = textInputController
        if (state != null && controller != null) {
            val renderText = renderChild as? RenderText
            val textBaseX = offsetX + childOffsetX
            val textBaseY = offsetY + childOffsetY
            targets += PixelTextInputTarget(
                bounds = PixelRect(
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                ),
                state = state,
                controller = controller,
                readOnly = textInputReadOnly,
                autofocus = textInputAutofocus,
                minLines = textInputMinLines,
                maxLines = textInputMaxLines,
                inputType = textInputType,
                action = textInputAction,
                focusNode = textInputFocusNode,
                onChanged = textInputOnChanged,
                onSubmitted = textInputOnSubmitted,
                textIndexAt = renderText?.let { text ->
                    { logicalX, logicalY ->
                        text.textIndexAt(
                            localX = logicalX - textBaseX,
                            localY = logicalY - textBaseY,
                        )
                    }
                },
                caretBoundsForIndex = renderText?.let { text ->
                    { index ->
                        val caret = text.caretRect(index)
                        PixelRect(
                            left = textBaseX + caret.x,
                            top = textBaseY + caret.y,
                            width = caret.width,
                            height = caret.height,
                        )
                    }
                },
                characterBoundsForRange = renderText?.let { text ->
                    { start, length ->
                        text.textCharacterRects(
                            backingText = state.text,
                            start = start,
                            length = length,
                        ).map { rect ->
                            rect?.let { characterRect ->
                                PixelRect(
                                    left = textBaseX + characterRect.x,
                                    top = textBaseY + characterRect.y,
                                    width = characterRect.width,
                                    height = characterRect.height,
                                )
                            }
                        }
                    }
                },
                source = this,
            )
        }
        renderChild?.collectTextInputTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    override fun collectSliderTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelSliderTarget>,
    ) {
        renderChild?.collectSliderTargets(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        val state = textInputState
        if (state != null) {
            /** The controller is required for direct accessibility mutation and focus actions. */
            val controller = textInputController
            /** Read-only surfaces keep their snapshot state but do not advertise mutation actions. */
            val semanticActions = if (controller != null && !textInputReadOnly) {
                PixelSemanticsActions(
                    onClick = {
                        controller.requestFocus(state)
                        true
                    },
                    onSetText = { nextText ->
                        controller.updateText(state = state, text = nextText)
                        textInputOnChanged?.invoke(nextText)
                        true
                    },
                    onSetSelection = { start, end ->
                        if (start < 0 || end < start || end > state.text.length) {
                            false
                        } else {
                            controller.setSelection(state = state, selectionStart = start, selectionEnd = end)
                            true
                        }
                    },
                )
            } else {
                PixelSemanticsActions()
            }
            targets += PixelSemanticsTarget(
                node = PixelSemanticsNode(
                    label = state.text.ifEmpty { "" },
                    role = PixelSemanticRole.TEXT_FIELD,
                    enabled = textInputCursorColor != null || !textInputReadOnly,
                    focused = state.isFocused,
                    left = offsetX,
                    top = offsetY,
                    width = size.width,
                    height = size.height,
                    id = semanticNodeId(),
                    value = state.text,
                    selectionStart = state.selectionStart,
                    selectionEnd = state.selectionEnd,
                    actions = semanticActions.capabilitySet(),
                ),
                source = this,
                actions = semanticActions,
                characterBoundsForRange = (renderChild as? RenderText)?.let { text ->
                    /** Absolute logical text origin retained by this semantic frame. */
                    val textBaseX = offsetX + childOffsetX
                    /** Absolute logical text origin retained by this semantic frame. */
                    val textBaseY = offsetY + childOffsetY
                    { start, length ->
                        text.textCharacterRects(
                            backingText = state.text,
                            start = start,
                            length = length,
                        ).map { rect ->
                            rect?.let { characterRect ->
                                PixelRect(
                                    left = textBaseX + characterRect.x,
                                    top = textBaseY + characterRect.y,
                                    width = characterRect.width,
                                    height = characterRect.height,
                                )
                            }
                        }
                    }
                },
            )
            // The rendered text is the field's visual value, not an independently spoken node.
            return
        }
        renderChild?.collectSemantics(
            offsetX = offsetX + childOffsetX,
            offsetY = offsetY + childOffsetY,
            targets = targets,
        )
    }

    private fun resolveChildOffsetX(availableWidth: Int, childWidth: Int): Int {
        val freeWidth = (availableWidth - childWidth).coerceAtLeast(0)
        return when (alignment) {
            PixelAlignment.TOP_CENTER,
            PixelAlignment.CENTER,
            PixelAlignment.BOTTOM_CENTER,
            -> freeWidth / 2

            PixelAlignment.TOP_END,
            PixelAlignment.CENTER_END,
            PixelAlignment.BOTTOM_END,
            -> freeWidth

            else -> 0
        }
    }

    private fun resolveChildOffsetY(availableHeight: Int, childHeight: Int): Int {
        val freeHeight = (availableHeight - childHeight).coerceAtLeast(0)
        return when (alignment) {
            PixelAlignment.CENTER_START,
            PixelAlignment.CENTER,
            PixelAlignment.CENTER_END,
            -> freeHeight / 2

            PixelAlignment.BOTTOM_START,
            PixelAlignment.BOTTOM_CENTER,
            PixelAlignment.BOTTOM_END,
            -> freeHeight

            else -> 0
        }
    }

    private fun hasPointerCallback(): Boolean {
        return onClick != null ||
            onPressedChanged != null ||
            onHoveredChanged != null ||
            onLongPress != null ||
            onDoubleTap != null ||
            onSwipeStart != null ||
            onSwipeUpdate != null ||
            onSwipeEnd != null ||
            onSwipeLeft != null ||
            onSwipeRight != null
    }

    private val renderChild: RenderBox?
        get() = child as? RenderBox
}
