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

    val textInput: DemoCategory = input
    val lab: DemoCategory = debug

    // Manual/reference categories are kept so reference scenes continue to compile,
    // but they are no longer registered in the UI/UX browser catalog.
    val quickStart = DemoCategory(
        id = "manual_quick_start",
        title = "快速接入",
        summary = "模块依赖、常用 import 与子包入口",
    )
    val minimalActivity = DemoCategory(
        id = "manual_minimal_activity",
        title = "最小 Activity",
        summary = "createPixelHostSetup 的最小 Android 宿主",
    )
    val hostConfig = DemoCategory(
        id = "manual_host_config",
        title = "宿主配置",
        summary = "PixelHostSetupConfig、HostView 与显示偏好",
    )
    val stateManagement = DemoCategory(
        id = "manual_state",
        title = "状态管理",
        summary = "ValueNotifier、StatefulWidget 与 controller/state",
    )
    val theme = DemoCategory(
        id = "manual_theme",
        title = "颜色、字体和主题",
        summary = "PixelColor、TextStyle、栅格和字体优先级",
    )
    val patterns = DemoCategory(
        id = "manual_patterns",
        title = "常见页面模式",
        summary = "面板、列表、分页、输入框和表单",
    )
    val apiReference = DemoCategory(
        id = "manual_api_reference",
        title = "API 速查",
        summary = "公开 API 表的可运行索引",
    )
    val customRenderObject = DemoCategory(
        id = "manual_custom_render_object",
        title = "自定义 RenderObject",
        summary = "advanced alias、layout、paint 和脏标记规则",
    )
    val testing = DemoCategory(
        id = "manual_testing",
        title = "测试",
        summary = "PixelTester DSL、Finder 和验证命令",
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

    val allItems: List<DemoScene> =
        categories.flatMap { category -> registeredItems.filter { it.category?.id == category.id } }

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
