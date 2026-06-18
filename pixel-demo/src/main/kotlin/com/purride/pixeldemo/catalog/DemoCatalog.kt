package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.AnimationStateShowcaseScene
import com.purride.pixeldemo.showcase.ControlShowcaseScene
import com.purride.pixeldemo.showcase.LabShowcaseScene
import com.purride.pixeldemo.showcase.LayoutShowcaseScene
import com.purride.pixeldemo.showcase.NavigationHostShowcaseScene
import com.purride.pixeldemo.showcase.PaintMediaShowcaseScene
import com.purride.pixeldemo.showcase.ScrollPagingShowcaseScene
import com.purride.pixeldemo.showcase.TextInputShowcaseScene

object DemoCatalog {
    val layout = DemoCategory(
        id = "layout",
        title = "基础布局",
        summary = "尺寸、约束、排列、方向感知布局",
    )
    val textInput = DemoCategory(
        id = "text_input",
        title = "文本输入",
        summary = "文本、富文本、输入框与 IME",
    )
    val controls = DemoCategory(
        id = "controls",
        title = "交互控件",
        summary = "按钮、选择、分段、进度与图标",
    )
    val scroll = DemoCategory(
        id = "scroll",
        title = "滚动分页",
        summary = "列表、网格、分页、刷新和滚动控制",
    )
    val paint = DemoCategory(
        id = "paint",
        title = "绘制媒体",
        summary = "图形原语、位图、精灵和自定义绘制",
    )
    val animation = DemoCategory(
        id = "animation",
        title = "动画状态",
        summary = "动画控制器、监听构建和状态构建",
    )
    val navigation = DemoCategory(
        id = "navigation",
        title = "导航宿主",
        summary = "导航、反馈、焦点、表单、语义和宿主调试",
    )
    val lab = DemoCategory(
        id = "lab",
        title = "压力验证",
        summary = "维护 engine 所需的压测与诊断入口",
    )

    val categories: List<DemoCategory> = listOf(
        layout,
        textInput,
        controls,
        scroll,
        paint,
        animation,
        navigation,
        lab,
    )

    val allItems: List<DemoScene> = listOf(
        LayoutShowcaseScene,
        TextInputShowcaseScene,
        ControlShowcaseScene,
        ScrollPagingShowcaseScene,
        PaintMediaShowcaseScene,
        AnimationStateShowcaseScene,
        NavigationHostShowcaseScene,
        LabShowcaseScene,
    )

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
