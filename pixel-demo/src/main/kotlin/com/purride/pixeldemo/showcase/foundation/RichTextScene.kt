package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Center
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.RichText
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object RichTextScene : DemoScene {
    override val id = "rich_text"
    override val title = "富文本"
    override val description = "多 span 混排：Default / Accent / 自定义 tone"

    override fun build(env: DemoEnv): Widget = Center(
        child = RichText(
            spans = listOf(
                PixelTextSpan(text = "Hello, ", style = TextStyle.Default),
                PixelTextSpan(text = "PIXEL", style = TextStyle.Accent),
                PixelTextSpan(text = " world! ", style = TextStyle.Default),
                PixelTextSpan(text = "这是富文本", style = TextStyle.Accent),
                PixelTextSpan(text = "，支持", style = TextStyle.Default),
                PixelTextSpan(
                    text = "自定义 tone",
                    style = TextStyle.Default.copy(tone = PixelTone.ON),
                ),
                PixelTextSpan(text = " 混排。", style = TextStyle.Default),
            ),
            softWrap = true,
            maxLines = Int.MAX_VALUE,
        ),
    )
}
