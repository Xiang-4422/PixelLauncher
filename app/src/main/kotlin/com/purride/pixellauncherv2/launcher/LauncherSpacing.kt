package com.purride.pixellauncherv2.launcher

/**
 * Launcher 顶层页面共用的空间节奏。
 *
 * 这些常量只描述页面级布局语义，避免 Home、Drawer、Settings 和状态栏分别写裸数值。
 * 字体字形留白、图片尺寸、分隔线粗细以及 pixel-engine 组件自身的内部布局不使用这里的值。
 */
object LauncherSpacing {

    /**
     * 普通内容距离屏幕左右边缘的间距。
     *
     * 使用场景：状态栏时间/标题/临时消息、Drawer 搜索框，以及 Home、Drawer、Settings
     * 正文内容。电量线需要横向铺满屏幕，因此不使用该间距。
     */
    const val CONTENT_HORIZONTAL = 2

    /**
     * 普通页面正文距离可用内容区顶部和底部的间距。
     *
     * 使用场景：Home 信息区、Drawer 应用列表和 Settings 设置列表。状态栏真实高度由
     * Android inset 和 [LauncherHeaderLayout] 决定，不使用该值替代系统栏高度。
     */
    const val CONTENT_VERTICAL = 2

    /**
     * 普通同级内容行之间的间距。
     *
     * 使用场景：Home 日期/天气/状态行、Drawer 应用行和 Settings 设置行。Slider 内部
     * 标题与轨道、Switch 内部分段等组件内部间距不使用该值。
     */
    const val ROW_SPACING = 2

    /**
     * Settings 页面不同分类之间的额外垂直间距。
     *
     * 使用场景：Settings section header 的顶部 margin。它只表达“上一类结束到下一类
     * 标题开始”的分组关系，不用于普通设置行之间，也不用于控件内部 padding。
     */
    const val SETTINGS_SECTION_GAP = ROW_SPACING * 2

    /**
     * Launcher 自定义带边框控件中文字到边框或色块边缘的最小内距。
     *
     * 当前用于 Settings 的 Switch 分段。该值不用于无边框 TextButton，也不应同时在
     * 控件外层和内部子项重复添加，否则会产生双重 padding。
     */
    const val BORDERED_CONTROL_INSET = 2
}
