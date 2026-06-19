package com.purride.pixeldemo.showcase

import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCategory
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.DemoEnv

class ComponentExampleScene(
    override val id: String,
    override val title: String,
    override val summary: String,
    override val category: DemoCategory,
    override val tags: Set<String>,
    override val apis: Set<String>,
    private val bodyBuilder: (DemoEnv) -> Widget,
) : DemoScene {
    override val isFullScreen: Boolean = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = bodyBuilder(env))
}
