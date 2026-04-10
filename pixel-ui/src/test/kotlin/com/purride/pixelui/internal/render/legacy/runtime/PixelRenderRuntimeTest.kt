package com.purride.pixelui.internal

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Center
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Expanded
import com.purride.pixelui.FlexFit
import com.purride.pixelui.Flexible
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Align
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.Padding
import com.purride.pixelui.PaddingDirectional
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Spacer
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextStyle
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.Theme
import com.purride.pixelui.ThemeData
import com.purride.pixelui.ContainerStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelBoxNode
import com.purride.pixelui.internal.legacy.PixelButton
import com.purride.pixelui.internal.legacy.PixelButtonNode
import com.purride.pixelui.internal.legacy.PixelColumn
import com.purride.pixelui.internal.legacy.PixelColumnNode
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelCrossAxisAlignment
import com.purride.pixelui.internal.legacy.PixelMainAxisAlignment
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelNode
import com.purride.pixelui.internal.legacy.PixelList
import com.purride.pixelui.internal.legacy.PixelListNode
import com.purride.pixelui.internal.legacy.PixelPagerNode
import com.purride.pixelui.internal.legacy.PixelPager
import com.purride.pixelui.internal.legacy.PixelRow
import com.purride.pixelui.internal.legacy.PixelRowNode
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollView
import com.purride.pixelui.internal.legacy.PixelSingleChildScrollViewNode
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.PixelSurfaceNode
import com.purride.pixelui.internal.legacy.PixelTextAlign
import com.purride.pixelui.internal.legacy.PixelTextField
import com.purride.pixelui.internal.legacy.PixelTextFieldNode
import com.purride.pixelui.internal.legacy.PixelText
import com.purride.pixelui.internal.legacy.PixelTextNode
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.legacy.fillMaxSize
import com.purride.pixelui.internal.legacy.fillMaxWidth
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.internal.legacy.weight
import com.purride.pixelui.internal.legacy.width
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelcore.PixelTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun PixelNode.withTestModifier(extra: PixelModifier): PixelNode {
    val merged = modifier.then(extra)
    return when (this) {
        is PixelTextNode -> copy(modifier = merged)
        is PixelSurfaceNode -> copy(modifier = merged)
        is PixelBoxNode -> copy(modifier = merged)
        is PixelRowNode -> copy(modifier = merged)
        is PixelColumnNode -> copy(modifier = merged)
        is PixelPagerNode -> copy(modifier = merged)
        is PixelListNode -> copy(modifier = merged)
        is PixelSingleChildScrollViewNode -> copy(modifier = merged)
        is PixelTextFieldNode -> copy(modifier = merged)
        is PixelButtonNode -> copy(modifier = merged)
        is CustomDraw -> copy(modifier = merged)
        else -> this
    }
}

private data class TestModifierWidget(
    override val key: Any? = null,
    override val child: Widget,
    val modifier: PixelModifier,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        val modifierInfo = PixelModifierSupport.resolve(modifier)
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            contentPaddingLeft = modifierInfo.paddingLeft,
            contentPaddingTop = modifierInfo.paddingTop,
            contentPaddingRight = modifierInfo.paddingRight,
            contentPaddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
            tightChildWidth = modifierInfo.fixedWidth != null || modifierInfo.fillMaxWidth,
            tightChildHeight = modifierInfo.fixedHeight != null || modifierInfo.fillMaxHeight,
        )
    }

    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        val modifierInfo = PixelModifierSupport.resolve(modifier)
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            explicitWidth = modifierInfo.fixedWidth,
            explicitHeight = modifierInfo.fixedHeight,
            fillMaxWidth = modifierInfo.fillMaxWidth,
            fillMaxHeight = modifierInfo.fillMaxHeight,
            contentPaddingLeft = modifierInfo.paddingLeft,
            contentPaddingTop = modifierInfo.paddingTop,
            contentPaddingRight = modifierInfo.paddingRight,
            contentPaddingBottom = modifierInfo.paddingBottom,
            onClick = modifierInfo.onClick,
            tightChildWidth = modifierInfo.fixedWidth != null || modifierInfo.fillMaxWidth,
            tightChildHeight = modifierInfo.fixedHeight != null || modifierInfo.fillMaxHeight,
        )
    }
}

private fun withLegacyModifier(
    child: Widget,
    modifier: PixelModifier,
    key: Any? = null,
): Widget {
    return if (modifier == PixelModifier.Empty) {
        child
    } else {
        TestModifierWidget(
            key = key,
            child = child,
            modifier = modifier,
        )
    }
}

