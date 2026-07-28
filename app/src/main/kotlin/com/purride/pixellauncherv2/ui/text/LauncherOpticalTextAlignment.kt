package com.purride.pixellauncherv2.ui.text

import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

/**
 * 消除左对齐文字首字形自带的空白列，使真实墨迹落在调用方提供的内容边界上。
 *
 * 该函数只改变绘制位置，不改变文字的测量宽度和点击区域，适合页面边缘文字；按钮内部文字
 * 和居中文字不应使用。
 */
internal fun opticallyAlignStartText(
    /** 用于查询首字形墨迹边界的完整文本。 */
    text: String,
    /** 返回首字形左侧空白像素数的解析函数。 */
    resolveLeadingInkInset: (String) -> Int,
    /** 已完成测量配置、仅需调整绘制位置的文字组件。 */
    child: Widget,
): Widget {
    /** 字形包解析出的非负左侧空白像素数。 */
    val leadingInkInset = resolveLeadingInkInset(text).coerceAtLeast(0)
    if (leadingInkInset == 0) return child
    return Transform.translate(
        offset = IntOffset(x = -leadingInkInset, y = 0),
        child = child,
    )
}

/**
 * 消除右对齐文字末字形自带的空白列，使真实墨迹落在调用方提供的内容边界上。
 *
 * 与 [opticallyAlignStartText] 一样只改变绘制位置，不用于按钮或有边框控件的内部文字。
 */
internal fun opticallyAlignEndText(
    /** 用于查询末字形墨迹边界的完整文本。 */
    text: String,
    /** 返回末字形右侧空白像素数的解析函数。 */
    resolveTrailingInkInset: (String) -> Int,
    /** 已完成测量配置、仅需调整绘制位置的文字组件。 */
    child: Widget,
): Widget {
    /** 字形包解析出的非负右侧空白像素数。 */
    val trailingInkInset = resolveTrailingInkInset(text).coerceAtLeast(0)
    if (trailingInkInset == 0) return child
    return Transform.translate(
        offset = IntOffset(x = trailingInkInset, y = 0),
        child = child,
    )
}
