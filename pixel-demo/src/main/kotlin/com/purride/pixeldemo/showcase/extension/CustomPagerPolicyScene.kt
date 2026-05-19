package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object CustomPagerPolicyScene : DemoScene {
    override val id = "custom_pager_policy"
    override val title = "自定义分页手势策略"
    override val description = "TunablePagerGesturePolicy 动态调整 axisBias"

    private var currentBias = 1.2f
    override val pagerGesturePolicy: PagerGesturePolicy = TunablePagerGesturePolicy { currentBias }

    override fun build(env: DemoEnv): Widget = CustomPagerPolicyWidget(onBiasChange = { bias -> currentBias = bias })
}

private val biasOptions = listOf(0.5f, 1.2f, 3.0f)

private class CustomPagerPolicyWidget(
    private val onBiasChange: (Float) -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = CustomPagerPolicyState()

    inner class CustomPagerPolicyState : State<CustomPagerPolicyWidget>() {
        private val pagerState = PixelPagerState(axis = PixelAxis.HORIZONTAL, pageCount = 5)
        private val pagerController = PageController()
        private var biasIdx = 1

        override fun build(context: BuildContext): Widget {
            val biasControls = biasOptions.mapIndexed { i, b ->
                OutlinedButton(
                    text = "bias=$b",
                    onPressed = {
                        setState { biasIdx = i }
                        widget.onBiasChange(biasOptions[i])
                    },
                    selected = i == biasIdx,
                )
            }
            return Column(
                children = listOf(
                    Text("axisBias=${biasOptions[biasIdx]}", style = TextStyle.Accent),
                    Text("bias 越大 → 越难误触发分页手势", style = TextStyle.Default, softWrap = true),
                    SizedBox(height = 4),
                    Expanded(
                        child = PageViewBuilder(
                            axis = Axis.HORIZONTAL,
                            controller = pagerController,
                            state = pagerState,
                            itemCount = 5,
                            itemBuilder = { i -> Center(child = Text("Page $i", style = TextStyle.Accent)) },
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(children = biasControls, spacing = 2),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
