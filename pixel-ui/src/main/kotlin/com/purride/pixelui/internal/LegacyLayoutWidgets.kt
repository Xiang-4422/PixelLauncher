package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PixelContainerStyle
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelClickableElement
import com.purride.pixelui.internal.legacy.PixelColumn
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPositioned
import com.purride.pixelui.internal.legacy.PixelRow
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.legacy.fillMaxSize
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.internal.legacy.width
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.toPixelAlignment
import com.purride.pixelui.internal.toPixelCrossAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisSize

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
