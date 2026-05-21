package com.purride.pixeldemo.showcase.integration

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PageController
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelcore.PixelAxis
import com.purride.pixelui.Axis
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object ConfigChangePreserveScene : DemoScene {
    override val id = "config_change_preserve"
    override val title = "配置变更保留"
    override val description = "旋转后三处状态全部保留"

    override fun build(env: DemoEnv): Widget = ConfigChangePreserveWidget()
}

private class ConfigChangePreserveWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ConfigChangePreserveState()

    class ConfigChangePreserveState : State<ConfigChangePreserveWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private val pagerState = PixelPagerState(axis = PixelAxis.HORIZONTAL, pageCount = 10)
        private val pagerCtrl = PageController()
        private val tfState = PixelTextFieldState()
        private val tfCtrl = TextEditingController()

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Padding(
                        child = Text("旋转设备后，下方三处状态应全部保留", style = TextStyle.Default, softWrap = true),
                        all = 4,
                    ),
                    SizedBox(height = 2),
                    Text("TextField", style = TextStyle.Default),
                    TextField(state = tfState, controller = tfCtrl, placeholder = "输入后旋转，内容保留"),
                    SizedBox(height = 4),
                    Text("Pager (10 页)", style = TextStyle.Default),
                    SizedBox(
                        height = 40,
                        child = PageViewBuilder(
                            axis = Axis.HORIZONTAL,
                            controller = pagerCtrl,
                            state = pagerState,
                            itemCount = 10,
                            itemBuilder = { i ->
                                Center(child = Text("Page $i", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))))
                            },
                        ),
                    ),
                    SizedBox(height = 4),
                    Text("List (100 项)", style = TextStyle.Default),
                    Expanded(
                        child = ListViewBuilder(
                            state = listState,
                            controller = listCtrl,
                            itemCount = 100,
                            itemBuilder = { i ->
                                Padding(
                                    child = Text("Item $i", style = TextStyle.Default),
                                    all = 3,
                                )
                            },
                        ),
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
