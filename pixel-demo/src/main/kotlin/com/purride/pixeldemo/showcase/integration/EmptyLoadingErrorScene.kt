package com.purride.pixeldemo.showcase.integration

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelcore.PixelTone
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

private enum class ContentState { EMPTY, LOADING, ERROR }

object EmptyLoadingErrorScene : DemoScene {
    override val id = "empty_loading_error"
    override val title = "空 / 加载 / 错误三态"
    override val description = "ValueNotifier 驱动三态切换：空态 / 骨架加载 / 错误重试"

    override fun build(env: DemoEnv): Widget = EmptyLoadingErrorWidget()
}

private class EmptyLoadingErrorWidget(override val key: Any? = null) : com.purride.pixelui.StatelessWidget(key = key) {
    private val stateNotifier = ValueNotifier(ContentState.EMPTY)

    override fun build(context: BuildContext): Widget {
        return Column(
            children = listOf(
                Expanded(
                    child = ValueListenableBuilder(
                        listenable = stateNotifier,
                        builder = { _, state ->
                            when (state) {
                                ContentState.EMPTY -> emptyView()
                                ContentState.LOADING -> loadingView()
                                ContentState.ERROR -> errorView(stateNotifier)
                            }
                        },
                    ),
                ),
                SizedBox(height = 2),
                Row(
                    children = listOf(
                        OutlinedButton("Empty", onPressed = { stateNotifier.value = ContentState.EMPTY },
                            selected = stateNotifier.value == ContentState.EMPTY),
                        OutlinedButton("Loading", onPressed = { stateNotifier.value = ContentState.LOADING },
                            selected = stateNotifier.value == ContentState.LOADING),
                        OutlinedButton("Error", onPressed = { stateNotifier.value = ContentState.ERROR },
                            selected = stateNotifier.value == ContentState.ERROR),
                    ),
                    spacing = 2,
                ),
            ),
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}

private fun emptyView(): Widget = Center(
    child = Column(
        children = listOf(
            Text("暂无内容", style = TextStyle.Accent),
            SizedBox(height = 4),
            Text("这里什么都没有", style = TextStyle.Default),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    ),
)

private fun loadingView(): Widget = Padding(
    child = Column(
        children = (0 until 5).map { i ->
            Container(
                width = null,
                height = 12,
                fillTone = PixelTone.OFF,
                borderTone = null,
            )
        },
        spacing = 4,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    ),
    all = 8,
)

private fun errorView(notifier: ValueNotifier<ContentState>): Widget = Center(
    child = Column(
        children = listOf(
            Text("加载失败", style = TextStyle.Accent),
            SizedBox(height = 2),
            Text("网络异常，请检查连接后重试", style = TextStyle.Default, softWrap = true),
            SizedBox(height = 4),
            OutlinedButton("重试", onPressed = { notifier.value = ContentState.LOADING }),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    ),
)
