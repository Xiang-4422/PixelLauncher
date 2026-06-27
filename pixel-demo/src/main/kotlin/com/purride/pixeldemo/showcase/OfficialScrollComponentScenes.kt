package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GridView
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListView
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparated
import com.purride.pixelui.ListViewSeparatedBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SliverAppBar
import com.purride.pixelui.SliverList
import com.purride.pixelui.SliverListBuilder
import com.purride.pixelui.SliverPinnedHeader
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.SwipeRefreshScaffold
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel

val ScrollOfficialComponentScenes: List<DemoScene> = listOf(
    scrollScene("scroll_single_child_scroll_view", "SingleChildScrollView", "单 child 滚动容器", setOf("SingleChildScrollView", "ScrollController", "PixelListState")) {
        SingleChildScrollDemo()
    },
    scrollGroupScene(
        id = "scroll_list_view",
        title = "ListView",
        summary = "静态、懒加载和分隔列表",
        apis = setOf("ListView", "ListViewBuilder", "ListViewSeparated", "ListViewSeparatedBuilder", "ScrollController", "PixelListState"),
        panels = listOf(
            "ListView" to { FixedListDemo() },
            "ListViewBuilder" to { LazyListDemo() },
            "ListViewSeparated" to { FixedSeparatedListDemo() },
            "SeparatedBuilder" to { LazySeparatedListDemo() },
        ),
    ),
    scrollGroupScene(
        id = "scroll_grid_view",
        title = "GridView",
        summary = "静态和懒加载网格",
        apis = setOf("GridView", "GridViewBuilder", "ScrollController", "PixelListState"),
        panels = listOf(
            "GridView" to { FixedGridDemo() },
            "GridViewBuilder" to { LazyGridDemo() },
        ),
    ),
    scrollGroupScene(
        id = "scroll_page_view",
        title = "PageView",
        summary = "静态和懒加载分页",
        apis = setOf("PageView", "PageViewBuilder", "PageController"),
        panels = listOf(
            "PageView" to { PageViewDemo(builder = false) },
            "PageViewBuilder" to { PageViewDemo(builder = true) },
        ),
    ),
    scrollScene("scroll_scrollbar", "Scrollbar", "滚动条装饰组件", setOf("Scrollbar", "ListViewBuilder", "PixelListState")) {
        ScrollbarDemo()
    },
    scrollScene("scroll_refresh_indicator", "RefreshIndicator", "下拉刷新容器", setOf("RefreshIndicator", "PixelRefreshIndicatorController")) {
        RefreshIndicatorDemo()
    },
    scrollScene("scroll_swipe_refresh_scaffold", "SwipeRefreshScaffold", "带上下栏的下拉刷新页面骨架", setOf("SwipeRefreshScaffold", "RefreshIndicator", "PixelRefreshIndicatorController")) {
        SwipeRefreshScaffoldDemo()
    },
    scrollGroupScene(
        id = "scroll_custom_scroll_view",
        title = "Slivers",
        summary = "CustomScrollView 和 sliver 组件",
        apis = setOf("CustomScrollView", "SliverList", "SliverListBuilder", "SliverPinnedHeader", "SliverAppBar"),
        panels = listOf(
            "CustomScrollView" to { CustomScrollDemo() },
            "SliverList" to { SliverListDemo(builder = false) },
            "SliverListBuilder" to { SliverListDemo(builder = true) },
            "PinnedHeader" to { PinnedHeaderDemo() },
            "SliverAppBar" to { SliverAppBarDemo() },
        ),
    ),
)

private fun scrollScene(
    id: String,
    title: String,
    summary: String,
    apis: Set<String>,
    body: () -> Widget,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = DemoCatalog.scroll,
        tags = setOf("component", "scroll", title.lowercase()),
        apis = apis,
        bodyBuilder = {
            scrollBody(
                listOf(
                    samplePanel(title = "Example", color = Yellow, child = body()),
                ),
            )
        },
    )

