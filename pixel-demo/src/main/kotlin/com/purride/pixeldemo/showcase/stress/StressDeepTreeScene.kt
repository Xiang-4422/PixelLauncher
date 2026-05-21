package com.purride.pixeldemo.showcase.stress
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay

object StressDeepTreeScene : DemoScene {
    override val id = "stress_deep_tree"
    override val title = "压测 · 深嵌套"
    override val description = "Padding × N 嵌套，观察 layout pass 累计开销（N=10 / 50 / 200）"

    override fun build(env: DemoEnv): Widget = StressDeepTreeWidget()
}

private val tiers = listOf(10, 50, 200)

private fun nestPadding(depth: Int, leaf: Widget): Widget {
    var w: Widget = leaf
    repeat(depth) { w = Padding(child = w, all = 1) }
    return w
}

private class StressDeepTreeWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressDeepTreeState()

    class StressDeepTreeState : State<StressDeepTreeWidget>() {
        private var tierIndex = 0

        override fun build(context: BuildContext): Widget {
            val depth = tiers[tierIndex]
            val leaf = Container(
                width = 20, height = 20,
                fillColor = PixelColor.fromRgb(200, 100, 0), borderColor = null,
            )
            val nested = nestPadding(depth, leaf)
            val controls = tiers.mapIndexed { i, n ->
                OutlinedButton(
                    text = "N=$n",
                    onPressed = { setState { tierIndex = i } },
                    borderColor = if (i == tierIndex) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "depth=$depth" }),
                    SizedBox(height = 1),
                    Expanded(child = Center(child = nested)),
                    SizedBox(height = 2),
                    Center(child = Text("Padding 嵌套深度: $depth", style = TextStyle.Default)),
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
