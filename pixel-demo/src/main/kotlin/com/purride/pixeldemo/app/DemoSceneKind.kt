package com.purride.pixeldemo.app

/**
 * Demo 页面类型。
 */
enum class DemoSceneKind(
    val menuLabel: String,
) {
    TEXT("文本与字体"),
    PALETTE("调色板与像素形状"),
    HORIZONTAL_PAGER("横向分页"),
    VERTICAL_PAGER("纵向分页"),
    LAYOUT_AND_CLICK("布局与点击"),
}
