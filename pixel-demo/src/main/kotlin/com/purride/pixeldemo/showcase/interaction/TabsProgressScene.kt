package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object TabsProgressScene : DemoScene {
    override val id = "tabs_progress"
    override val title = "Tabs + Progress"
    override val description = "Tabs / SegmentedControl / ProgressBar / ActivityIndicator"

    override fun build(env: DemoEnv): Widget = TabsProgressWidget()
}

private class TabsProgressWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TabsProgressState()

    class TabsProgressState : State<TabsProgressWidget>() {
        private var selected = 0

        override fun build(context: BuildContext): Widget {
            return Column(
                children = listOf(
                    Tabs(labels = listOf("ONE", "TWO", "THREE"), selectedIndex = selected, onSelected = { setState { selected = it } }),
                    SegmentedControl(labels = listOf("LOW", "MID", "HIGH"), selectedIndex = selected, onSelected = { setState { selected = it } }),
                    ProgressBar(progress = (selected + 1) / 3f),
                    ActivityIndicator(frame = selected),
                    Text("selected=$selected"),
                ),
                spacing = 3,
                mainAxisSize = MainAxisSize.MAX,
            )
        }
    }
}
