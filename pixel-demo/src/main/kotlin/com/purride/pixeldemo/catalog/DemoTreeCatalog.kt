package com.purride.pixeldemo.catalog

data class DemoTreeNode(
    val id: String,
    val title: String,
    val shortTitle: String,
    val summary: String,
    val category: DemoCategory? = null,
    val scene: DemoScene? = null,
    val children: List<DemoTreeNode> = emptyList(),
) {
    val isLeaf: Boolean get() = scene != null
}

object DemoTreeCatalog {
    val categories: List<DemoTreeNode> = DemoCatalog.categories.map { category ->
        DemoTreeNode(
            id = category.id,
            title = category.title,
            shortTitle = category.shortTitle(),
            summary = category.summary,
            category = category,
            children = DemoCatalog.itemsFor(category).map { scene ->
                DemoTreeNode(
                    id = scene.id,
                    title = scene.title,
                    shortTitle = scene.shortTitle(),
                    summary = scene.summary,
                    category = category,
                    scene = scene,
                )
            },
        )
    }

    val leafScenes: List<DemoScene> = categories.flatMap { category -> category.children.mapNotNull(DemoTreeNode::scene) }

    fun defaultSelectedPath(): List<String> = listOf(categories.first().id)

    fun selectedPath(selectedIdsByDepth: List<String>): List<DemoTreeNode> {
        val path = mutableListOf<DemoTreeNode>()
        var candidates = categories
        selectedIdsByDepth.forEach { id ->
            val node = candidates.firstOrNull { it.id == id } ?: return path
            path += node
            candidates = node.children
        }
        return path
    }

    fun visibleColumns(selectedIdsByDepth: List<String>): List<List<DemoTreeNode>> {
        val path = selectedPath(selectedIdsByDepth)
        return buildList {
            add(categories)
            path.forEach { node ->
                if (node.children.isNotEmpty()) add(node.children)
            }
        }
    }
}

private fun DemoCategory.shortTitle(): String = when (id) {
    DemoCatalog.layout.id -> "布局"
    DemoCatalog.text.id -> "文本"
    DemoCatalog.input.id -> "输入"
    DemoCatalog.controls.id -> "控件"
    DemoCatalog.feedback.id -> "反馈"
    DemoCatalog.scroll.id -> "滚动"
    DemoCatalog.paint.id -> "绘制"
    DemoCatalog.animation.id -> "动效"
    DemoCatalog.navigation.id -> "导航"
    DemoCatalog.debug.id -> "调试"
    else -> title
}

private fun DemoScene.shortTitle(): String = when (id) {
    "layout_padding_directional" -> "Padding\nDirectional"
    "layout_align_directional" -> "Align\nDirectional"
    "layout_container_directional" -> "Container\nDirectional"
    "layout_positioned_directional" -> "Positioned\nDirectional"
    "layout_positioned_fill" -> "Positioned\nFill"
    "layout_transform_translate" -> "Transform\n.translate"
    "layout_decorated_box" -> "Decorated\nBox"
    "layout_aspect_ratio" -> "Aspect\nRatio"
    "layout_constrained_box" -> "Constrained\nBox"
    "layout_safe_area" -> "Safe\nArea"
    "layout_gesture_detector" -> "Gesture\nDetector"
    "text_text_field" -> "Text\nField"
    "controls_outlined_button" -> "Outlined\nButton"
    "controls_segmented_control" -> "Segmented\nControl"
    "controls_progress_bar" -> "Progress\nBar"
    "controls_activity_indicator" -> "Activity\nIndicator"
    "controls_app_scaffold" -> "App\nScaffold"
    "scroll_single_child_scroll_view" -> "SingleChild\nScrollView"
    "scroll_list_view" -> "ListView"
    "scroll_list_view_builder" -> "ListView\nBuilder"
    "scroll_list_view_separated" -> "ListView\nSeparated"
    "scroll_list_view_separated_builder" -> "Separated\nBuilder"
    "scroll_grid_view" -> "GridView"
    "scroll_grid_view_builder" -> "GridView\nBuilder"
    "scroll_page_view" -> "PageView"
    "scroll_page_view_builder" -> "PageView\nBuilder"
    "scroll_refresh_indicator" -> "Refresh\nIndicator"
    "scroll_custom_scroll_view" -> "Custom\nScrollView"
    "scroll_sliver_list" -> "SliverList"
    "scroll_sliver_list_builder" -> "SliverList\nBuilder"
    "scroll_sliver_pinned_header" -> "Pinned\nHeader"
    "scroll_sliver_app_bar" -> "Sliver\nAppBar"
    "paint_custom_paint" -> "Custom\nPaint"
    "paint_animated_sprite" -> "Animated\nSprite"
    "animation_animated_container" -> "Animated\nContainer"
    "animation_animated_opacity" -> "Animated\nOpacity"
    "animation_animated_padding" -> "Animated\nPadding"
    "animation_animated_align" -> "Animated\nAlign"
    "animation_animated_positioned" -> "Animated\nPositioned"
    "animation_animated_switcher" -> "Animated\nSwitcher"
    "animation_tween_animation_builder" -> "Tween\nBuilder"
    "animation_animated_builder" -> "Animated\nBuilder"
    "nav_focus_scope" -> "Focus\nScope"
    "nav_form_field" -> "Form\nField"
    "debug_overlay" -> "Debug\nOverlay"
    "debug_inspector_panel" -> "Inspector\nPanel"
    "debug_inspector_bounds_overlay" -> "Bounds\nOverlay"
    "layout_foundation" -> "基础布局"
    "layout_constraints" -> "约束"
    "layout_arrangement" -> "排列"
    "layout_stack" -> "Stack"
    "layout_directionality" -> "方向感知"
    "text_rendering" -> "文本渲染"
    "text_rich" -> "富文本"
    "text_field_input" -> "输入框"
    "text_edit_state" -> "编辑状态"
    "controls_buttons_list" -> "按钮列表"
    "controls_selection_switch" -> "选择开关"
    "controls_segmented_tabs" -> "分段标签"
    "controls_slider_progress" -> "滑块进度"
    "controls_badge_icon" -> "徽标图标"
    "components_scroll_paging" -> "滚动分页"
    "deep_state_restoration" -> "保存恢复"
    "components_paint_media" -> "绘制媒体"
    "deep_resources_sprites" -> "资源精灵"
    "components_animation_state" -> "动画状态"
    "deep_animation_runtime" -> "动画运行"
    "components_navigation_host" -> "导航宿主"
    "deep_navigation_runtime" -> "路由栈"
    "deep_focus_form_semantics" -> "表单焦点"
    "components_lab" -> "压力验证"
    "deep_inspector_advanced" -> "Inspector"
    "deep_performance_lab" -> "性能实验"
    else -> title
}
