package com.purride.pixeldemo.showcase.composition

import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Badge
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Divider
import com.purride.pixelui.Gap
import com.purride.pixelui.Row
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object AppScaffoldScene : DemoScene {
    override val id = "app_scaffold"
    override val title = "AppScaffold"
    override val description = "title / body / bottomBar + Badge / Divider / Gap"

    override fun build(env: DemoEnv): Widget = AppScaffoldWidget()
}

private class AppScaffoldWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AppScaffoldState()

    class AppScaffoldState : State<AppScaffoldWidget>() {
        override fun build(context: BuildContext): Widget {
            return AppScaffold(
                title = Text("Inbox"),
                body = Column(
                    children = listOf(
                        Row(children = listOf(Badge(child = Text("MAIL"), label = Text("7")), Text("Unread messages")), spacing = 3),
                        Gap(height = 2),
                        Divider(),
                        Gap(height = 2),
                        Text("AppScaffold keeps common app chrome compact."),
                    ),
                    spacing = 1,
                ),
                bottomBar = Text("Bottom bar"),
            )
        }
    }
}
