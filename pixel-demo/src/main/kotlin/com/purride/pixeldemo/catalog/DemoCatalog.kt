package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.AnimationOfficialComponentScenes
import com.purride.pixeldemo.showcase.ControlOfficialComponentScenes
import com.purride.pixeldemo.showcase.DebugOfficialComponentScenes
import com.purride.pixeldemo.showcase.InspectorAdvancedScene
import com.purride.pixeldemo.showcase.LayoutOfficialComponentScenes
import com.purride.pixeldemo.showcase.NavigationDeepDiveScene
import com.purride.pixeldemo.showcase.NavigationInputOfficialComponentScenes
import com.purride.pixeldemo.showcase.PaintOfficialComponentScenes
import com.purride.pixeldemo.showcase.PerformanceLabScene
import com.purride.pixeldemo.showcase.ResourcesSpritesShowcaseScene
import com.purride.pixeldemo.showcase.ScrollOfficialComponentScenes
import com.purride.pixeldemo.showcase.StateRestorationScene
import com.purride.pixeldemo.showcase.TextOfficialComponentScenes

object DemoCatalog {
    val layout = DemoCategory(
        id = "layout",
        title = "布局",
        summary = "尺寸、约束、排列、叠层和方向感知布局",
    )
    val text = DemoCategory(
        id = "text",
        title = "文本",
        summary = "文本渲染和富文本",
    )
    val input = DemoCategory(
        id = "input",
        title = "输入",
        summary = "输入框、焦点、表单和语义",
    )
    val controls = DemoCategory(
        id = "controls",
        title = "控件",
        summary = "按钮、选择、分段和滑块",
    )
    val feedback = DemoCategory(
        id = "feedback",
        title = "反馈",
        summary = "进度、提示、徽标和浮层",
    )
    val scroll = DemoCategory(
        id = "scroll",
        title = "滚动",
        summary = "列表、网格、分页、刷新和保存恢复",
    )
    val paint = DemoCategory(
        id = "paint",
        title = "绘制",
        summary = "Canvas、Path、Bitmap、Sprite 和资源缓存",
    )
    val animation = DemoCategory(
        id = "animation",
        title = "动效",
        summary = "Animated 组件、Tween、Ticker 和状态构建",
    )
    val navigation = DemoCategory(
        id = "navigation",
        title = "导航",
        summary = "页面骨架、导航和路由",
    )
    val debug = DemoCategory(
        id = "debug",
        title = "调试",
        summary = "Inspector、Debug overlay、压测和 frame stats",
    )

    val categories: List<DemoCategory> = listOf(
        layout,
        text,
        input,
        controls,
        feedback,
        scroll,
        paint,
        animation,
        navigation,
        debug,
    )

