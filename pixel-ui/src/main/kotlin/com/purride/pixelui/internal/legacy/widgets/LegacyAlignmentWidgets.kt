package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.fillMaxSize
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.internal.legacy.width
import com.purride.pixelui.internal.toPixelAlignment

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
