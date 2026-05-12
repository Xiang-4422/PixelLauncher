package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Axis
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextDirection

/**
 * 把公开文本对齐值转换成 pipeline 内部文本对齐值。
 */
internal fun TextAlign.toPixelTextAlign(): PixelTextAlign {
    return when (this) {
        TextAlign.START -> PixelTextAlign.START
        TextAlign.CENTER -> PixelTextAlign.CENTER
        TextAlign.END -> PixelTextAlign.END
    }
}

/**
 * 把公开对齐值转换成 pipeline 内部对齐值。
 */
internal fun Alignment.toPixelAlignment(): PixelAlignment {
    return when (this) {
        Alignment.TOP_START -> PixelAlignment.TOP_START
        Alignment.TOP_CENTER -> PixelAlignment.TOP_CENTER
        Alignment.TOP_END -> PixelAlignment.TOP_END
        Alignment.CENTER_START -> PixelAlignment.CENTER_START
        Alignment.CENTER -> PixelAlignment.CENTER
        Alignment.CENTER_END -> PixelAlignment.CENTER_END
        Alignment.BOTTOM_START -> PixelAlignment.BOTTOM_START
        Alignment.BOTTOM_CENTER -> PixelAlignment.BOTTOM_CENTER
        Alignment.BOTTOM_END -> PixelAlignment.BOTTOM_END
    }
}

/**
 * 按文本方向把公开方向性对齐值转换成 pipeline 内部对齐值。
 */
internal fun AlignmentDirectional.toPixelAlignment(
    direction: TextDirection,
): PixelAlignment {
    return when (this) {
        AlignmentDirectional.TOP_START -> if (direction == TextDirection.LTR) PixelAlignment.TOP_START else PixelAlignment.TOP_END
        AlignmentDirectional.TOP_CENTER -> PixelAlignment.TOP_CENTER
        AlignmentDirectional.TOP_END -> if (direction == TextDirection.LTR) PixelAlignment.TOP_END else PixelAlignment.TOP_START
        AlignmentDirectional.CENTER_START -> if (direction == TextDirection.LTR) PixelAlignment.CENTER_START else PixelAlignment.CENTER_END
        AlignmentDirectional.CENTER -> PixelAlignment.CENTER
        AlignmentDirectional.CENTER_END -> if (direction == TextDirection.LTR) PixelAlignment.CENTER_END else PixelAlignment.CENTER_START
        AlignmentDirectional.BOTTOM_START -> if (direction == TextDirection.LTR) PixelAlignment.BOTTOM_START else PixelAlignment.BOTTOM_END
        AlignmentDirectional.BOTTOM_CENTER -> PixelAlignment.BOTTOM_CENTER
        AlignmentDirectional.BOTTOM_END -> if (direction == TextDirection.LTR) PixelAlignment.BOTTOM_END else PixelAlignment.BOTTOM_START
    }
}

/**
 * 把公开主轴排布值转换成 pipeline 内部主轴排布值。
 */
internal fun MainAxisAlignment.toPixelMainAxisAlignment(): PixelMainAxisAlignment {
    return when (this) {
        MainAxisAlignment.START -> PixelMainAxisAlignment.START
        MainAxisAlignment.CENTER -> PixelMainAxisAlignment.CENTER
        MainAxisAlignment.END -> PixelMainAxisAlignment.END
        MainAxisAlignment.SPACE_BETWEEN -> PixelMainAxisAlignment.SPACE_BETWEEN
        MainAxisAlignment.SPACE_AROUND -> PixelMainAxisAlignment.SPACE_AROUND
        MainAxisAlignment.SPACE_EVENLY -> PixelMainAxisAlignment.SPACE_EVENLY
    }
}

/**
 * 按轴向和文本方向把公开主轴排布值转换成 pipeline 内部主轴排布值。
 */
internal fun MainAxisAlignment.toPixelMainAxisAlignment(
    axis: Axis,
    direction: TextDirection,
): PixelMainAxisAlignment {
    return when {
        axis == Axis.HORIZONTAL && direction == TextDirection.RTL -> when (this) {
            MainAxisAlignment.START -> PixelMainAxisAlignment.END
            MainAxisAlignment.END -> PixelMainAxisAlignment.START
            MainAxisAlignment.CENTER -> PixelMainAxisAlignment.CENTER
            MainAxisAlignment.SPACE_BETWEEN -> PixelMainAxisAlignment.SPACE_BETWEEN
            MainAxisAlignment.SPACE_AROUND -> PixelMainAxisAlignment.SPACE_AROUND
            MainAxisAlignment.SPACE_EVENLY -> PixelMainAxisAlignment.SPACE_EVENLY
        }

        else -> toPixelMainAxisAlignment()
    }
}

/**
 * 把公开主轴尺寸策略转换成 pipeline 内部主轴尺寸策略。
 */
internal fun MainAxisSize.toPixelMainAxisSize(): PixelMainAxisSize {
    return when (this) {
        MainAxisSize.MIN -> PixelMainAxisSize.MIN
        MainAxisSize.MAX -> PixelMainAxisSize.MAX
    }
}

/**
 * 把公开交叉轴对齐值转换成 pipeline 内部交叉轴对齐值。
 */
internal fun CrossAxisAlignment.toPixelCrossAxisAlignment(): PixelCrossAxisAlignment {
    return when (this) {
        CrossAxisAlignment.START -> PixelCrossAxisAlignment.START
        CrossAxisAlignment.CENTER -> PixelCrossAxisAlignment.CENTER
        CrossAxisAlignment.END -> PixelCrossAxisAlignment.END
        CrossAxisAlignment.STRETCH -> PixelCrossAxisAlignment.STRETCH
    }
}

/**
 * 按轴向和文本方向把公开交叉轴对齐值转换成 pipeline 内部交叉轴对齐值。
 */
internal fun CrossAxisAlignment.toPixelCrossAxisAlignment(
    axis: Axis,
    direction: TextDirection,
): PixelCrossAxisAlignment {
    return when {
        axis == Axis.VERTICAL && direction == TextDirection.RTL -> when (this) {
            CrossAxisAlignment.START -> PixelCrossAxisAlignment.END
            CrossAxisAlignment.END -> PixelCrossAxisAlignment.START
            CrossAxisAlignment.CENTER -> PixelCrossAxisAlignment.CENTER
            CrossAxisAlignment.STRETCH -> PixelCrossAxisAlignment.STRETCH
        }

        else -> toPixelCrossAxisAlignment()
    }
}
