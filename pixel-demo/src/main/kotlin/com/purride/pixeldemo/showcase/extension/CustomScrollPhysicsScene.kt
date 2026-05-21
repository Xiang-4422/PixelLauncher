package com.purride.pixeldemo.showcase.extension

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelScrollPhysics
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object CustomScrollPhysicsScene : DemoScene {
    override val id = "custom_scroll_physics"
    override val title = "自定义 ScrollPhysics"
    override val description = "切换默认 / 高摩擦 / 低摩擦 / 启用 bounce 四档，体感 fling 差异"

    override fun build(env: DemoEnv): Widget = CustomScrollPhysicsWidget(env)
}

private data class PhysicsPreset(val label: String, val physics: PixelScrollPhysics)

private val presets = listOf(
    PhysicsPreset("默认", PixelScrollPhysics.Default),
    PhysicsPreset("高摩擦", PixelScrollPhysics(decelerationPxPerSecondSquared = 6000f)),
    PhysicsPreset("低摩擦", PixelScrollPhysics(decelerationPxPerSecondSquared = 800f)),
    PhysicsPreset(
        "bounce",
        PixelScrollPhysics(
            bounceEnabled = true,
            bounceOverscrollLimitPx = 80f,
            bounceResistance = 0.5f,
        ),
    ),
)

private class CustomScrollPhysicsWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomScrollPhysicsState()

    inner class CustomScrollPhysicsState : State<CustomScrollPhysicsWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private var idx = 0
        private var originalPhysics: PixelScrollPhysics = PixelScrollPhysics.Default

        override fun initState() {
            super.initState()
            originalPhysics = widget.env.hostView.scrollPhysics
            widget.env.hostView.scrollPhysics = presets[idx].physics
        }

        override fun dispose() {
            widget.env.hostView.scrollPhysics = originalPhysics
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val current = presets[idx]
            val controls = presets.mapIndexed { i, p ->
                OutlinedButton(
                    text = p.label,
                    onPressed = {
                        setState {
                            idx = i
                            widget.env.hostView.scrollPhysics = p.physics
                        }
                    },
                    borderColor = if (i == idx) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            return Column(
                children = listOf(
                    Padding(
                        child = Text("当前: ${current.label}", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                        all = 4,
                    ),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = 200,
                            itemBuilder = { i ->
                                Padding(
                                    child = Text(
                                        "Item $i",
                                        style = if (i % 5 == 0) TextStyle(color = PixelColor.fromRgb(200, 100, 0)) else TextStyle.Default,
                                    ),
                                    all = 3,
                                )
                            },
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = controls,
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                        ),
                        horizontal = 4,
                        vertical = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
