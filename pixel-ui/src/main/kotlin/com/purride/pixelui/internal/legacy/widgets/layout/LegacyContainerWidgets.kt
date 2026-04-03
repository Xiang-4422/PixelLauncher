package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.PixelContainerStyle
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.internal.legacy.width
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `Container` 的 bridge widget。
 */
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
    /**
     * 构建默认方向下的容器 bridge widget。
     */
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

/**
 * 方向性感知的 `Container` bridge widget。
 */
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
    /**
     * 在 build 时按当前方向解析 padding、margin 和 alignment。
     */
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