private fun Padding(
    child: Widget,
    all: Int,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Padding(
        child = child,
        all = all,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Align(
        child = child,
        alignment = alignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun AlignDirectional(
    child: Widget,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.AlignDirectional(
        child = child,
        alignment = alignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.GestureDetector(
        child = child,
        onTap = onTap,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Text(
    data: String,
    modifier: PixelModifier,
    style: TextStyle = TextStyle.Default,
    theme: ThemeData? = null,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: TextAlign = TextAlign.START,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Text(
        data = data,
        style = style,
        theme = theme,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun DecoratedBox(
    child: Widget? = null,
    modifier: PixelModifier,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    padding: Int = 2,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.DecoratedBox(
        child = child,
        fillTone = fillTone,
        borderTone = borderTone,
        padding = padding,
        alignment = alignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Container(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    modifier: PixelModifier,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Container(
        child = child,
        width = width,
        height = height,
        padding = padding,
        margin = margin,
        style = style,
        theme = theme,
        fillTone = fillTone,
        borderTone = borderTone,
        alignment = alignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Stack(
    children: List<Widget>,
    modifier: PixelModifier,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Stack(
        children = children,
        alignment = alignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Positioned(
    child: Widget,
    left: Int? = null,
    top: Int? = null,
    right: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Positioned(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        width = width,
        height = height,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun PositionedDirectional(
    child: Widget,
    start: Int? = null,
    top: Int? = null,
    end: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.PositionedDirectional(
        child = child,
        start = start,
        top = top,
        end = end,
        bottom = bottom,
        width = width,
        height = height,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun PositionedFill(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    modifier: PixelModifier,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.PositionedFill(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Row(
    children: List<Widget>,
    modifier: PixelModifier,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Row(
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun Column(
    children: List<Widget>,
    modifier: PixelModifier,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.Column(
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    modifier: PixelModifier,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.TextField(
        state = state,
        controller = controller,
        placeholder = placeholder,
        style = style,
        theme = theme,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

private fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    modifier: PixelModifier,
    style: ButtonStyle = ButtonStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    key: Any? = null,
): Widget = withLegacyModifier(
    child = com.purride.pixelui.OutlinedButton(
        text = text,
        onPressed = onPressed,
        style = style,
        theme = theme,
        enabled = enabled,
        key = key,
    ),
    modifier = modifier,
    key = key,
)

class PixelRenderRuntimeTest {

    private val runtime = createLegacyFallbackRuntime()

    @Test
    fun surfaceCentersChildWithinPadding() {
        val result = runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(20, 20),
                padding = 2,
                alignment = PixelAlignment.CENTER,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText("A"),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val pixels = collectOnPixels(result)
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        assertTrue(minX >= 7)
        assertTrue(maxX <= 12)
        assertTrue(minY >= 6)
    }

    @Test
    fun paddingWithEdgeInsetsOffsetsChildBySpecifiedSides() {
        val result = runtime.render(
            root = Padding(
                padding = EdgeInsets.only(left = 3, top = 2),
                child = Text("A"),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val pixels = collectOnPixels(result)
        assertTrue(pixels.minOf { it.first } >= 3)
        assertTrue(pixels.minOf { it.second } >= 2)
    }

    @Test
    fun containerAppliesPaddingWithoutExposingSurfaceInternals() {
        val result = runtime.render(
            root = Container(
                width = 20,
                height = 12,
                padding = EdgeInsets.all(2),
                fillTone = PixelTone.OFF,
                borderTone = null,
                child = Text("A"),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        val pixels = collectOnPixels(result)
        assertTrue(pixels.minOf { it.first } >= 2)
        assertTrue(pixels.minOf { it.second } >= 2)
    }

    @Test
    fun columnStacksChildrenVerticallyWithSpacing() {
        val result = runtime.render(
            root = PixelColumn(
                spacing = 3,
                children = listOf(
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 6)),
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 6)),
                ),
            ),
            logicalWidth = 30,
            logicalHeight = 30,
        )

        val clickFreeBounds = collectFilledRows(result)
        assertTrue(clickFreeBounds.contains(0))
        assertTrue(clickFreeBounds.contains(5))
        assertTrue(clickFreeBounds.contains(9))
        assertTrue(clickFreeBounds.contains(14))
    }

    @Test
    fun rowWeightDistributesRemainingWidth() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(20, 4),
                spacing = 2,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.weight(1f).height(4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(18, 1))
    }

    @Test
    fun columnWeightDistributesRemainingHeight() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(6, 20),
                spacing = 2,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(6, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.fillMaxWidth().weight(1f),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 6,
            logicalHeight = 20,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 18))
    }

    @Test
    fun expandedUsesTightFitAndOccupiesRemainingWidth() {
        val result = runtime.render(
            root = Row(
                children = listOf(
                    SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                    ),
                    Expanded(
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
                spacing = 2,
            ),
            logicalWidth = 20,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(18, 1))
    }

    @Test
    fun flexibleLooseKeepsChildAtMeasuredWidthWithinAllocatedSlot() {
        val result = runtime.render(
            root = Row(
                children = listOf(
                    SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                    ),
                    Flexible(
                        fit = FlexFit.LOOSE,
                        child = SizedBox(
                            width = 6,
                            height = 4,
                            child = DecoratedBox(
                                fillTone = PixelTone.ACCENT,
                                borderTone = null,
                            ),
                        ),
                    ),
                ),
                spacing = 2,
            ),
            logicalWidth = 20,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(7, 1))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(18, 1))
    }

    @Test
    fun rowCrossAxisCenterAlignsChildrenVertically() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(12, 10),
                crossAxisAlignment = PixelCrossAxisAlignment.CENTER,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 2))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 3))
    }

    @Test
    fun columnCrossAxisEndAlignsChildrenHorizontally() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(10, 12),
                crossAxisAlignment = PixelCrossAxisAlignment.END,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(5, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(6, 1))
    }

    @Test
    fun rowCrossAxisStretchExpandsChildToContainerHeight() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(10, 8),
                crossAxisAlignment = PixelCrossAxisAlignment.STRETCH,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.width(4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 8,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 0))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 7))
    }

    @Test
    fun columnCrossAxisStretchExpandsChildToContainerWidth() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(10, 8),
                crossAxisAlignment = PixelCrossAxisAlignment.STRETCH,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.height(4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 8,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 1))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(9, 1))
    }

    @Test
    fun rowMainAxisCenterAlignsChildrenHorizontally() {
        val result = runtime.render(
            root = PixelRow(
                modifier = PixelModifier.Empty.size(12, 4),
                mainAxisAlignment = PixelMainAxisAlignment.CENTER,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(3, 1))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(4, 1))
    }

    @Test
    fun columnMainAxisEndAlignsChildrenVertically() {
        val result = runtime.render(
            root = PixelColumn(
                modifier = PixelModifier.Empty.size(4, 12),
                mainAxisAlignment = PixelMainAxisAlignment.END,
                children = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(4, 4),
                        borderTone = null,
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 7))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 8))
    }

    @Test
    fun rowMainAxisMaxExpandsToAvailableWidth() {
        val result = runtime.render(
            root = Row(
                mainAxisSize = MainAxisSize.MAX,
                children = listOf(
                    DecoratedBox(
                        fillTone = PixelTone.ACCENT,
                        borderTone = null,
                        child = SizedBox(width = 4, height = 4),
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(11, 1))
    }

    @Test
    fun columnMainAxisMaxExpandsToAvailableHeight() {
        val result = runtime.render(
            root = Column(
                mainAxisSize = MainAxisSize.MAX,
                children = listOf(
                    DecoratedBox(
                        fillTone = PixelTone.ON,
                        borderTone = null,
                        child = SizedBox(width = 4, height = 4),
                    ),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 11))
    }

    @Test
    fun rowMainAxisSpaceBetweenDistributesChildrenAcrossWidth() {
        val result = runtime.render(
            root = Row(
                modifier = PixelModifier.Empty.size(12, 4),
                mainAxisAlignment = MainAxisAlignment.SPACE_BETWEEN,
                children = listOf(
                    Container(
                        width = 2,
                        height = 4,
                        fillTone = PixelTone.ON,
                        borderTone = null,
                    ),
                    Container(
                        width = 2,
                        height = 4,
                        fillTone = PixelTone.ACCENT,
                        borderTone = null,
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 1))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(5, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(10, 1))
    }

    @Test
    fun columnMainAxisSpaceEvenlyAddsTopAndBottomGaps() {
        val result = runtime.render(
            root = Column(
                modifier = PixelModifier.Empty.size(4, 12),
                mainAxisAlignment = MainAxisAlignment.SPACE_EVENLY,
                children = listOf(
                    Container(
                        width = 4,
                        height = 2,
                        fillTone = PixelTone.ON,
                        borderTone = null,
                    ),
                    Container(
                        width = 4,
                        height = 2,
                        fillTone = PixelTone.ACCENT,
                        borderTone = null,
                    ),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 0))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 2))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 6))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 11))
    }

    @Test
    fun pagerExportsClickTargetsFromCurrentPage() {
        var clicked = false
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)

        val result = runtime.render(
            root = PixelPager(
                axis = PixelAxis.HORIZONTAL,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 20),
                pages = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty
                            .size(10, 10)
                            .clickable { clicked = true },
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(10, 10),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        assertEquals(1, result.clickTargets.size)
        assertTrue(result.clickTargets.first().bounds.contains(5, 5))
        result.clickTargets.first().onClick.invoke()
        assertTrue(clicked)
    }

    @Test
    fun pagerExportsPageChangedCallback() {
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)
        var changedPage = -1

        val result = runtime.render(
            root = PixelPager(
                axis = PixelAxis.HORIZONTAL,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 20),
                onPageChanged = { page -> changedPage = page },
                pages = listOf(
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 10)),
                    PixelSurface(modifier = PixelModifier.Empty.size(10, 10)),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        assertEquals(1, result.pagerTargets.size)
        result.pagerTargets.first().onPageChanged?.invoke(1)
        assertEquals(1, changedPage)
    }

    @Test
    fun flutterStyleAliasesRenderThroughCompatibilityLayer() {
        val result = runtime.render(
            root = Column(
                modifier = PixelModifier.Empty.size(20, 10),
                spacing = 2,
                mainAxisAlignment = MainAxisAlignment.START,
                crossAxisAlignment = CrossAxisAlignment.START,
                children = listOf(
                    DecoratedBox(
                        modifier = PixelModifier.Empty.fillMaxWidth().height(4),
                        borderTone = null,
                        fillTone = PixelTone.ON,
                        alignment = Alignment.CENTER,
                        child = Row(
                            mainAxisAlignment = MainAxisAlignment.START,
                            crossAxisAlignment = CrossAxisAlignment.START,
                            children = listOf(
                                Text("A"),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertTrue(collectOnPixels(result).isNotEmpty())
    }

    @Test
    fun flutterStylePaddingAndCenterWrapExistingLayoutSemantics() {
        val result = runtime.render(
            root = Padding(
                all = 2,
                modifier = PixelModifier.Empty.size(12, 12),
                child = Center(
                    child = SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 12,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(4, 4))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(7, 7))
    }

    @Test
    fun flutterStyleContainerMarginOffsetsDecoratedBox() {
        val result = runtime.render(
            root = Container(
                width = 4,
                height = 4,
                margin = EdgeInsets.only(left = 2, top = 3),
                fillTone = PixelTone.ACCENT,
                borderTone = null,
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(2, 3))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(5, 6))
    }

    @Test
    fun flutterStyleThemeDataFeedsDefaultTextButtonAndContainerStyles() {
        val themed = ThemeData(
            textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            buttonStyle = PixelButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            ),
            containerStyle = ContainerStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                alignment = Alignment.CENTER,
            ),
        )

        val result = runtime.render(
            root = Theme(
                data = themed,
                child = Column(
                    modifier = PixelModifier.Empty.size(24, 20),
                    spacing = 2,
                    children = listOf(
                        Container(
                            width = 24,
                            height = 8,
                            child = Text(
                                data = "THEMED",
                            ),
                        ),
                        OutlinedButton(
                            text = "BTN",
                            onPressed = { },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(10),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 20,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertTrue(collectOnPixels(result).isNotEmpty())
    }

    @Test
    fun flutterStyleThemeDataFeedsAccentAndDisabledBranches() {
        val themed = ThemeData(
            accentTextStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            accentButtonStyle = PixelButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            ),
            disabledButtonStyle = PixelButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            ),
        )

        val result = runtime.render(
            root = Theme(
                data = themed,
                child = Column(
                    modifier = PixelModifier.Empty.size(24, 20),
                    spacing = 2,
                    children = listOf(
                        Text(
                            data = "ACCENT",
                            style = TextStyle.Accent,
                        ),
                        OutlinedButton(
                            text = "DISABLED",
                            onPressed = null,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(10),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 20,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 9))
        assertTrue(collectOnPixels(result).isNotEmpty())
    }

    @Test
    fun flutterStyleThemeDataFeedsReadOnlyTextFieldStyle() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "READ")
        val themed = ThemeData(
            readOnlyTextFieldStyle = PixelTextFieldStyle.Default.copy(
                readOnlyBorderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle.Accent,
            ),
        )

        val result = runtime.render(
            root = Theme(
                data = themed,
                child = TextField(
                    state = state,
                    controller = controller,
                    modifier = PixelModifier.Empty.size(20, 10),
                    readOnly = true,
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertEquals(1, result.textInputTargets.size)
        assertTrue(result.textInputTargets.first().readOnly)
    }

    @Test
    fun flutterStyleThemeWrapsMixedSubtreeDefaults() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "VALUE")
        val themed = ThemeData(
            textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            buttonStyle = PixelButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            ),
            textFieldStyle = PixelTextFieldStyle.Default.copy(
                borderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
            ),
            containerStyle = ContainerStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                alignment = Alignment.CENTER,
            ),
        )

        val result = runtime.render(
            root = Theme(
                data = themed,
                child = Column(
                    modifier = PixelModifier.Empty.size(24, 30),
                    spacing = 2,
                    children = listOf(
                        Container(
                            width = 24,
                            height = 8,
                            child = Text("TITLE"),
                        ),
                        TextField(
                            state = state,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(8),
                        ),
                        OutlinedButton(
                            text = "ACTION",
                            onPressed = { },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(10),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 30,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 10))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 20))
    }

    @Test
    fun localThemeOverridesHostThemeDefaults() {
        val result = runtime.render(
            root = Theme(
                data = ThemeData(
                    textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
                    buttonStyle = PixelButtonStyle(
                        fillTone = PixelTone.OFF,
                        borderTone = PixelTone.ACCENT,
                        textStyle = PixelTextStyle(tone = PixelTone.ACCENT),
                    ),
                ),
                child = Column(
                    modifier = PixelModifier.Empty.size(24, 24),
                    spacing = 2,
                    children = listOf(
                        OutlinedButton(
                            text = "OUTER",
                            onPressed = { },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(10),
                        ),
                        Theme(
                            data = ThemeData(
                                textStyle = TextStyle.Default,
                                buttonStyle = ButtonStyle.Default,
                            ),
                            child = OutlinedButton(
                                text = "INNER",
                                onPressed = { },
                                modifier = PixelModifier.Empty.fillMaxWidth().height(10),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 12))
    }

    @Test
    fun flutterStyleSpacerPushesTrailingChild() {
        val result = runtime.render(
            root = Row(
                modifier = PixelModifier.Empty.size(20, 4),
                children = listOf(
                    SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                    ),
                    Spacer(),
                    SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 4,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(10, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(17, 1))
    }

    @Test
    fun flutterStyleAlignPlacesChildAtTopStart() {
        val result = runtime.render(
            root = Align(
                alignment = Alignment.TOP_START,
                modifier = PixelModifier.Empty.size(10, 10),
                child = SizedBox(
                    width = 4,
                    height = 4,
                    child = DecoratedBox(
                        fillTone = PixelTone.ON,
                        borderTone = null,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 0))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(5, 5))
    }

    @Test
    fun flutterStyleAlignPlacesChildAtBottomEnd() {
        val result = runtime.render(
            root = Align(
                alignment = Alignment.BOTTOM_END,
                modifier = PixelModifier.Empty.size(10, 10),
                child = SizedBox(
                    width = 4,
                    height = 4,
                    child = DecoratedBox(
                        fillTone = PixelTone.ACCENT,
                        borderTone = null,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(4, 4))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(9, 9))
    }

    @Test
    fun flutterStyleAlignPlacesChildAtCenterStart() {
        val result = runtime.render(
            root = Align(
                alignment = Alignment.CENTER_START,
                modifier = PixelModifier.Empty.size(10, 10),
                child = SizedBox(
                    width = 4,
                    height = 4,
                    child = DecoratedBox(
                        fillTone = PixelTone.ON,
                        borderTone = null,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 5))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(6, 1))
    }

    @Test
    fun flutterStyleTextAlignCenterCentersTextWithinWidth() {
        val result = runtime.render(
            root = SizedBox(
                width = 20,
                height = 8,
                child = Text(
                    data = "A",
                    textAlign = TextAlign.CENTER,
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 8,
        )

        val pixels = collectOnPixels(result)
        assertTrue(pixels.minOf { it.first } >= 7)
        assertTrue(pixels.maxOf { it.first } <= 12)
    }

    @Test
    fun flutterStyleTextAlignEndPlacesTextAtRightEdge() {
        val result = runtime.render(
            root = SizedBox(
                width = 20,
                height = 8,
                child = Text(
                    data = "A",
                    textAlign = TextAlign.END,
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 8,
        )

        val pixels = collectOnPixels(result)
        assertTrue(pixels.maxOf { it.first } >= 17)
    }

    @Test
    fun flutterStyleTextAlignStartUsesRightEdgeInRtl() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = SizedBox(
                    width = 20,
                    height = 8,
                    child = Text(
                        data = "A",
                        textAlign = TextAlign.START,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 8,
        )

        val pixels = collectOnPixels(result)
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        assertTrue("RTL START minX=$minX maxX=$maxX", minX >= 14)
        assertTrue("RTL START minX=$minX maxX=$maxX", maxX >= 16)
    }

    @Test
    fun flutterStyleTextAlignEndUsesLeftEdgeInRtl() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = SizedBox(
                    width = 20,
                    height = 8,
                    child = Text(
                        data = "A",
                        textAlign = TextAlign.END,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 8,
        )

        val pixels = collectOnPixels(result)
        assertTrue(pixels.minOf { it.first } <= 2)
    }

    @Test
    fun flutterStyleRowMainAxisStartUsesRightEdgeInRtl() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = SizedBox(
                    width = 20,
                    height = 8,
                    child = Row(
                        mainAxisSize = MainAxisSize.MAX,
                        mainAxisAlignment = MainAxisAlignment.START,
                        children = listOf(
                            SizedBox(
                                width = 4,
                                height = 4,
                                child = DecoratedBox(
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 8,
        )

        assertTrue(hasTone(result, PixelTone.ACCENT, minX = 16, maxX = 19, minY = 0, maxY = 7))
    }

    @Test
    fun flutterStyleColumnCrossAxisStartUsesRightEdgeInRtl() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = SizedBox(
                    width = 20,
                    height = 12,
                    child = Column(
                        modifier = PixelModifier.Empty.fillMaxWidth(),
                        crossAxisAlignment = CrossAxisAlignment.START,
                        children = listOf(
                            SizedBox(
                                width = 4,
                                height = 4,
                                child = DecoratedBox(
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertTrue(hasTone(result, PixelTone.ACCENT, minX = 16, maxX = 19, minY = 0, maxY = 11))
    }

    @Test
    fun flutterStylePositionedPlacesChildAtExplicitTopLeft() {
        val result = runtime.render(
            root = Stack(
                children = listOf(
                    Positioned(
                        left = 3,
                        top = 2,
                        child = SizedBox(
                            width = 4,
                            height = 4,
                            child = DecoratedBox(
                                fillTone = PixelTone.ACCENT,
                                borderTone = null,
                            ),
                        ),
                    ),
                ),
                modifier = PixelModifier.Empty.size(12, 10),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(2, 2))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(3, 2))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(6, 5))
    }

    @Test
    fun flutterStylePositionedUsesRightAndBottomOffsets() {
        val result = runtime.render(
            root = Stack(
                children = listOf(
                    Positioned(
                        right = 1,
                        bottom = 2,
                        width = 4,
                        height = 3,
                        child = DecoratedBox(
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                    ),
                ),
                modifier = PixelModifier.Empty.size(12, 10),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(6, 4))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(7, 5))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(10, 7))
    }

    @Test
    fun flutterStylePositionedFillExpandsChildAcrossAvailableArea() {
        val result = runtime.render(
            root = Stack(
                children = listOf(
                    PositionedFill(
                        left = 1,
                        top = 2,
                        right = 1,
                        bottom = 2,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
                modifier = PixelModifier.Empty.size(12, 10),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(0, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(1, 2))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(10, 7))
        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(11, 8))
    }

    @Test
    fun flutterStylePositionedDirectionalUsesStartAsLeftInLtr() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.LTR,
                child = Stack(
                    children = listOf(
                        PositionedDirectional(
                            start = 2,
                            top = 1,
                            child = SizedBox(
                                width = 4,
                                height = 4,
                                child = DecoratedBox(
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = null,
                                ),
                            ),
                        ),
                    ),
                    modifier = PixelModifier.Empty.size(12, 10),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 1))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(2, 1))
    }

    @Test
    fun flutterStylePositionedDirectionalUsesStartAsRightInRtl() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = Stack(
                    children = listOf(
                        PositionedDirectional(
                            start = 2,
                            top = 1,
                            width = 4,
                            height = 4,
                            child = DecoratedBox(
                                fillTone = PixelTone.ON,
                                borderTone = null,
                            ),
                        ),
                    ),
                    modifier = PixelModifier.Empty.size(12, 10),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(5, 1))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(6, 1))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(9, 4))
    }

    @Test
    fun flutterStyleAlignDirectionalResolvesStartByDirectionality() {
        val ltr = runtime.render(
            root = Directionality(
                textDirection = TextDirection.LTR,
                child = AlignDirectional(
                    alignment = AlignmentDirectional.TOP_START,
                    modifier = PixelModifier.Empty.size(10, 10),
                    child = SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )
        val rtl = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = AlignDirectional(
                    alignment = AlignmentDirectional.TOP_START,
                    modifier = PixelModifier.Empty.size(10, 10),
                    child = SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ACCENT.value, ltr.buffer.getPixel(0, 0))
        assertEquals(PixelTone.OFF.value, rtl.buffer.getPixel(0, 0))
        assertEquals(PixelTone.ACCENT.value, rtl.buffer.getPixel(9, 0))
    }

    @Test
    fun flutterStyleContainerDirectionalAlignsChildUsingDirectionality() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = ContainerDirectional(
                    width = 12,
                    height = 8,
                    alignment = AlignmentDirectional.CENTER_START,
                    child = SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ON,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 12,
            logicalHeight = 8,
        )

        assertEquals(PixelTone.OFF.value, result.buffer.getPixel(1, 2))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(8, 2))
    }

    @Test
    fun flutterStylePaddingDirectionalResolvesStartAndEndByDirectionality() {
        val ltr = runtime.render(
            root = Directionality(
                textDirection = TextDirection.LTR,
                child = PaddingDirectional(
                    padding = EdgeInsetsDirectional.only(start = 3, top = 1),
                    child = Text("A"),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )
        val rtl = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = PaddingDirectional(
                    padding = EdgeInsetsDirectional.only(start = 3, top = 1),
                    child = Text("A"),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertTrue(collectOnPixels(ltr).minOf { it.first } >= 3)
        assertTrue(collectOnPixels(rtl).maxOf { it.first } <= 16)
    }

    @Test
    fun flutterStyleContainerDirectionalResolvesDirectionalPaddingAndMargin() {
        val result = runtime.render(
            root = Directionality(
                textDirection = TextDirection.RTL,
                child = ContainerDirectional(
                    width = 12,
                    height = 8,
                    alignment = AlignmentDirectional.TOP_START,
                    paddingDirectional = EdgeInsetsDirectional.only(start = 2),
                    marginDirectional = EdgeInsetsDirectional.only(start = 1),
                    child = SizedBox(
                        width = 4,
                        height = 4,
                        child = DecoratedBox(
                            fillTone = PixelTone.ACCENT,
                            borderTone = null,
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        val accentPixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until result.buffer.height) {
            for (x in 0 until result.buffer.width) {
                if (result.buffer.getPixel(x, y).toInt() == PixelTone.ACCENT.value.toInt()) {
                    accentPixels += x to y
                }
            }
        }

        assertTrue(accentPixels.minOf { it.first } >= 6)
        assertTrue(accentPixels.maxOf { it.first } <= 9)
    }

    @Test
    fun flutterStyleGestureDetectorExportsClickTarget() {
        var clicked = false
        val result = runtime.render(
            root = GestureDetector(
                modifier = PixelModifier.Empty.size(8, 8),
                onTap = { clicked = true },
                child = DecoratedBox(
                    fillTone = PixelTone.ACCENT,
                    borderTone = null,
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        assertEquals(1, result.clickTargets.size)
        assertTrue(result.clickTargets.first().bounds.contains(4, 4))
        result.clickTargets.first().onClick.invoke()
        assertTrue(clicked)
    }

    @Test
    fun pagerTranslatesAdjacentPageTargetsWhileDragging() {
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 2, currentPage = 0, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state)
        controller.dragBy(state, deltaPx = -10f, viewportSizePx = 20)

        val result = runtime.render(
            root = PixelPager(
                axis = PixelAxis.HORIZONTAL,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 20),
                pages = listOf(
                    PixelBox(
                        modifier = PixelModifier.Empty.fillMaxSize(),
                        alignment = PixelAlignment.TOP_START,
                        children = listOf(
                            PixelSurface(
                                modifier = PixelModifier.Empty.size(6, 6),
                            ),
                        ),
                    ),
                    PixelBox(
                        modifier = PixelModifier.Empty.fillMaxSize().padding(left = 2, top = 0),
                        alignment = PixelAlignment.TOP_START,
                        children = listOf(
                            PixelSurface(
                                modifier = PixelModifier.Empty
                                    .size(6, 6)
                                    .clickable {},
                            ),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 20,
        )

        val adjacentTarget = result.clickTargets.single()
        assertTrue(adjacentTarget.bounds.contains(12, 2))
    }

    @Test
    fun textNodeCanOverrideRuntimeTextRasterizer() {
        val customRasterizer = object : PixelTextRasterizer {
            override fun measureText(text: String): Int = 3

            override fun measureHeight(text: String): Int = 4

            override fun drawText(
                buffer: PixelBuffer,
                text: String,
                x: Int,
                y: Int,
                value: Byte,
            ) {
                buffer.fillRect(
                    left = x,
                    top = y,
                    rectWidth = 3,
                    rectHeight = 4,
                    value = value,
                )
            }
        }

        val result = runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(10, 10),
                padding = 0,
                alignment = PixelAlignment.TOP_START,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText(
                    text = "WIDE",
                    style = PixelTextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = customRasterizer,
                    ),
                ),
            ),
            logicalWidth = 10,
            logicalHeight = 10,
        )

        val pixels = collectOnPixels(result)
        val minX = pixels.minOf { it.first }
        val maxX = pixels.maxOf { it.first }
        val minY = pixels.minOf { it.second }
        val maxY = pixels.maxOf { it.second }
        assertEquals(0, minX)
        assertEquals(2, maxX)
        assertEquals(0, minY)
        assertEquals(3, maxY)
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
    }

    @Test
    fun textSoftWrapRendersMultipleLinesWithinWidth() {
        val drawnTexts = mutableListOf<String>()
        val customRasterizer = object : PixelTextRasterizer {
            override fun measureText(text: String): Int = text.length

            override fun measureHeight(text: String): Int = 1

            override fun drawText(
                buffer: PixelBuffer,
                text: String,
                x: Int,
                y: Int,
                value: Byte,
            ) {
                drawnTexts += text
                if (text.isNotEmpty()) {
                    buffer.fillRect(
                        left = x,
                        top = y,
                        rectWidth = text.length,
                        rectHeight = 1,
                        value = value,
                    )
                }
            }
        }

        val result = runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(3, 2),
                padding = 0,
                alignment = PixelAlignment.TOP_START,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText(
                    text = "ABCD",
                    softWrap = true,
                    maxLines = 2,
                    style = PixelTextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = customRasterizer,
                    ),
                ),
            ),
            logicalWidth = 3,
            logicalHeight = 2,
        )

        assertEquals(listOf("ABC", "D"), drawnTexts)
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 1))
    }

    @Test
    fun textEllipsisTrimsLastVisibleLineWhenLineCountOverflows() {
        val drawnTexts = mutableListOf<String>()
        val customRasterizer = object : PixelTextRasterizer {
            override fun measureText(text: String): Int = text.length

            override fun measureHeight(text: String): Int = 1

            override fun drawText(
                buffer: PixelBuffer,
                text: String,
                x: Int,
                y: Int,
                value: Byte,
            ) {
                drawnTexts += text
            }
        }

        runtime.render(
            root = PixelSurface(
                modifier = PixelModifier.Empty.size(3, 1),
                padding = 0,
                alignment = PixelAlignment.TOP_START,
                borderTone = null,
                fillTone = PixelTone.OFF,
                child = PixelText(
                    text = "ABCD",
                    maxLines = 1,
                    overflow = PixelTextOverflow.ELLIPSIS,
                    style = PixelTextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = customRasterizer,
                    ),
                ),
            ),
            logicalWidth = 3,
            logicalHeight = 1,
        )

        assertEquals(listOf("..."), drawnTexts)
    }

    @Test
    fun pixelButtonBuildsClickableSurfaceWithStyledText() {
        var clicked = false

        val result = runtime.render(
            root = PixelButton(
                text = "OK",
                onClick = { clicked = true },
                modifier = PixelModifier.Empty.size(18, 10),
                style = PixelButtonStyle.Accent,
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertEquals(1, result.clickTargets.size)
        assertTrue(result.clickTargets.first().bounds.contains(9, 5))
        result.clickTargets.first().onClick.invoke()
        assertTrue(clicked)
        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(0, 0))
    }

    @Test
    fun pixelButtonDoesNotExportClickTargetWhenDisabled() {
        var clicked = false

        val result = runtime.render(
            root = PixelButton(
                text = "DISABLED",
                onClick = { clicked = true },
                modifier = PixelModifier.Empty.size(18, 10),
                style = PixelButtonStyle.Accent,
                enabled = false,
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertEquals(0, result.clickTargets.size)
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 0))
        assertFalse(clicked)
    }

    @Test
    fun pixelButtonDoesNotExportClickTargetWhenOnClickIsNull() {
        val result = runtime.render(
            root = PixelButton(
                text = "NULL",
                onClick = null,
                modifier = PixelModifier.Empty.size(18, 10),
                style = PixelButtonStyle.Accent,
            ),
            logicalWidth = 20,
            logicalHeight = 12,
        )

        assertEquals(0, result.clickTargets.size)
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 0))
    }

    @Test
    fun listExportsViewportTargetAndClipsChildClickArea() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 4f)

        val result = runtime.render(
            root = PixelList(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                items = listOf(
                    PixelSurface(
                        modifier = PixelModifier.Empty
                            .size(20, 8)
                            .clickable {},
                    ),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(20, 8),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.listTargets.size)
        assertTrue(result.listTargets.single().bounds.contains(10, 5))
        assertEquals(1, result.clickTargets.size)
        assertEquals(0, result.clickTargets.single().bounds.top)
        assertEquals(4, result.clickTargets.single().bounds.height)
    }

    @Test
    fun listRendersLowerItemsAfterScrollOffsetApplied() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 8f)

        val result = runtime.render(
            root = PixelList(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 6),
                spacing = 2,
                items = listOf(
                    PixelSurface(modifier = PixelModifier.Empty.size(20, 6)),
                    PixelSurface(
                        modifier = PixelModifier.Empty.size(20, 6),
                        fillTone = PixelTone.ACCENT,
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 6,
        )

        assertEquals(PixelTone.ACCENT.value, result.buffer.getPixel(5, 1))
    }

    @Test
    fun singleChildScrollViewExportsViewportTargetAndClipsChildClickArea() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 4f)

        val result = runtime.render(
            root = PixelSingleChildScrollView(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                child = PixelColumn(
                    spacing = 2,
                    children = listOf(
                        PixelSurface(
                            modifier = PixelModifier.Empty
                                .size(20, 8)
                                .clickable {},
                        ),
                        PixelSurface(
                            modifier = PixelModifier.Empty.size(20, 8),
                        ),
                    ),
                ),
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.listTargets.size)
        assertTrue(result.listTargets.single().bounds.contains(10, 5))
        assertEquals(1, result.clickTargets.size)
        assertEquals(0, result.clickTargets.single().bounds.top)
        assertEquals(4, result.clickTargets.single().bounds.height)
        assertEquals(18f, state.contentHeightPx.toFloat(), 0.001f)
    }

    @Test
    fun textFieldExportsInputTargetAndDrawsPlaceholder() {
        val controller = PixelTextFieldController()
        val state = controller.create()

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                placeholder = "TYPE",
                style = PixelTextFieldStyle.Default,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.textInputTargets.size)
        assertTrue(result.textInputTargets.single().bounds.contains(5, 5))
        assertTrue(
            hasTone(
                result = result,
                tone = PixelTone.ACCENT,
                minX = 2,
                maxX = 18,
                minY = 1,
                maxY = 9,
            ),
        )
    }

    @Test
    fun focusedTextFieldDrawsAccentCursor() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HI")
        controller.focus(state)

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                style = PixelTextFieldStyle.Default,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertTrue(
            hasTone(
                result = result,
                tone = PixelTone.ACCENT,
                minX = 1,
                maxX = 19,
                minY = 1,
                maxY = 9,
            ),
        )
    }

    @Test
    fun disabledTextFieldDoesNotExportInputTarget() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "LOCKED")

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                style = PixelTextFieldStyle.Default,
                enabled = false,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(0, result.textInputTargets.size)
        assertTrue(
            hasTone(
                result = result,
                tone = PixelTone.OFF,
                minX = 2,
                maxX = 18,
                minY = 1,
                maxY = 9,
            ),
        )
    }

    @Test
    fun readOnlyTextFieldExportsReadOnlyTargetWithoutCursor() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "READ")
        controller.focus(state)

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                style = PixelTextFieldStyle.Default.copy(
                    readOnlyBorderTone = PixelTone.ON,
                ),
                readOnly = true,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.textInputTargets.size)
        assertTrue(result.textInputTargets.first().readOnly)
        assertFalse(
            hasTone(
                result = result,
                tone = PixelTone.ACCENT,
                minX = 0,
                maxX = 19,
                minY = 0,
                maxY = 9,
            ),
        )
    }

    @Test
    fun autofocusTextFieldExportsAutofocusTarget() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "AUTO")

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                autofocus = true,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.textInputTargets.size)
        assertTrue(result.textInputTargets.first().autofocus)
    }

    @Test
    fun textFieldExportsRequestedInputAction() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "NEXT")

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(20, 10),
                textInputAction = PixelTextInputAction.NEXT,
            ),
            logicalWidth = 20,
            logicalHeight = 10,
        )

        assertEquals(1, result.textInputTargets.size)
        assertEquals(PixelTextInputAction.NEXT, result.textInputTargets.first().action)
    }

    @Test
    fun textFieldClipsLongDisplayTextToContentWidth() {
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "THIS IS A VERY LONG INPUT")

        val result = runtime.render(
            root = PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty.size(12, 10),
            ),
            logicalWidth = 12,
            logicalHeight = 10,
        )

        assertEquals(PixelTone.ON.value, result.buffer.getPixel(11, 5))
        assertEquals(PixelTone.ON.value, result.buffer.getPixel(0, 5))
    }

    private fun collectOnPixels(result: PixelRenderResult): List<Pair<Int, Int>> {
        val pixels = mutableListOf<Pair<Int, Int>>()
        for (y in 0 until result.buffer.height) {
            for (x in 0 until result.buffer.width) {
                if (result.buffer.getPixel(x, y).toInt() != 0) {
                    pixels += x to y
                }
            }
        }
        return pixels
    }

    private fun collectFilledRows(result: PixelRenderResult): Set<Int> {
        val rows = mutableSetOf<Int>()
        for (y in 0 until result.buffer.height) {
            var hasValue = false
            for (x in 0 until result.buffer.width) {
                if (result.buffer.getPixel(x, y).toInt() != 0) {
                    hasValue = true
                    break
                }
            }
            if (hasValue) {
                rows += y
            }
        }
        return rows
    }

    private fun hasTone(
        result: PixelRenderResult,
        tone: PixelTone,
        minX: Int,
        maxX: Int,
        minY: Int,
        maxY: Int,
    ): Boolean {
        for (y in minY until maxY) {
            for (x in minX until maxX) {
                if (result.buffer.getPixel(x, y) == tone.value) {
                    return true
                }
            }
        }
        return false
    }
}

/**
 * 为 legacy renderer 专用测试显式创建旧 bridge/legacy runtime。
 *
 * 默认 `WidgetRenderRuntimeFactory` 已经切到 pipeline-only；这组测试仍需要覆盖
 * 历史 PixelNode renderer，因此在测试内部显式 opt-in 旧链路。
 */
private fun createLegacyFallbackRuntime(): WidgetRenderRuntime {
    val bridgeTreeResolver = DefaultBridgeTreeResolver
    val legacyTreeRenderer = LegacyTreeRendererFactory.createDefault()
    val pipelineRenderer = PipelineElementTreeRenderer(
        bridgeTreeResolver = bridgeTreeResolver,
    )
    val renderSupport = RetainedRenderSupportAssembly(
        widgetAdapter = BridgeWidgetAdapter,
        elementTreeRenderer = object : ElementTreeRenderer {
            /**
             * 测试夹具先跑 direct pipeline，再显式回退到旧 bridge/legacy renderer。
             */
            override fun render(request: ElementTreeRenderRequest): PixelRenderResult {
                return pipelineRenderer.renderOrNull(request)
                    ?: legacyTreeRenderer.render(
                        request = LegacyRenderRequest(
                            root = bridgeTreeResolver.resolve(
                                request = BridgeTreeResolveRequest(root = request.root),
                            ) ?: error("legacy fallback 测试夹具无法解析 bridge tree。"),
                            logicalWidth = request.logicalWidth,
                            logicalHeight = request.logicalHeight,
                        ),
                    )
            }
        },
    ).toRenderSupport()
    return RetainedWidgetRuntimeAssemblyFactory.create(
        onVisualUpdate = { },
        renderSupport = renderSupport,
    ).toRuntime()
}
