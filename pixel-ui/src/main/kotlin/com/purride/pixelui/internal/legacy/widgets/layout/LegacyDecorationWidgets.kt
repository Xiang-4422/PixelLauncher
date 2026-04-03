package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.toPixelAlignment

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
