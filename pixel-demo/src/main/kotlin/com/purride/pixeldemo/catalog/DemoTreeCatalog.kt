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
            children = DemoCatalog.groupsFor(category).map { group ->
                DemoTreeNode(
                    id = group.id,
                    title = group.title,
                    shortTitle = group.title,
                    summary = group.summary,
                    category = category,
                    children = group.sceneIds.mapNotNull { sceneId ->
                        DemoCatalog.findById(sceneId)?.let { scene ->
                            DemoTreeNode(
                                id = scene.id,
                                title = scene.title,
                                shortTitle = scene.shortTitle(),
                                summary = scene.summary,
                                category = category,
                                scene = scene,
                            )
                        }
                    },
                )
            },
        )
    }

    val leafScenes: List<DemoScene> = categories.flatMap { category -> category.leafScenes() }

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

private fun DemoTreeNode.leafScenes(): List<DemoScene> =
    scene?.let(::listOf) ?: children.flatMap { it.leafScenes() }

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
    "layout_transform_translate" -> "Transform\n.translate"
    "layout_decorated_box" -> "Decorated\nBox"
    "layout_aspect_ratio" -> "Aspect\nRatio"
    "layout_constrained_box" -> "Constrained\nBox"
    "layout_safe_area" -> "Safe\nArea"
    "layout_gesture_detector" -> "Gesture\nDetector"
    "input_text_field" -> "Text\nField"
    "controls_outlined_button" -> "Outlined\nButton"
    "controls_checkbox" -> "Selection"
    "feedback_dialog" -> "Messages"
    "feedback_progress_bar" -> "Progress\nBar"
    "feedback_activity_indicator" -> "Activity\nIndicator"
    "navigation_app_scaffold" -> "App\nScaffold"
    "scroll_single_child_scroll_view" -> "SingleChild\nScrollView"
    "scroll_list_view" -> "ListView"
    "scroll_grid_view" -> "GridView"
    "scroll_page_view" -> "PageView"
    "scroll_refresh_indicator" -> "Refresh\nIndicator"
    "scroll_custom_scroll_view" -> "Slivers"
    "paint_custom_paint" -> "Custom\nPaint"
    "paint_image" -> "Bitmap\nSprite"
    "animation_animated_container" -> "Animated\nWidgets"
    "animation_tween_animation_builder" -> "Animation\nBuilders"
    "input_focus_scope" -> "Focus"
    "debug_overlay" -> "Debug\nOverlay"
    "debug_inspector_panel" -> "Inspector"
    "layout_stack" -> "Stack"
    "deep_state_restoration" -> "保存恢复"
    "deep_resources_sprites" -> "资源精灵"
    "deep_navigation_runtime" -> "路由栈"
    "deep_inspector_advanced" -> "Inspector"
    "deep_performance_lab" -> "性能实验"
    else -> title
}
