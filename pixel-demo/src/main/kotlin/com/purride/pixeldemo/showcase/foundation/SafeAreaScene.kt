package com.purride.pixeldemo.showcase.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SizedBox
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object SafeAreaScene : DemoScene {
    override val id = "safe_area"
    override val title = "SafeArea"
    override val description = "MediaQuery window insets 转换成内容安全边距"

    override fun build(env: DemoEnv): Widget = SafeAreaShowcase()
}

private class SafeAreaShowcase : StatelessWidget() {
    override fun build(context: BuildContext): Widget {
        val media = MediaQuery.of(context)
        val previewPadding = PixelWindowInsets(left = 8, top = 6, right = 4, bottom = 2)
        val previewIme = PixelWindowInsets(bottom = 6)
        return MediaQuery(
            data = media.copy(
                viewInsets = previewIme,
                viewPadding = previewPadding,
                padding = previewPadding.copy(bottom = 0),
            ),
            child = Column(
                children = listOf(
                    Text("SAFE L8 T6 R4 B0  IME B6", style = TextStyle.Default),
                    SizedBox(height = 4),
                    Container(
                        width = 140,
                        height = 70,
                        borderColor = PixelColor.White,
                        fillColor = PixelColor.Transparent,
                        child = SafeArea(
                            child = Container(
                                fillColor = PixelColor.fromRgb(200, 100, 0),
                                borderColor = PixelColor.White,
                                child = Center(
                                    child = Text(
                                        "SAFE",
                                        style = TextStyle(color = PixelColor.Black),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
            ),
        )
    }
}
