package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelColumn
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPositioned
import com.purride.pixelui.internal.legacy.PixelRow
import com.purride.pixelui.internal.toPixelAlignment
import com.purride.pixelui.internal.toPixelCrossAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisSize

/**
 * Flutter 风格 `Stack` 的 bridge widget。
 */
internal data class StackWidget(
    val children: List<Widget>,
    val alignment: Alignment,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 使用 pixel box 承接 stack 叠放语义。
     */
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

/**
 * Flutter 风格 `Positioned` 的 bridge widget。
 */
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
    /**
     * 使用 pixel positioned 承接定位语义。
     */
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

/**
 * 方向性感知的 `Positioned` bridge widget。
 */
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
    /**
     * 在 build 时按当前方向解析 start/end。
     */
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

/**
 * Flutter 风格 `Row` 的 bridge widget。
 */
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
    /**
     * 使用 pixel row 承接横向弹性布局。
     */
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

/**
 * Flutter 风格 `Column` 的 bridge widget。
 */
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
    /**
     * 使用 pixel column 承接纵向弹性布局。
     */
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
