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
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.settings.DemoSettingsScene
import com.purride.pixelcore.PixelColor

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
                    Expanded(child = sceneList()),
                    settingsBar(),
                ),
            )
        }

        private fun header(): Widget =
            Padding(
                child = Text("PIXEL DEMO", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                horizontal = 4,
                vertical = 3,
            )

        private fun sceneList(): Widget {
            val rows = mutableListOf<Widget>()
            DemoCatalog.sections.forEachIndexed { index, section ->
                val accent = sectionAccent(index)
                rows += sectionHeader(section.title, accent)
                section.scenes.forEach { scene ->
                    rows += sceneRow(scene, accent)
                }
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

        private fun sectionHeader(title: String, accent: PixelColor): Widget =
            Padding(
                child = Text(title.uppercase(), style = TextStyle(color = accent)),
                horizontal = 0,
                vertical = 2,
            )

        private fun sceneRow(scene: DemoScene, accent: PixelColor): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(scene) },
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text(scene.title, style = TextStyle.Default),
                            Text(
                                scene.description,
                                style = TextStyle(color = accent),
                            ),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 3,
                ),
            )

        private fun sectionAccent(index: Int): PixelColor {
            val palette = listOf(
                PixelColor.fromRgb(255, 192, 64),
                PixelColor.fromRgb(80, 220, 255),
                PixelColor.fromRgb(120, 255, 160),
                PixelColor.fromRgb(255, 120, 160),
                PixelColor.fromRgb(180, 160, 255),
                PixelColor.fromRgb(255, 240, 120),
                PixelColor.fromRgb(120, 180, 255),
                PixelColor.fromRgb(255, 150, 80),
                PixelColor.fromRgb(160, 255, 240),
                PixelColor.fromRgb(220, 140, 255),
                PixelColor.fromRgb(180, 220, 120),
            )
            return palette[index % palette.size]
        }

        private fun settingsBar(): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(DemoSettingsScene) },
                child = Padding(
                    child = Text("SETTINGS", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                    horizontal = 4,
                    vertical = 3,
                ),
            )
    }
}
