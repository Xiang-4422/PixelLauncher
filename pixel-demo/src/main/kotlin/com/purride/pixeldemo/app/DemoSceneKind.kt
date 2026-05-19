package com.purride.pixeldemo.app

/**
 * Demo 页面类型。
 */
enum class DemoSceneKind(
    val menuLabel: String,
) {
    PIPELINE_TEXT_SURFACE("新渲染管线"),
    TEXT("文本与字体"),
    PALETTE("调色板与像素形状"),
    TEXT_FIELD("文本输入"),
    SINGLE_CHILD_SCROLL("单子节点滚动"),
    HORIZONTAL_PAGER("横向分页"),
    VERTICAL_PAGER("纵向分页"),
    LIST("纵向列表"),
    VIRTUAL_LIST("虚拟列表"),
    SCROLL_STRESS("滚动压力"),
    FORM_AND_LIST("表单与列表组合"),
    PAGER_AND_LIST("分页与列表组合"),
    TEXT_INPUT_MULTILINE("多行输入"),
    RICH_TEXT("富文本"),
    THEME_STATES("主题状态"),
    ENGINE_STABILITY_GATE("Engine 稳定性 Gate"),
    ENVIRONMENT("环境继承"),
    LAUNCHER_LIKE("Launcher 组合页"),
    DRAWER_LIKE("Drawer 验收页"),
    DRAWER_GATE_V2("Drawer Gate V2"),
    LAYOUT_AND_CLICK("布局与点击"),
    CUSTOM_RENDER_OBJECT("自定义 RenderObject"),
    IME_TYPES("输入键盘类型"),
    GESTURE_TUNING("手势调参"),
    FONT_SHOWCASE("字体切换"),
    THEME_TOKENS("Token 主题调参"),
    RTL_MIRROR("RTL 镜像对照"),
    PALETTE_GRID("调色板总览"),
}
