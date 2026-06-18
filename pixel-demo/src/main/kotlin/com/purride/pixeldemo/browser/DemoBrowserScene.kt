package com.purride.pixeldemo.browser

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoCategory
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.apiTags
import com.purride.pixeldemo.settings.DemoSettingsScene

object DemoBrowserScene : DemoScene {
    override val id: String = "component_browser"
    override val title: String = "PIXEL ENGINE DEMO"
    override val summary: String = "组件浏览器"
    override val isFullScreen: Boolean = true

    override fun build(env: DemoEnv): Widget = BrowserWidget(env)
}

private class BrowserWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = BrowserState()

    inner class BrowserState : State<BrowserWidget>() {
        private val searchController = TextEditingController()
        private val searchState = searchController.create()
        private val listState = PixelListState()
        private val listController = ScrollController()
        private var selectedCategoryId = DemoCatalog.categories.first().id
        private var query = ""

        override fun build(context: BuildContext): Widget {
            val selectedCategory = DemoCatalog.categories.first { it.id == selectedCategoryId }
            val items = if (query.isBlank()) {
                DemoCatalog.itemsFor(selectedCategory)
            } else {
                DemoCatalog.search(query)
            }
            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    header(),
                    searchBox(),
                    categoryPicker(),
                    Expanded(child = resultsList(items)),
                    footer(selectedCategory = selectedCategory, count = items.size),
                ),
            )
        }

        private fun header(): Widget =
            Padding(
                horizontal = 4,
                vertical = 3,
                child = Row(
                    children = listOf(
                        Expanded(
                            child = Column(
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Text("PIXEL ENGINE DEMO", style = TextStyle(color = Accent)),
                                    Text("组件浏览器", style = TextStyle(color = Muted)),
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = "SETTINGS",
                            onPressed = { widget.env.navigator.push(DemoSettingsScene) },
                            borderColor = Accent,
                        ),
                    ),
                    spacing = 3,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
            )

        private fun searchBox(): Widget =
            Padding(
                horizontal = 4,
                vertical = 1,
                child = TextField(
                    state = searchState,
                    controller = searchController,
                    placeholder = "搜索 API / 组件",
                    onChanged = { value ->
                        query = value
                        setState {}
                    },
                    fillColor = Panel,
                    borderColor = if (query.isBlank()) Muted else Accent,
                ),
            )

        private fun categoryPicker(): Widget =
            Padding(
                horizontal = 4,
                vertical = 2,
                child = Wrap(
                    spacing = 2,
                    runSpacing = 2,
                    children = DemoCatalog.categories.map { category ->
                        val selected = category.id == selectedCategoryId && query.isBlank()
                        OutlinedButton(
                            text = category.title,
                            onPressed = {
                                query = ""
                                searchController.updateText(searchState, "")
                                selectedCategoryId = category.id
                                setState {}
                            },
                            borderColor = if (selected) Accent else Muted,
                        )
                    },
                ),
            )

        private fun resultsList(items: List<DemoScene>): Widget {
            val rows = if (items.isEmpty()) {
                listOf(
                    Container(
                        padding = EdgeInsets.all(6),
                        borderColor = Muted,
                        child = Text("没有匹配的组件", style = TextStyle(color = Muted)),
                    ),
                )
            } else {
                items.map { item -> resultRow(item) }
            }
            return SingleChildScrollView(
                state = listState,
                controller = listController,
                child = Padding(
                    horizontal = 4,
                    vertical = 2,
                    child = Column(
                        children = rows,
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            )
        }

        private fun resultRow(item: DemoScene): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(item) },
                child = Container(
                    padding = EdgeInsets.all(3),
                    borderColor = item.categoryColor(),
                    fillColor = Panel,
                    child = Column(
                        children = listOf(
                            Row(
                                children = listOf(
                                    Expanded(child = Text(item.title, style = TextStyle(color = PixelColor.White))),
                                    Text(item.category?.title.orEmpty(), style = TextStyle(color = item.categoryColor())),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                            Text(
                                item.summary,
                                style = TextStyle(color = Muted),
                                softWrap = false,
                                overflow = PixelTextOverflow.ELLIPSIS,
                            ),
                            apiTags(item.apis.take(5), color = item.categoryColor()),
                        ),
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            )

        private fun footer(selectedCategory: DemoCategory, count: Int): Widget =
            Padding(
                horizontal = 4,
                vertical = 2,
                child = Row(
                    children = listOf(
                        Expanded(
                            child = Text(
                                if (query.isBlank()) selectedCategory.summary else "搜索：$query",
                                style = TextStyle(color = Muted),
                                softWrap = false,
                                overflow = PixelTextOverflow.ELLIPSIS,
                            ),
                        ),
                        Text("$count 项", style = TextStyle(color = Accent)),
                    ),
                    mainAxisAlignment = MainAxisAlignment.SPACE_BETWEEN,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                ),
            )
    }
}

private val Accent = PixelColor.fromRgb(255, 176, 64)
private val Muted = PixelColor.fromRgb(140, 160, 170)
private val Panel = PixelColor.fromRgb(14, 18, 22)

private fun DemoScene.categoryColor(): PixelColor = when (category?.id) {
    "layout" -> PixelColor.fromRgb(255, 176, 64)
    "text_input" -> PixelColor.fromRgb(92, 220, 255)
    "controls" -> PixelColor.fromRgb(120, 245, 150)
    "scroll" -> PixelColor.fromRgb(255, 120, 160)
    "paint" -> PixelColor.fromRgb(180, 150, 255)
    "animation" -> PixelColor.fromRgb(255, 230, 100)
    "navigation" -> PixelColor.fromRgb(120, 180, 255)
    "lab" -> PixelColor.fromRgb(255, 150, 80)
    else -> Accent
}
