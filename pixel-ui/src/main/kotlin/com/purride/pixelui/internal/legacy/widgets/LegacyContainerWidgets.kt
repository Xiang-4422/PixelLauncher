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
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.legacy.fillMaxSize
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
 * Flutter 风格 `Padding` 的 bridge widget。
 */
internal data class PaddingWidget(
    val child: Widget,
    val padding: EdgeInsets,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 用额外 box 包裹 child 并施加 padding。
     */
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

/**
 * 方向性感知的 padding bridge widget。
 */
internal data class PaddingDirectionalWidget(
    val child: Widget,
    val padding: EdgeInsetsDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析 padding。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedPadding = padding.resolve(Directionality.of(context))
        return PaddingWidget(
            child = child,
            padding = resolvedPadding,
            key = key,
        )
    }
}

/**
 * Flutter 风格 `Align` 的 bridge widget。
 */
internal data class AlignWidget(
    val child: Widget,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 用填满父级的 box 承接对齐。
     */
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

/**
 * 方向性感知的 `Align` bridge widget。
 */
internal data class AlignDirectionalWidget(
    val child: Widget,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析对齐。
     */
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

/**
 * Flutter 风格 `SizedBox` 的 bridge widget。
 */
internal data class SizedBoxWidget(
    val width: Int?,
    val height: Int?,
    val child: Widget?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 用 box 和固定尺寸 modifier 承接尺寸约束。
     */
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

/**
 * Flutter 风格 `GestureDetector` 的 bridge widget。
 */
internal data class GestureDetectorWidget(
    val child: Widget,
    val onTap: () -> Unit,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 把点击语义折叠进 child 的 modifier。
     */
    override fun build(context: BuildContext): Widget {
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            childNode.withExtraModifier(PixelModifier.Empty.clickable(onTap))
        }
    }
}

/**
 * Flutter 风格 `DecoratedBox` 的 bridge widget。
 */
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
    /**
     * 使用 pixel surface 承接装饰能力。
     */
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
