package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomScrollView
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparatedBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.ScrollController
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SliverAppBar
import com.purride.pixelui.SliverListBuilder
import com.purride.pixelui.SliverPinnedHeader
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
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object ScrollPagingShowcaseScene : DemoScene {
    override val id = "components_scroll_paging"
    override val title = "滚动分页"
    override val summary = "列表、网格、分页、刷新、滚动条和 sliver"
    override val category = DemoCatalog.scroll
    override val tags = setOf("scroll", "list", "grid", "pager", "refresh", "sliver")
    override val apis = setOf(
        "SingleChildScrollView",
        "ListView",
        "ListViewBuilder",
        "ListViewSeparated",
        "GridView",
        "PageView",
        "CustomScrollView",
        "Scrollbar",
        "RefreshIndicator",
        "ScrollController",
        "PageController",
        "PixelScrollPhysics",
        "PixelSliverList",
        "PixelSliverListBuilder",
        "PixelSliverPinnedHeader",
        "PixelSliverAppBar",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = ScrollBody())
}

private class ScrollBody(
    override val key: Any? = null,
) : com.purride.pixelui.StatefulWidget(key = key) {
    override fun createState(): com.purride.pixelui.State<out com.purride.pixelui.StatefulWidget> = ScrollState()

    private class ScrollState : com.purride.pixelui.State<ScrollBody>() {
        private val listController = ScrollController()
        private val listState = PixelListState()
        private val gridController = ScrollController()
        private val gridState = PixelListState()
        private val pageController = PageController()
        private val pageState = pageController.create(pageCount = 3, axis = PixelAxis.HORIZONTAL)
        private val refreshController = PixelRefreshIndicatorController()
        private val refreshState = refreshController.create()

        override fun build(context: com.purride.pixelui.BuildContext): Widget =
            Column(
                children = listOf(
                    sectionTitle("列表和滚动条"),
                    samplePanel(
                        title = "ListViewBuilder / Scrollbar",
                        color = Pink,
                        child = Container(
                            height = 46,
                            borderColor = Pink,
                            child = Scrollbar(
                                state = listState,
                                thumbColor = Pink,
                                child = ListViewBuilder(
                                    itemCount = 40,
                                    state = listState,
                                    controller = listController,
                                    itemExtent = 10,
                                    spacing = 1,
                                    itemBuilder = { index ->
                                        Container(
                                            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
                                            borderColor = if (index % 2 == 0) Muted else null,
                                            child = Text("row $index", style = TextStyle(color = if (index % 3 == 0) Accent else PixelColor.White)),
                                        )
                                    },
                                ),
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "GridViewBuilder",
                        color = Green,
                        child = Container(
                            height = 44,
                            borderColor = Green,
                            child = GridViewBuilder(
                                itemCount = 24,
                                cellWidth = 28,
                                cellHeight = 14,
                                state = gridState,
                                controller = gridController,
                                spacing = 2,
                                runSpacing = 2,
                                itemBuilder = { index ->
                                    Container(
                                        fillColor = if (index % 2 == 0) Green else Blue,
                                        child = Text("$index", style = TextStyle(color = PixelColor.Black)),
                                    )
                                },
                            ),
                        ),
                    ),
                    sectionTitle("分页和组合滚动"),
                    samplePanel(
                        title = "PageView / PageController",
                        color = Cyan,
                        child = Container(
                            height = 34,
                            borderColor = Cyan,
                            child = PageView(
                                axis = PixelAxis.HORIZONTAL,
                                controller = pageController,
                                state = pageState,
                                pages = listOf(page("PAGE 1", Cyan), page("PAGE 2", Accent), page("PAGE 3", Purple)),
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "RefreshIndicator / separated / sliver",
                        color = Accent,
                        child = Column(
                            children = listOf(
                                Container(
                                    height = 34,
                                    borderColor = Accent,
                                    child = RefreshIndicator(
                                        state = refreshState,
                                        controller = refreshController,
                                        onRefresh = { refreshController.completeRefresh(refreshState) },
                                        child = ListViewSeparatedBuilder(
                                            itemCount = 20,
                                            state = PixelListState(),
                                            controller = ScrollController(),
                                            itemExtent = 8,
                                            separatorExtent = 1,
                                            itemBuilder = { index -> Text("item $index", style = TextStyle(color = PixelColor.White)) },
                                            separatorBuilder = { Container(height = 1, fillColor = Muted) },
                                        ),
                                    ),
                                ),
                                Container(
                                    height = 36,
                                    borderColor = Blue,
                                    child = CustomScrollView(
                                        state = PixelListState(),
                                        controller = ScrollController(),
                                        slivers = listOf(
                                            SliverAppBar(
                                                expandedHeight = 16,
                                                collapsedHeight = 8,
                                                child = Container(fillColor = Blue, child = Text("SLIVER APP BAR", style = TextStyle(color = PixelColor.Black))),
                                            ),
                                            SliverPinnedHeader(child = Container(fillColor = Accent, child = Text("PINNED", style = TextStyle(color = PixelColor.Black)))),
                                            SliverListBuilder(
                                                itemCount = 12,
                                                itemExtent = 8,
                                                itemBuilder = { index -> Text("sliver $index", style = TextStyle(color = PixelColor.White)) },
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    SingleChildScrollView(
                        state = PixelListState(),
                        controller = ScrollController(),
                        child = Text("SingleChildScrollView", style = TextStyle(color = Muted)),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )

        private fun page(label: String, color: PixelColor): Widget =
            Container(
                fillColor = color,
                child = Text(label, style = TextStyle(color = PixelColor.Black)),
            )
    }
}
