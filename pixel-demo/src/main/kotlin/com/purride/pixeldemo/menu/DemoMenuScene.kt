package com.purride.pixeldemo.menu

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.ScrollController
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.catalog.DemoSection
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object DemoMenuScene : DemoScene {
    override val id = "menu"
    override val title = "PIXEL DEMO"
    override val description = "选择 scene"

    override fun build(env: DemoEnv): Widget = MenuWidget(env)
}

private class MenuWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = MenuState()

    inner class MenuState : State<MenuWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            val sections = DemoCatalog.sections
            val items = mutableListOf<Widget>()

            if (sections.isEmpty()) {
                items += Padding(
                    child = Text("暂无 scene", style = TextStyle.Default),
                    all = 8,
                )
            } else {
                sections.forEach { section ->
                    items += sectionHeader(section)
                    section.scenes.forEach { scene ->
                        items += sceneRow(scene)
                    }
                    items += SizedBox(height = 4)
                }
            }

            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = items,
                        spacing = 0,
                        mainAxisSize = MainAxisSize.MIN,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 4,
                ),
            )
        }

        private fun sectionHeader(section: DemoSection): Widget =
            Padding(
                child = Text(section.title.uppercase(), style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                horizontal = 0,
                vertical = 2,
            )

        private fun sceneRow(scene: DemoScene): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(scene) },
                child = Padding(
                    child = Text(scene.title, style = TextStyle.Default),
                    horizontal = 4,
                    vertical = 2,
                ),
            )
    }
}
