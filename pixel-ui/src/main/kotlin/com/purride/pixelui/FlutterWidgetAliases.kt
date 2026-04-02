package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.internal.LegacyNodeWidget
import com.purride.pixelui.node.CustomDraw
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格公开别名层。
 *
 * 这一轮不再走“公开 Widget -> 直接强转 PixelNode”的路线，
 * 而是让公开组件先形成 retained build tree，再在最后一步翻译成 legacy node。
 */
typealias TextStyle = PixelTextStyle
typealias ButtonStyle = PixelButtonStyle
typealias ContainerStyle = PixelContainerStyle
typealias TextFieldStyle = PixelTextFieldStyle
typealias TextOverflow = PixelTextOverflow
typealias TextInputAction = PixelTextInputAction
typealias ThemeData = PixelThemeData

private fun PixelNode.withExtraModifier(extra: PixelModifier): PixelNode {
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

private fun BuildContext.resolveTheme(explicit: ThemeData?): ThemeData {
    return explicit ?: Theme.maybeOf(this) ?: ThemeData.Default
}

private data class LegacyLeafWidget(
    override val key: Any? = null,
    private val factory: (BuildContext) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget> = emptyList()

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context)
    }
}

private data class LegacySingleChildWidget(
    override val key: Any? = null,
    val child: Widget,
    private val factory: (BuildContext, PixelNode) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context, childNodes.single())
    }
}

private data class LegacyMultiChildWidget(
    override val key: Any? = null,
    val children: List<Widget>,
    private val factory: (BuildContext, List<PixelNode>) -> PixelNode,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = children

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return factory(context, childNodes)
    }
}

private data class FlexWrapperWidget(
    override val key: Any? = null,
    val child: Widget,
    val flex: Int,
    val fit: FlexFit,
) : LegacyNodeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    override fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode {
        return childNodes.single().withExtraModifier(
            PixelModifier.Empty.weight(
                weight = flex.coerceAtLeast(1).toFloat(),
                fit = when (fit) {
                    FlexFit.TIGHT -> PixelFlexFit.TIGHT
                    FlexFit.LOOSE -> PixelFlexFit.LOOSE
                },
            ),
        )
    }
}

private data class TextWidget(
    val data: String,
    val style: TextStyle,
    val theme: ThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: TextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            TextStyle.Default -> resolvedTheme.textStyle
            TextStyle.Accent -> resolvedTheme.accentTextStyle
            else -> style
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelText(
                text = data,
                modifier = PixelModifier.Empty,
                style = resolvedStyle,
                softWrap = softWrap,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign.toPixelTextAlign(),
                textDirection = Directionality.of(context),
                key = key,
            )
        }
    }
}

private data class ContainerWidget(
    val child: Widget?,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val margin: EdgeInsets?,
    val style: ContainerStyle?,
    val theme: ThemeData?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = style ?: run {
            val requestedStyle = ContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = alignment,
            )
            when (requestedStyle) {
                ContainerStyle.Default -> resolvedTheme.containerStyle
                resolvedTheme.accentContainerStyle -> resolvedTheme.accentContainerStyle
                else -> requestedStyle
            }
        }
        return LegacyMultiChildWidget(
            key = key,
            children = child?.let(::listOf) ?: emptyList(),
        ) { _, childNodes ->
            val paddedChild = childNodes.singleOrNull()?.let { childNode ->
                if (padding == null) {
                    childNode
                } else {
                    PixelBox(
                        children = listOf(childNode),
                        modifier = PixelModifier.Empty.padding(
                            left = padding.left,
                            top = padding.top,
                            right = padding.right,
                            bottom = padding.bottom,
                        ),
                        alignment = PixelAlignment.TOP_START,
                    )
                }
            }
            val sizedModifier = when {
                width != null && height != null -> PixelModifier.Empty.size(width, height)
                width != null -> PixelModifier.Empty.width(width)
                height != null -> PixelModifier.Empty.height(height)
                else -> PixelModifier.Empty
            }
            val baseNode = PixelSurface(
                child = paddedChild,
                modifier = sizedModifier,
                fillTone = resolvedStyle.fillTone,
                borderTone = resolvedStyle.borderTone,
                padding = 0,
                alignment = resolvedStyle.alignment.toPixelAlignment(),
                key = key,
            )
            if (margin == null) {
                baseNode
            } else {
                PixelBox(
                    children = listOf(baseNode),
                    modifier = PixelModifier.Empty.padding(
                        left = margin.left,
                        top = margin.top,
                        right = margin.right,
                        bottom = margin.bottom,
                    ),
                    alignment = PixelAlignment.TOP_START,
                )
            }
        }
    }
}

