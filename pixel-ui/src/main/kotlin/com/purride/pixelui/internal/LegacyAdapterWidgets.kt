package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.ContainerStyle
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.FlexFit
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelBoxNode
import com.purride.pixelui.PixelButton
import com.purride.pixelui.PixelButtonNode
import com.purride.pixelui.PixelButtonStyle
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelColumnNode
import com.purride.pixelui.PixelContainerStyle
import com.purride.pixelui.PixelList
import com.purride.pixelui.PixelListNode
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelPagerNode
import com.purride.pixelui.PixelPositioned
import com.purride.pixelui.PixelRow
import com.purride.pixelui.PixelRowNode
import com.purride.pixelui.PixelSingleChildScrollView
import com.purride.pixelui.PixelSingleChildScrollViewNode
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelSurfaceNode
import com.purride.pixelui.PixelText
import com.purride.pixelui.PixelTextField
import com.purride.pixelui.PixelTextFieldNode
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.PixelTextNode
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Theme
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.CustomDraw
import com.purride.pixelui.internal.legacy.PixelFlexFit
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelNode
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.legacy.fillMaxSize
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.toPixelAlignment
import com.purride.pixelui.toPixelCrossAxisAlignment
import com.purride.pixelui.toPixelMainAxisAlignment
import com.purride.pixelui.toPixelMainAxisSize
import com.purride.pixelui.toPixelTextAlign
import com.purride.pixelui.internal.legacy.weight
import com.purride.pixelui.internal.legacy.width

internal interface LegacyNodeWidget : Widget {
    val childWidgets: List<Widget>

    fun createLegacyNode(
        context: BuildContext,
        childNodes: List<PixelNode>,
    ): PixelNode
}

internal fun BuildContext.resolveTheme(explicit: PixelThemeData?): PixelThemeData {
    return explicit ?: Theme.maybeOf(this) ?: PixelThemeData.Default
}

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

internal data class LegacyLeafWidget(
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

internal data class LegacySingleChildWidget(
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

internal data class LegacyMultiChildWidget(
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

internal data class FlexWrapperWidget(
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

internal data class TextWidget(
    val data: String,
    val style: PixelTextStyle,
    val theme: PixelThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            PixelTextStyle.Default -> resolvedTheme.textStyle
            PixelTextStyle.Accent -> resolvedTheme.accentTextStyle
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

internal data class ContainerWidget(
    val child: Widget?,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val margin: EdgeInsets?,
    val style: PixelContainerStyle?,
    val theme: PixelThemeData?,
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
            val requestedStyle = PixelContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = alignment,
            )
            when (requestedStyle) {
                PixelContainerStyle.Default -> resolvedTheme.containerStyle
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

internal data class TextFieldWidget(
    val state: PixelTextFieldState,
    val controller: PixelTextFieldController,
    val placeholder: String,
    val style: PixelTextFieldStyle,
    val theme: PixelThemeData?,
    val enabled: Boolean,
    val readOnly: Boolean,
    val autofocus: Boolean,
    val textInputAction: PixelTextInputAction,
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
            style != PixelTextFieldStyle.Default -> style
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

internal data class OutlinedButtonWidget(
    val text: String,
    val onPressed: (() -> Unit)?,
    val style: PixelButtonStyle,
    val theme: PixelThemeData?,
    val enabled: Boolean,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            PixelButtonStyle.Default -> resolvedTheme.buttonStyle
            PixelButtonStyle.Accent -> resolvedTheme.accentButtonStyle
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

internal data class PageViewWidget(
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

internal data class ListViewWidget(
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

internal data class SingleChildScrollViewWidget(
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

internal data class PaddingWidget(
    val child: Widget,
    val padding: EdgeInsets,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class PaddingDirectionalWidget(
    val child: Widget,
    val padding: EdgeInsetsDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val resolvedPadding = padding.resolve(Directionality.of(context))
        return PaddingWidget(
            child = child,
            padding = resolvedPadding,
            key = key,
        )
    }
}

internal data class AlignWidget(
    val child: Widget,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class AlignDirectionalWidget(
    val child: Widget,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            PixelBox(
                children = listOf(childNode),
                modifier = PixelModifier.Empty.fillMaxSize(),
                alignment = alignment.toPixelAlignment(Directionality.of(context)),
            )
        }
    }
}

internal data class SizedBoxWidget(
    val width: Int?,
    val height: Int?,
    val child: Widget?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class GestureDetectorWidget(
    val child: Widget,
    val onTap: () -> Unit,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            childNode.withExtraModifier(PixelModifier.Empty.clickable(onTap))
        }
    }
}

internal data class DecoratedBoxWidget(
    val child: Widget?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class ContainerDirectionalWidget(
    val child: Widget?,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val paddingDirectional: EdgeInsetsDirectional?,
    val margin: EdgeInsets?,
    val marginDirectional: EdgeInsetsDirectional?,
    val style: PixelContainerStyle?,
    val theme: PixelThemeData?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedPadding = paddingDirectional?.resolve(direction) ?: padding
        val resolvedMargin = marginDirectional?.resolve(direction) ?: margin
        val resolvedStyle = style ?: when (
            PixelContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = Alignment.CENTER,
            )
        ) {
            PixelContainerStyle.Default -> resolvedTheme.containerStyle
            resolvedTheme.accentContainerStyle -> resolvedTheme.accentContainerStyle
            else -> PixelContainerStyle(
                fillTone = fillTone,
                borderTone = borderTone,
                alignment = Alignment.CENTER,
            )
        }
        return LegacyMultiChildWidget(
            key = key,
            children = child?.let(::listOf) ?: emptyList(),
        ) { _, childNodes ->
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
}

internal data class StackWidget(
    val children: List<Widget>,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class PositionedWidget(
    val child: Widget,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
    val width: Int?,
    val height: Int?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
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
}

internal data class PositionedDirectionalWidget(
    val child: Widget,
    val start: Int?,
    val top: Int?,
    val end: Int?,
    val bottom: Int?,
    val width: Int?,
    val height: Int?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        val (resolvedLeft, resolvedRight) = when (direction) {
            TextDirection.LTR -> start to end
            TextDirection.RTL -> end to start
        }
        return PositionedWidget(
            child = child,
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

internal data class RowWidget(
    val children: List<Widget>,
    val spacing: Int,
    val mainAxisSize: MainAxisSize,
    val mainAxisAlignment: MainAxisAlignment,
    val crossAxisAlignment: CrossAxisAlignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        return LegacyMultiChildWidget(
            key = key,
            children = children,
        ) { _, childNodes ->
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
}

internal data class ColumnWidget(
    val children: List<Widget>,
    val spacing: Int,
    val mainAxisSize: MainAxisSize,
    val mainAxisAlignment: MainAxisAlignment,
    val crossAxisAlignment: CrossAxisAlignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: BuildContext): Widget {
        val direction = Directionality.of(context)
        return LegacyMultiChildWidget(
            key = key,
            children = children,
        ) { _, childNodes ->
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
}