private fun scrollGroupScene(
    id: String,
    title: String,
    summary: String,
    apis: Set<String>,
    panels: List<Pair<String, () -> Widget>>,
): DemoScene =
    ComponentExampleScene(
        id = id,
        title = title,
        summary = summary,
        category = DemoCatalog.scroll,
        tags = setOf("component", "scroll", title.lowercase()),
        apis = apis,
        bodyBuilder = {
            scrollBody(
                panels.map { (panelTitle, body) ->
                    samplePanel(title = panelTitle, color = Yellow, child = body())
                },
            )
        },
    )

private fun scrollBody(children: List<Widget>): Widget =
    Column(
        children = children,
        spacing = 4,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private class SingleChildScrollDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SingleChildScrollState()

    private class SingleChildScrollState : State<SingleChildScrollDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Yellow, child = SingleChildScrollView(state = state, controller = controller, child = scrollRows("line", 12)))
    }
}

private class FixedListDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FixedListState()

    private class FixedListState : State<FixedListDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Cyan, child = ListView(state = state, controller = controller, spacing = 1, items = List(8) { index -> row("row $index", if (index % 2 == 0) Cyan else Muted) }))
    }
}

private class LazyListDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = LazyListState()

    private class LazyListState : State<LazyListDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Pink, child = ListViewBuilder(itemCount = 40, state = state, controller = controller, itemExtent = 9, spacing = 1, itemBuilder = { index -> row("lazy $index", if (index % 2 == 0) Pink else Muted) }))
    }
}

private class FixedSeparatedListDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FixedSeparatedListState()

    private class FixedSeparatedListState : State<FixedSeparatedListDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Green, child = ListViewSeparated(itemCount = 6, state = state, controller = controller, itemBuilder = { index -> row("item $index", Green) }, separatorBuilder = { Container(height = 1, fillColor = Muted) }))
    }
}

private class LazySeparatedListDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = LazySeparatedListState()

    private class LazySeparatedListState : State<LazySeparatedListDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Accent, child = ListViewSeparatedBuilder(itemCount = 40, state = state, controller = controller, itemExtent = 8, separatorExtent = 1, itemBuilder = { index -> row("item $index", Accent) }, separatorBuilder = { Container(height = 1, fillColor = Muted) }))
    }
}

private class FixedGridDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FixedGridState()

    private class FixedGridState : State<FixedGridDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Blue, child = GridView(items = List(12) { index -> gridCell(index, Blue) }, cellWidth = 22, cellHeight = 12, state = state, controller = controller, spacing = 2, runSpacing = 2))
    }
}

private class LazyGridDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = LazyGridState()

    private class LazyGridState : State<LazyGridDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Purple, child = GridViewBuilder(itemCount = 36, cellWidth = 22, cellHeight = 12, state = state, controller = controller, spacing = 2, runSpacing = 2, itemBuilder = { index -> gridCell(index, Purple) }))
    }
}

private class PageViewDemo(
    private val builder: Boolean,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PageViewState()

    private class PageViewState : State<PageViewDemo>() {
        private val controller = PageController()
        private val state = controller.create(pageCount = 3, axis = PixelAxis.HORIZONTAL)
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(
                height = 34,
                borderColor = Cyan,
                child = if (widget.builder) {
                    PageViewBuilder(axis = PixelAxis.HORIZONTAL, controller = controller, state = state, itemCount = 3, itemBuilder = { index -> page("PAGE $index", listOf(Cyan, Accent, Purple)[index]) })
                } else {
                    PageView(axis = PixelAxis.HORIZONTAL, controller = controller, state = state, pages = listOf(page("PAGE 1", Cyan), page("PAGE 2", Accent), page("PAGE 3", Purple)))
                },
            )
    }
}

private class ScrollbarDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ScrollbarState()

    private class ScrollbarState : State<ScrollbarDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Pink, child = Scrollbar(state = state, thumbColor = Pink, child = ListViewBuilder(itemCount = 30, state = state, controller = controller, itemExtent = 9, itemBuilder = { index -> row("scroll $index", Pink) })))
    }
}

