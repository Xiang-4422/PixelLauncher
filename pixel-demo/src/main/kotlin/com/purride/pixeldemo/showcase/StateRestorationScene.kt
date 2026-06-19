package com.purride.pixeldemo.showcase

import android.os.Bundle
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.nextPage
import com.purride.pixelui.previousPage
import com.purride.pixelui.state.PixelListAnchor
import com.purride.pixelui.state.PixelListSavedState
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerSavedState
import com.purride.pixelui.state.PixelPagerSnapshot
import com.purride.pixelui.state.getPixelListSavedState
import com.purride.pixelui.state.saveToBundle
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

object StateRestorationScene : DemoScene {
    override val id = "deep_state_restoration"
    override val title = "状态保存恢复"
    override val summary = "List/Pager saved state、snapshot、Bundle round trip"
    override val category = DemoCatalog.scroll
    override val tags = setOf("state", "restoration", "pager", "list", "bundle")
    override val apis = setOf(
        "PixelListSavedState",
        "PixelListAnchor",
        "PixelListRestorationPolicy",
        "PixelPagerSavedState",
        "PixelPagerSnapshot",
        "PixelPagerController",
        "PixelListController",
        "PixelListRestorationPolicy",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = StateRestorationBody())
}

private class StateRestorationBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StateRestorationState()

    private class StateRestorationState : State<StateRestorationBody>() {
        private val listController = ScrollController()
        private val listState = PixelListState()
        private val pageController = PageController()
        private val pageState = pageController.create(pageCount = 3, axis = PixelAxis.HORIZONTAL)
        private var savedList: PixelListSavedState? = PixelListSavedState(
            scrollOffsetPx = 0f,
            maxScrollOffsetPx = 0f,
            anchor = PixelListAnchor(itemIndex = 0, itemOffsetPx = 0f),
        )
        private var savedPager: PixelPagerSavedState? = PixelPagerSavedState(
            currentPage = 0,
            axis = PixelAxis.HORIZONTAL,
        )
        private var restoredList: PixelListSavedState? = null

        override fun build(context: com.purride.pixelui.BuildContext): Widget {
            val pagerSnapshot: PixelPagerSnapshot = pageController.snapshot(pageState)
            return Column(
                children = listOf(
                    sectionTitle("List state"),
                    samplePanel(
                        title = "PixelListSavedState / Bundle",
                        color = Pink,
                        child = Column(
                            children = listOf(
                                Container(
                                    height = 44,
                                    borderColor = Pink,
                                    child = ListViewBuilder(
                                        itemCount = 80,
                                        state = listState,
                                        controller = listController,
                                        itemExtent = 8,
                                        itemBuilder = { index ->
                                            Text("restore row $index", style = TextStyle(color = if (index % 10 == 0) Accent else PixelColor.White))
                                        },
                                    ),
                                ),
                                Row(
                                    children = listOf(
                                        OutlinedButton(
                                            text = "END",
                                            onPressed = {
                                                listController.jumpToEnd(listState)
                                                setState {}
                                            },
                                            borderColor = Pink,
                                        ),
                                        OutlinedButton(
                                            text = "SAVE",
                                            onPressed = {
                                                savedList = listController.saveState(listState)
                                                val bundle = Bundle()
                                                savedList?.saveToBundle(bundle)
                                                restoredList = bundle.getPixelListSavedState()
                                                setState {}
                                            },
                                            borderColor = Accent,
                                        ),
                                        OutlinedButton(
                                            text = "RESTORE",
                                            onPressed = {
                                                savedList?.let { listController.restoreState(listState, it) }
                                                setState {}
                                            },
                                            borderColor = Green,
                                        ),
                                    ),
                                    spacing = 2,
                                ),
                                stateLine("saved", listStateText(savedList)),
                                stateLine("bundle", listStateText(restoredList)),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                    sectionTitle("Pager state"),
                    samplePanel(
                        title = "PixelPagerSavedState / PixelPagerSnapshot",
                        color = Cyan,
                        child = Column(
                            children = listOf(
                                Container(
                                    height = 32,
                                    borderColor = Cyan,
                                    child = PageView(
                                        axis = Axis.HORIZONTAL,
                                        controller = pageController,
                                        state = pageState,
                                        pages = listOf(page("PAGE 0", Cyan), page("PAGE 1", Purple), page("PAGE 2", Blue)),
                                    ),
                                ),
                                Row(
                                    children = listOf(
                                        OutlinedButton(text = "PREV", onPressed = { pageController.previousPage(pageState); setState {} }, borderColor = Muted),
                                        OutlinedButton(text = "NEXT", onPressed = { pageController.nextPage(pageState); setState {} }, borderColor = Accent),
                                        OutlinedButton(
                                            text = "SAVE",
                                            onPressed = {
                                                savedPager = pageController.saveState(pageState)
                                                setState {}
                                            },
                                            borderColor = Cyan,
                                        ),
                                        OutlinedButton(
                                            text = "RESTORE",
                                            onPressed = {
                                                savedPager?.let { pageController.restoreState(pageState, it) }
                                                setState {}
                                            },
                                            borderColor = Green,
                                        ),
                                    ),
                                    spacing = 2,
                                ),
                                stateLine("snapshot", "anchor=${pagerSnapshot.anchorPage} adj=${pagerSnapshot.adjacentPage} drag=${pagerSnapshot.dragOffsetPx.toInt()}"),
                                stateLine("saved", "page=${savedPager?.currentPage} axis=${savedPager?.axis}"),
                            ),
                            spacing = 3,
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun page(label: String, color: PixelColor): Widget =
            Container(
                padding = EdgeInsets.all(3),
                fillColor = color,
                child = Text(label, style = TextStyle(color = PixelColor.Black)),
            )

        private fun stateLine(label: String, value: String): Widget =
            Row(
                children = listOf(
                    Container(width = 42, child = Text(label, style = TextStyle(color = Muted))),
                    Text(value, style = TextStyle(color = PixelColor.White)),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            )

        private fun listStateText(state: PixelListSavedState?): String =
            state?.let { "offset=${it.scrollOffsetPx.toInt()} max=${it.maxScrollOffsetPx.toInt()} anchor=${it.anchor?.itemIndex}" } ?: "<none>"
    }
}
