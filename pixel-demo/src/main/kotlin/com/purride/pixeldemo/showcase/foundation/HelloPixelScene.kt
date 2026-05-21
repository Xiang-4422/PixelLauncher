package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelui.Center
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixelcore.PixelColor

object HelloPixelScene : DemoScene {
    override val id = "hello_pixel"
    override val title = "HELLO PIXEL"
    override val description = "最小可运行 widget 树"

    override fun build(env: DemoEnv): Widget =
        Center(child = Text("HELLO PIXEL", style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))))
}