private class RefreshIndicatorDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = RefreshIndicatorState()

    private class RefreshIndicatorState : State<RefreshIndicatorDemo>() {
        private val listState = PixelListState()
        private val listController = ScrollController()
        private val refreshController = PixelRefreshIndicatorController()
        private val refreshState = refreshController.create()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Accent, child = RefreshIndicator(state = refreshState, controller = refreshController, onRefresh = { refreshController.completeRefresh(refreshState) }, child = ListViewBuilder(itemCount = 20, state = listState, controller = listController, itemExtent = 9, itemBuilder = { index -> row("pull $index", Accent) })))
    }
}

private class SwipeRefreshScaffoldDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SwipeRefreshScaffoldState()

    private class SwipeRefreshScaffoldState : State<SwipeRefreshScaffoldDemo>() {
        private val listState = PixelListState()
        private val listController = ScrollController()
        private val refreshController = PixelRefreshIndicatorController()
        private val refreshState = refreshController.create()

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(
                height = 50,
                borderColor = Green,
                child = SwipeRefreshScaffold(
                    state = refreshState,
                    controller = refreshController,
                    onRefresh = { refreshController.completeRefresh(refreshState) },
                    topBar = Text("PULL FEED", style = TextStyle(color = Green)),
                    body = ListViewBuilder(
                        itemCount = 20,
                        state = listState,
                        controller = listController,
                        itemExtent = 9,
                        itemBuilder = { index -> row("feed $index", Green) },
                    ),
                ),
            )
    }
}

private class CustomScrollDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomScrollState()

    private class CustomScrollState : State<CustomScrollDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Blue, child = CustomScrollView(state = state, controller = controller, slivers = listOf(SliverPinnedHeader(child = Container(fillColor = Blue, child = Text("HEADER", style = TextStyle(color = PixelColor.Black)))), SliverListBuilder(itemCount = 10, itemExtent = 8, itemBuilder = { index -> row("sliver $index", Blue) }))))
    }
}

private class SliverListDemo(
    private val builder: Boolean,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SliverListState()

    private class SliverListState : State<SliverListDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(
                height = 44,
                borderColor = Green,
                child = CustomScrollView(
                    state = state,
                    controller = controller,
                    slivers = if (widget.builder) {
                        listOf(SliverListBuilder(itemCount = 20, itemExtent = 8, itemBuilder = { index -> row("builder $index", Green) }))
                    } else {
                        listOf(SliverList(items = List(8) { index -> row("sliver $index", Green) }, spacing = 1))
                    },
                ),
            )
    }
}

private class PinnedHeaderDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PinnedHeaderState()

    private class PinnedHeaderState : State<PinnedHeaderDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Accent, child = CustomScrollView(state = state, controller = controller, slivers = listOf(SliverPinnedHeader(child = Container(fillColor = Accent, child = Text("PINNED", style = TextStyle(color = PixelColor.Black)))), SliverListBuilder(itemCount = 12, itemExtent = 8, itemBuilder = { index -> row("row $index", Accent) }))))
    }
}

private class SliverAppBarDemo(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SliverAppBarState()

    private class SliverAppBarState : State<SliverAppBarDemo>() {
        private val state = PixelListState()
        private val controller = ScrollController()
        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Container(height = 44, borderColor = Purple, child = CustomScrollView(state = state, controller = controller, slivers = listOf(SliverAppBar(expandedHeight = 18, collapsedHeight = 8, child = Container(fillColor = Purple, child = Text("APP BAR", style = TextStyle(color = PixelColor.Black)))), SliverListBuilder(itemCount = 10, itemExtent = 8, itemBuilder = { index -> row("row $index", Purple) }))))
    }
}

private fun scrollRows(prefix: String, count: Int): Widget =
    Column(
        children = List(count) { index -> row("$prefix $index", if (index % 2 == 0) Yellow else Muted) },
        spacing = 1,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )

private fun row(label: String, color: PixelColor): Widget =
    Container(
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        borderColor = color,
        child = Text(label, style = TextStyle(color = color)),
    )

private fun gridCell(index: Int, color: PixelColor): Widget =
    Container(fillColor = if (index % 2 == 0) color else Muted, child = Text("$index", style = TextStyle(color = PixelColor.Black)))

private fun page(label: String, color: PixelColor): Widget =
    Container(fillColor = color, child = Text(label, style = TextStyle(color = PixelColor.Black)))