private data class TextFieldWidget(
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val placeholder: String,
    val style: TextFieldStyle,
    val theme: ThemeData?,
    val enabled: Boolean,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val textInputAction: TextInputAction,
    val onChanged: ((String) -> Unit)?,
    val onSubmitted: ((String) -> Unit)?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when {
            style != TextFieldStyle.Default -> style
            !enabled -> resolvedTheme.disabledTextFieldStyle
            readOnly -> resolvedTheme.readOnlyTextFieldStyle
            else -> resolvedTheme.textFieldStyle
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelTextField(
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                placeholder = placeholder,
                style = resolvedStyle,
                enabled = enabled,
                readOnly = readOnly,
                autofocus = autofocus,
                textInputAction = textInputAction,
                onChanged = onChanged,
                onSubmitted = onSubmitted,
                key = key,
            )
        }
    }
}

private data class OutlinedButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    val style: ButtonStyle,
    val theme: ThemeData?,
    val enabled: Boolean,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            ButtonStyle.Default -> resolvedTheme.buttonStyle
            ButtonStyle.Accent -> resolvedTheme.accentButtonStyle
            else -> style
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelButton(
                text = text,
                onClick = onPressed,
                modifier = PixelModifier.Empty,
                style = resolvedStyle,
                disabledStyle = resolvedTheme.disabledButtonStyle,
                enabled = enabled,
                key = key,
            )
        }
    }
}

private data class PageViewWidget(
    val axis: Axis,
    val controller: PixelPagerController,
    val state: PixelPagerState,
    val pages: List<Widget>,
    val onPageChanged: ((Int) -> Unit)?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacyMultiChildWidget(
            key = key,
            children = pages,
        ) { _, childNodes ->
            PixelPager(
                axis = axis,
                state = state,
                controller = controller,
                pages = childNodes,
                modifier = PixelModifier.Empty,
                onPageChanged = onPageChanged,
                key = key,
            )
        }
    }
}

private data class ListViewWidget(
    val items: List<Widget>,
    val state: PixelListState,
    val controller: PixelListController,
    val spacing: Int,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacyMultiChildWidget(
            key = key,
            children = items,
        ) { _, childNodes ->
            PixelList(
                items = childNodes,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                spacing = spacing,
                key = key,
            )
        }
    }
}

private data class SingleChildScrollViewWidget(
    val child: Widget,
    val state: PixelListState,
    val controller: PixelListController,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            PixelSingleChildScrollView(
                child = childNode,
                state = state,
                controller = controller,
                modifier = PixelModifier.Empty,
                key = key,
            )
        }
    }
}

fun Padding(
    child: Widget,
    all: Int,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.all(all),
        key = key,
    )
}

fun Padding(
    child: Widget,
    padding: EdgeInsets,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { _, childNode ->
        PixelBox(
            children = listOf(childNode),
            modifier = PixelModifier.Empty.padding(
                left = padding.left,
                top = padding.top,
                right = padding.right,
                bottom = padding.bottom,
            ),
            alignment = PixelAlignment.TOP_START,
        )
    }
}

fun Padding(
    child: Widget,
    horizontal: Int = 0,
    vertical: Int = 0,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.symmetric(
            horizontal = horizontal,
            vertical = vertical,
        ),
        key = key,
    )
}

fun PaddingDirectional(
    child: Widget,
    padding: EdgeInsetsDirectional,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { context, childNode ->
        val resolvedPadding = padding.resolve(Directionality.of(context))
        PixelBox(
            children = listOf(childNode),
            modifier = PixelModifier.Empty.padding(
                left = resolvedPadding.left,
                top = resolvedPadding.top,
                right = resolvedPadding.right,
                bottom = resolvedPadding.bottom,
            ),
            alignment = PixelAlignment.TOP_START,
        )
    }
}

fun Padding(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.only(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
        key = key,
    )
}

fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { _, childNode ->
        PixelBox(
            children = listOf(childNode),
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = alignment.toPixelAlignment(),
        )
    }
}

