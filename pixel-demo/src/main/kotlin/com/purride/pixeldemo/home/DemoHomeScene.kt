package com.purride.pixeldemo.home

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.catalog.DemoSection
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.settings.DemoSettingsScene

object DemoHomeScene : DemoScene {
    override val id = "home"
    override val title = "PIXEL DEMO"
    override val description = ""
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget = HomeWidget(env)
}

private class HomeWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = HomeState()

    inner class HomeState : State<HomeWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    header(),
                    Expanded(child = sectionList()),
                    settingsBar(),
                ),
            )
        }

        private fun header(): Widget =
            Padding(
                child = Text("PIXEL DEMO", style = TextStyle.Accent),
                horizontal = 4,
                vertical = 3,
            )

        private fun sectionList(): Widget {
            val sections = DemoCatalog.sections
            val rows = mutableListOf<Widget>()
            sections.forEach { section ->
                rows += sectionRow(section)
            }
            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = rows,
                        spacing = 0,
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 2,
                ),
            )
        }

        private fun sectionRow(section: DemoSection): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(DemoCategoryScene(section)) },
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text(section.title.uppercase(), style = TextStyle.Default),
                            Text(
                                "${section.scenes.size} scenes",
                                style = TextStyle.Accent,
                            ),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 3,
                ),
            )

        private fun settingsBar(): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(DemoSettingsScene) },
                child = Padding(
                    child = Text("SETTINGS", style = TextStyle.Accent),
                    horizontal = 4,
                    vertical = 3,
                ),
            )
    }
}
