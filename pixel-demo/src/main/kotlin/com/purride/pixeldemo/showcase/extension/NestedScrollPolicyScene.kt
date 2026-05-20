package com.purride.pixeldemo.showcase.extension

import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Axis
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.gesture.NestedScrollGesturePolicy
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object NestedScrollPolicyScene : DemoScene {
    override val id = "nested_scroll_policy"
    override val title = "嵌套滚动策略"
    override val description = "切换 NestedScrollGesturePolicy 看 pager-in-list 接管行为"

    override fun build(env: DemoEnv): Widget = NestedScrollPolicyWidget(env)
}

private val pagerFirst = object : NestedScrollGesturePolicy() {
    override fun shouldDeferPagerToList(
        pagerAxis: PixelAxis,
        pagerWantsDrag: Boolean,
        listWantsDrag: Boolean,
        listCanConsumeDrag: Boolean,
    ): Boolean = false

    override fun shouldHandOffListToPager(
        pagerAxis: PixelAxis,
        listCanConsumeDrag: Boolean,
        deltaPx: Float,
    ): Boolean = pagerAxis == PixelAxis.VERTICAL && deltaPx != 0f
}

private val presets = listOf(
    "默认（list 优先）" to NestedScrollGesturePolicy.Default,
    "pager 优先" to pagerFirst,
)

private class NestedScrollPolicyWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = NestedScrollPolicyState()

    inner class NestedScrollPolicyState : State<NestedScrollPolicyWidget>() {
        private val pagerCtrl = PageController()
        private val pagerState = PixelPagerState(axis = PixelAxis.VERTICAL, pageCount = 4)
        private val listStates = List(4) { PixelListState() }
        private val listCtrls = List(4) { ScrollController() }
        private var idx = 0
        private var original: NestedScrollGesturePolicy = NestedScrollGesturePolicy.Default

        override fun initState() {
            super.initState()
            original = widget.env.hostView.nestedScrollPolicy
            widget.env.hostView.nestedScrollPolicy = presets[idx].second
        }

        override fun dispose() {
            widget.env.hostView.nestedScrollPolicy = original
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val controls = presets.mapIndexed { i, p ->
                OutlinedButton(
                    text = p.first,
                    onPressed = {
                        setState {
                            idx = i
                            widget.env.hostView.nestedScrollPolicy = p.second
                        }
                    },
                    selected = i == idx,
                )
            }
            return Column(
                children = listOf(
                    Padding(
                        child = Text("纵向 Pager 内嵌纵向 List", style = TextStyle.Accent),
                        horizontal = 4, vertical = 2,
                    ),
                    Expanded(
                        child = PageViewBuilder(
                            axis = Axis.VERTICAL,
                            controller = pagerCtrl,
                            state = pagerState,
                            itemCount = 4,
                            itemBuilder = { pageIdx ->
                                Column(
                                    children = listOf(
                                        Center(child = Text("PAGE $pageIdx", style = TextStyle.Accent)),
                                        SizedBox(height = 2),
                                        Expanded(
                                            child = ListViewBuilder(
                                                state = listStates[pageIdx],
                                                controller = listCtrls[pageIdx],
                                                itemCount = 30,
                                                itemBuilder = { row ->
                                                    Padding(
                                                        child = Text("P$pageIdx · row $row", style = TextStyle.Default),
                                                        all = 2,
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                    mainAxisSize = MainAxisSize.MAX,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
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