fun Center(
    child: Widget,
    key: Any? = null,
): Widget {
    return Align(
        child = child,
        alignment = Alignment.CENTER,
        key = key,
    )
}

fun AlignDirectional(
    child: Widget,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { context, childNode ->
        PixelBox(
            children = listOf(childNode),
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = alignment.toPixelAlignment(Directionality.of(context)),
        )
    }
}

fun SizedBox(
    width: Int? = null,
    height: Int? = null,
    child: Widget? = null,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = child?.let(::listOf) ?: emptyList(),
    ) { _, childNodes ->
        val baseModifier = when {
            width != null && height != null -> PixelModifier.Empty.size(width, height)
            width != null -> PixelModifier.Empty.width(width)
            height != null -> PixelModifier.Empty.height(height)
            else -> PixelModifier.Empty
        }
        PixelBox(
            children = childNodes,
            modifier = baseModifier,
            alignment = PixelAlignment.TOP_START,
        )
    }
}

fun Expanded(
    child: Widget,
    flex: Int = 1,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = child,
        flex = flex,
        fit = FlexFit.TIGHT,
    )
}

fun Flexible(
    child: Widget,
    flex: Int = 1,
    fit: FlexFit = FlexFit.LOOSE,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = child,
        flex = flex,
        fit = fit,
    )
}

fun Spacer(
    flex: Int = 1,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = SizedBox(key = "${key ?: "spacer"}-box"),
        flex = flex,
        fit = FlexFit.TIGHT,
    )
}

fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { _, childNode ->
        childNode.withExtraModifier(
            PixelModifier.Empty.clickable(onTap),
        )
    }
}

fun Text(
    data: String,
    style: TextStyle = TextStyle.Default,
    theme: ThemeData? = null,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: TextAlign = TextAlign.START,
    key: Any? = null,
): Widget {
    return TextWidget(
        data = data,
        style = style,
        theme = theme,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        key = key,
    )
}

fun DecoratedBox(
    child: Widget? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    padding: Int = 2,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = child?.let(::listOf) ?: emptyList(),
    ) { _, childNodes ->
        PixelSurface(
            child = childNodes.singleOrNull(),
            modifier = PixelModifier.Empty,
            fillTone = fillTone,
            borderTone = borderTone,
            padding = padding,
            alignment = alignment.toPixelAlignment(),
            key = key,
        )
    }
}

fun Container(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return ContainerWidget(
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
    )
}

fun ContainerDirectional(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    paddingDirectional: EdgeInsetsDirectional? = null,
    margin: EdgeInsets? = null,
    marginDirectional: EdgeInsetsDirectional? = null,
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = child?.let(::listOf) ?: emptyList(),
    ) { context, childNodes ->
        val direction = Directionality.of(context)
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedPadding = paddingDirectional?.resolve(direction) ?: padding
        val resolvedMargin = marginDirectional?.resolve(direction) ?: margin
        val resolvedStyle = style ?: when (
            ContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = Alignment.CENTER,
            )
        ) {
            ContainerStyle.Default -> resolvedTheme.containerStyle
            resolvedTheme.accentContainerStyle -> resolvedTheme.accentContainerStyle
            else -> ContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = Alignment.CENTER,
            )
        }
        val resolvedAlignment = alignment.toPixelAlignment(direction)
        val paddedChild = childNodes.singleOrNull()?.let { childNode ->
            if (resolvedPadding == null) {
                childNode
            } else {
                PixelBox(
                    children = listOf(childNode),
                    modifier = PixelModifier.Empty.padding(
                        left = resolvedPadding.left,
                        top = resolvedPadding.top,
                        right = resolvedPadding.right,
                        bottom = resolvedPadding.bottom,
                    ),
                    alignment = PixelAlignment.TOP_START,
                )
            }
        }
        val sizedModifier = when {
            width != null && height != null -> PixelModifier.Empty.size(width, height)
            width != null -> PixelModifier.Empty.width(width)
            height != null -> PixelModifier.Empty.height(height)
            else -> PixelModifier.Empty
        }
        val baseNode = PixelSurface(
            child = paddedChild,
            modifier = sizedModifier,
            fillTone = resolvedStyle.fillTone,
            borderTone = resolvedStyle.borderTone,
            padding = 0,
            alignment = resolvedAlignment,
            key = key,
        )
        if (resolvedMargin == null) {
            baseNode
        } else {
            PixelBox(
                children = listOf(baseNode),
                modifier = PixelModifier.Empty.padding(
                    left = resolvedMargin.left,
                    top = resolvedMargin.top,
                    right = resolvedMargin.right,
                    bottom = resolvedMargin.bottom,
                ),
                alignment = PixelAlignment.TOP_START,
            )
        }
    }
}

