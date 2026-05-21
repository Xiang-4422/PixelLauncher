package com.purride.pixeldemo.home

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
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
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.catalog.DemoSection
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

class DemoCategoryScene(val section: DemoSection) : DemoScene {
    override val id = "cat_${section.title.lowercase()}"
    override val title = section.title
    override val description = "${section.scenes.size} scenes"

    override fun build(env: DemoEnv): Widget = CategoryWidget(env, section)
}

private class CategoryWidget(
    private val env: DemoEnv,
    private val section: DemoSection,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CategoryState()

    inner class CategoryState : State<CategoryWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()

        override fun build(context: BuildContext): Widget {
            val rows = mutableListOf<Widget>()
            widget.section.scenes.forEach { scene ->
                rows += sceneRow(scene)
            }
            rows += SizedBox(height = 4)
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
                    vertical = 4,
                ),
            )
        }

        private fun sceneRow(scene: DemoScene): Widget =
            GestureDetector(
                onTap = { widget.env.navigator.push(scene) },
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text(scene.title, style = TextStyle.Default),
                            Text(
                                scene.description,
                                style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)),
                            ),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    horizontal = 4,
                    vertical = 3,
                ),
            )
    }
}