    val groups: List<DemoGroup> = listOf(
        DemoGroup(
            id = "layout_box",
            title = "盒模型",
            summary = "尺寸、边距、装饰和空白",
            category = layout,
            sceneIds = listOf(
                "layout_padding",
                "layout_align",
                "layout_sized_box",
                "layout_visibility",
                "layout_container",
                "layout_decorated_box",
                "layout_aspect_ratio",
                "layout_constrained_box",
                "layout_fitted_box",
                "layout_divider",
                "layout_gap",
            ),
        ),
        DemoGroup(
            id = "layout_flex",
            title = "弹性排列",
            summary = "Row、Column、Flex 空间分配和换行",
            category = layout,
            sceneIds = listOf(
                "layout_row",
                "layout_column",
                "layout_expanded",
                "layout_flexible",
                "layout_spacer",
                "layout_wrap",
            ),
        ),
        DemoGroup(
            id = "layout_stack",
            title = "叠层变换",
            summary = "Stack、定位、透明、裁剪和偏移",
            category = layout,
            sceneIds = listOf(
                "layout_stack",
                "layout_positioned",
                "layout_opacity",
                "layout_clip_rect",
                "layout_transform_translate",
            ),
        ),
        DemoGroup(
            id = "layout_system",
            title = "系统边界",
            summary = "SafeArea、键盘避让和手势入口",
            category = layout,
            sceneIds = listOf(
                "layout_safe_area",
                "layout_ime_avoiding",
                "layout_gesture_detector",
            ),
        ),
        DemoGroup(
            id = "text_rendering",
            title = "文本渲染",
            summary = "普通文本和富文本",
            category = text,
            sceneIds = listOf(
                "text_text",
                "text_rich_text",
            ),
        ),
        DemoGroup(
            id = "input_text",
            title = "文本输入",
            summary = "TextField、controller 和 IME",
            category = input,
            sceneIds = listOf(
                "input_text_field",
            ),
        ),
        DemoGroup(
            id = "input_focus",
            title = "焦点表单",
            summary = "焦点、表单和语义节点",
            category = input,
            sceneIds = listOf(
                "input_focus_scope",
                "input_form",
                "input_semantics",
            ),
        ),
        DemoGroup(
            id = "controls_actions",
            title = "操作控件",
            summary = "按钮、列表行和快捷提示",
            category = controls,
            sceneIds = listOf(
                "controls_outlined_button",
                "controls_list_tile",
                "controls_shortcut_hint",
            ),
        ),
        DemoGroup(
            id = "controls_selection",
            title = "选择控件",
            summary = "单选、多段选择和开关",
            category = controls,
            sceneIds = listOf(
                "controls_selection_list",
                "controls_section_list",
                "controls_checkbox",
                "controls_tabs",
            ),
        ),
        DemoGroup(
            id = "controls_value",
            title = "数值控件",
            summary = "步进、调节和滑块",
            category = controls,
            sceneIds = listOf(
                "controls_value_adjuster",
                "controls_slider",
            ),
        ),
        DemoGroup(
            id = "feedback_status",
            title = "状态反馈",
            summary = "进度、加载状态和徽标",
            category = feedback,
            sceneIds = listOf(
                "feedback_progress_bar",
                "feedback_activity_indicator",
                "feedback_load_state_view",
                "feedback_badge",
            ),
        ),
        DemoGroup(
            id = "feedback_overlay",
            title = "浮层反馈",
            summary = "对话框、消息、菜单和提示",
            category = feedback,
            sceneIds = listOf(
                "feedback_dialog",
                "feedback_overlay_tools",
                "feedback_popover_menu",
            ),
        ),
        DemoGroup(
            id = "scroll_views",
            title = "滚动视图",
            summary = "单子级滚动、列表、网格和分页",
            category = scroll,
            sceneIds = listOf(
                "scroll_single_child_scroll_view",
                "scroll_list_view",
                "scroll_grid_view",
                "scroll_page_view",
                "scroll_scrollbar",
            ),
        ),
        DemoGroup(
            id = "scroll_refresh",
            title = "刷新恢复",
            summary = "刷新、Sliver 和滚动状态恢复",
            category = scroll,
            sceneIds = listOf(
                "scroll_refresh_indicator",
                "scroll_swipe_refresh_scaffold",
                "scroll_custom_scroll_view",
                "deep_state_restoration",
            ),
        ),
        DemoGroup(
            id = "paint_primitives",
            title = "基础绘制",
            summary = "Icon、线、圆、多边形和路径",
            category = paint,
            sceneIds = listOf(
                "paint_icon",
                "paint_line",
                "paint_circle",
                "paint_polygon",
                "paint_path",
            ),
        ),
        DemoGroup(
            id = "paint_resources",
            title = "图片资源",
            summary = "CustomPaint、Bitmap、Sprite 和资源缓存",
            category = paint,
            sceneIds = listOf(
                "paint_custom_paint",
                "paint_image",
                "deep_resources_sprites",
            ),
        ),
        DemoGroup(
            id = "animation_widgets",
            title = "组件动效",
            summary = "隐式动画和动画构建器",
            category = animation,
            sceneIds = listOf(
                "animation_animated_container",
                "animation_tween_animation_builder",
            ),
        ),
        DemoGroup(
            id = "navigation_runtime",
            title = "页面导航",
            summary = "页面骨架、Navigator 和路由栈",
            category = navigation,
            sceneIds = listOf(
                "navigation_app_scaffold",
                "deep_navigation_runtime",
            ),
        ),
        DemoGroup(
            id = "debug_tools",
            title = "调试工具",
            summary = "Overlay、Inspector 和性能实验",
            category = debug,
            sceneIds = listOf(
                "debug_overlay",
                "debug_inspector_panel",
                "deep_inspector_advanced",
                "deep_performance_lab",
            ),
        ),
    )

    private val registeredItems: List<DemoScene> =
        LayoutOfficialComponentScenes +
            TextOfficialComponentScenes +
            ControlOfficialComponentScenes +
            ScrollOfficialComponentScenes +
            listOf(StateRestorationScene) +
            PaintOfficialComponentScenes +
            listOf(
                ResourcesSpritesShowcaseScene,
            ) +
            AnimationOfficialComponentScenes +
            NavigationInputOfficialComponentScenes +
            listOf(
                NavigationDeepDiveScene,
            ) +
            DebugOfficialComponentScenes +
            listOf(
                InspectorAdvancedScene,
                PerformanceLabScene,
            )

    private val registeredItemsById: Map<String, DemoScene> = registeredItems.associateBy { it.id }

    val allItems: List<DemoScene> =
        groups.flatMap { group -> group.sceneIds.mapNotNull { id -> registeredItemsById[id] } }

    fun groupsFor(category: DemoCategory): List<DemoGroup> =
        groups.filter { it.category.id == category.id }

    fun groupsFor(categoryId: String): List<DemoGroup> =
        groups.filter { it.category.id == categoryId }

    fun itemsFor(category: DemoCategory): List<DemoScene> =
        allItems.filter { it.category?.id == category.id }

    fun itemsFor(categoryId: String): List<DemoScene> =
        allItems.filter { it.category?.id == categoryId }

    fun findById(id: String): DemoScene? =
        allItems.find { it.id == id }

    fun search(query: String): List<DemoScene> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return allItems
        return allItems.filter { item ->
            val haystack = buildString {
                append(item.id).append(' ')
                append(item.title).append(' ')
                append(item.summary).append(' ')
                item.category?.let { append(it.title).append(' ').append(it.summary).append(' ') }
                append(item.tags.joinToString(" ")).append(' ')
                append(item.apis.joinToString(" "))
            }.lowercase()
            terms.all { term -> haystack.contains(term) }
        }
    }

    fun previousItem(id: String): DemoScene? {
        val index = allItems.indexOfFirst { it.id == id }
        return if (index > 0) allItems[index - 1] else null
    }

    fun nextItem(id: String): DemoScene? {
        val index = allItems.indexOfFirst { it.id == id }
        return if (index >= 0 && index < allItems.lastIndex) allItems[index + 1] else null
    }
}