fun Stack(
    children: List<Widget>,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = children,
    ) { _, childNodes ->
        PixelBox(
            children = childNodes,
            modifier = PixelModifier.Empty,
            alignment = alignment.toPixelAlignment(),
            key = key,
        )
    }
}

fun Positioned(
    child: Widget,
    left: Int? = null,
    top: Int? = null,
    right: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { _, childNode ->
        PixelPositioned(
            child = childNode,
            modifier = PixelModifier.Empty,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            width = width,
            height = height,
            key = key,
        )
    }
}

fun PositionedDirectional(
    child: Widget,
    start: Int? = null,
    top: Int? = null,
    end: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    key: Any? = null,
): Widget {
    return LegacySingleChildWidget(
        key = key,
        child = child,
    ) { context, childNode ->
        val direction = Directionality.of(context)
        val (resolvedLeft, resolvedRight) = when (direction) {
            TextDirection.LTR -> start to end
            TextDirection.RTL -> end to start
        }
        PixelPositioned(
            child = childNode,
            modifier = PixelModifier.Empty,
            left = resolvedLeft,
            top = top,
            right = resolvedRight,
            bottom = bottom,
            width = width,
            height = height,
            key = key,
        )
    }
}

fun PositionedFill(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    key: Any? = null,
): Widget {
    return Positioned(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        key = key,
    )
}

fun Row(
    children: List<Widget>,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = children,
    ) { context, childNodes ->
        val direction = Directionality.of(context)
        PixelRow(
            children = childNodes,
            modifier = PixelModifier.Empty,
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = direction,
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = direction,
            ),
            key = key,
        )
    }
}

fun Column(
    children: List<Widget>,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = children,
    ) { context, childNodes ->
        val direction = Directionality.of(context)
        PixelColumn(
            children = childNodes,
            modifier = PixelModifier.Empty,
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.VERTICAL,
                direction = direction,
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.VERTICAL,
                direction = direction,
            ),
            key = key,
        )
    }
}

fun PageView(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    pages: List<Widget>,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return LegacyMultiChildWidget(
        key = key,
        children = pages,
    ) { context, childNodes ->
        context.watch(controller)
        PixelPager(
            axis = axis,
            state = state,
            controller = controller,
            pages = childNodes,
            modifier = PixelModifier.Empty,
            onPageChanged = onPageChanged,
            key = key,
        )
    }
}

fun PageViewBuilder(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PageViewWidget(
        axis = axis,
        controller = controller,
        state = state,
        pages = List(itemCount) { index -> itemBuilder(index) },
        key = key,
        onPageChanged = onPageChanged,
    )
}

fun ListView(
    items: List<Widget>,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ListViewWidget(
        items = items,
        state = state,
        controller = controller,
        spacing = spacing,
        key = key,
    )
}

fun ListViewBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ListView(
        items = List(itemCount) { index -> itemBuilder(index) },
        state = state,
        controller = controller,
        spacing = spacing,
        key = key,
    )
}

fun ListViewSeparated(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    separatorBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    val separatedItems = buildList {
        repeat(itemCount) { index ->
            add(itemBuilder(index))
            if (index < itemCount - 1) {
                add(separatorBuilder(index))
            }
        }
    }
    return ListView(
        items = separatedItems,
        state = state,
        controller = controller,
        spacing = 0,
        key = key,
    )
}

fun SingleChildScrollView(
    child: Widget,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    return SingleChildScrollViewWidget(
        child = child,
        state = state,
        controller = controller,
        key = key,
    )
}

fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
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
): Widget {
    return TextFieldWidget(
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
    )
}

fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    style: ButtonStyle = ButtonStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return OutlinedButtonWidget(
        text = text,
        onPressed = onPressed,
        style = style,
        theme = theme,
        enabled = enabled,
        key = key,
    )
}
