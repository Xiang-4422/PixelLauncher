package com.purride.pixeldemo.showcase.stress

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.RichText
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoMetricsOverlay
import com.purride.pixelcore.PixelColor

object StressTextHeavyScene : DemoScene {
    override val id = "stress_text_heavy"
    override val title = "压测 · 富文本"
    override val description = "多 span × 多 tone 长 RichText，观察折行与字形栅格成本"

    override fun build(env: DemoEnv): Widget = StressTextHeavyWidget()
}

private val tiers = listOf(100, 500, 2000)

private fun buildSpans(spanCount: Int): List<PixelTextSpan> {
    val styles = listOf(PixelTextStyle.Default, PixelTextStyle(color = PixelColor.fromRgb(200, 100, 0)))
    val words = listOf("pixel ", "engine ", "render ", "stress ", "text ", "span ", "lorem ", "ipsum ")
    return List(spanCount) { i ->
        PixelTextSpan(text = words[i % words.size], style = styles[i % styles.size])
    }
}

private class StressTextHeavyWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = StressTextHeavyState()

    class StressTextHeavyState : State<StressTextHeavyWidget>() {
        private val scrollState = PixelListState()
        private val scrollCtrl = ScrollController()
        private var tierIndex = 0

        override fun build(context: BuildContext): Widget {
            val n = tiers[tierIndex]
            val spans = buildSpans(n)
            val controls = tiers.mapIndexed { i, c ->
                OutlinedButton(
                    text = "$c",
                    onPressed = { setState { tierIndex = i } },
                    borderColor = if (i == tierIndex) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                )
            }
            return Column(
                children = listOf(
                    DemoMetricsOverlay(extraSampler = { "spans=$n" }),
                    SizedBox(height = 1),
                    Expanded(
                        child = SingleChildScrollView(
                            state = scrollState,
                            controller = scrollCtrl,
                            child = Padding(
                                child = RichText(spans = spans, softWrap = true),
                                all = 4,
                            ),
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
