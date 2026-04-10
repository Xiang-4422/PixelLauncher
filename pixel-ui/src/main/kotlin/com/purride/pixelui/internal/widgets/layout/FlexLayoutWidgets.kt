package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Directionality
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelPositioned
import com.purride.pixelui.internal.toPixelAlignment
import com.purride.pixelui.internal.toPixelCrossAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisAlignment
import com.purride.pixelui.internal.toPixelMainAxisSize

/**
 * Flutter 风格 `Stack` 的直接 render object widget。
 */
internal data class StackWidget(
    override val children: List<Widget>,
    val alignment: Alignment,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建 stack render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderStack(alignment = alignment.toPixelAlignment())
    }

    /**
     * 同步新的 stack 对齐配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderStack).updateStack(alignment.toPixelAlignment())
    }
}

/**
 * Flutter 风格 `Positioned` 的直接 render object widget。
 */
internal data class PositionedWidget(
    override val child: Widget,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
    val width: Int?,
    val height: Int?,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
) {
    /**
     * 创建定位 render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderPositioned(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            width = width,
            height = height,
        )
    }

    /**
     * 同步新的定位配置。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderPositioned).updatePosition(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            width = width,
            height = height,
        )
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
 * Flutter 风格 `Row` 的直接 render object widget。
 */
internal data class RowWidget(
    override val children: List<Widget>,
    val spacing: Int,
    val mainAxisSize: MainAxisSize,
    val mainAxisAlignment: MainAxisAlignment,
    val crossAxisAlignment: CrossAxisAlignment,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建横向 flex render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderFlex(
            direction = FlexDirection.HORIZONTAL,
            children = emptyList(),
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = Directionality.of(context),
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = Directionality.of(context),
            ),
        )
    }

    /**
     * 同步新的 Row 配置到既有 flex render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderFlex).updateFlex(
            direction = FlexDirection.HORIZONTAL,
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = Directionality.of(context),
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.HORIZONTAL,
                direction = Directionality.of(context),
            ),
        )
    }
}

/**
 * Flutter 风格 `Column` 的直接 render object widget。
 */
internal data class ColumnWidget(
    override val children: List<Widget>,
    val spacing: Int,
    val mainAxisSize: MainAxisSize,
    val mainAxisAlignment: MainAxisAlignment,
    val crossAxisAlignment: CrossAxisAlignment,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(
    children = children,
    key = key,
) {
    /**
     * 创建纵向 flex render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderFlex(
            direction = FlexDirection.VERTICAL,
            children = emptyList(),
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.VERTICAL,
                direction = Directionality.of(context),
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.VERTICAL,
                direction = Directionality.of(context),
            ),
        )
    }

    /**
     * 同步新的 Column 配置到既有 flex render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderFlex).updateFlex(
            direction = FlexDirection.VERTICAL,
            spacing = spacing,
            mainAxisSize = mainAxisSize.toPixelMainAxisSize(),
            mainAxisAlignment = mainAxisAlignment.toPixelMainAxisAlignment(
                axis = Axis.VERTICAL,
                direction = Directionality.of(context),
            ),
            crossAxisAlignment = crossAxisAlignment.toPixelCrossAxisAlignment(
                axis = Axis.VERTICAL,
                direction = Directionality.of(context),
            ),
        )
    }
}
