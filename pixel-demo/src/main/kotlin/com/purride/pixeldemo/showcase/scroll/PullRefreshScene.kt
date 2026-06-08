package com.purride.pixeldemo.showcase.scroll

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object PullRefreshScene : DemoScene {
    override val id = "pull_refresh"
    override val title = "Pull-to-refresh"
    override val description = "RefreshIndicator 下拉阈值、刷新态和完成回调"

    override fun build(env: DemoEnv): Widget = PullRefreshWidget()
}

private class PullRefreshWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = PullRefreshState()

    class PullRefreshState : State<PullRefreshWidget>() {
        private val listState = PixelListState()
        private val listController = ScrollController()
        private val refreshController = PixelRefreshIndicatorController()
        private val refreshState = refreshController.create()
        private var refreshCount = 0

        override fun build(context: BuildContext): Widget {
            val status = when {
                refreshState.isRefreshing -> "refreshing #$refreshCount"
                refreshState.isArmed -> "release to refresh"
                refreshState.pullDistancePx > 0f -> "pull ${refreshState.pullDistancePx.toInt()}"
                else -> "pull from top"
            }
            return Column(
                children = listOf(
                    Expanded(
                        child = RefreshIndicator(
                            state = refreshState,
                            controller = refreshController,
                            thresholdPx = 14,
                            indicatorColor = PixelColor.fromRgb(120, 220, 255),
                            armedColor = PixelColor.fromRgb(200, 100, 0),
                            refreshingColor = PixelColor.fromRgb(255, 255, 0),
                            onRefresh = {
                                setState { refreshCount += 1 }
                            },
                            child = ListViewBuilder(
                                itemCount = 36,
                                itemBuilder = { index ->
                                    Padding(
                                        child = Text("Row $index  $status", style = TextStyle.Default),
                                        horizontal = 6,
                                        vertical = 2,
                                    )
                                },
                                state = listState,
                                controller = listController,
                                itemExtent = 12,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Row(
                        children = listOf(
                            OutlinedButton("Top", onPressed = { listController.jumpToStart(listState) }),
                            OutlinedButton("Done", onPressed = {
                                setState { refreshController.completeRefresh(refreshState) }
                            }),
                        ),
                        spacing = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}

