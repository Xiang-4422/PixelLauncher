package com.purride.pixeldemo.scaffold

import com.purride.pixelui.Column
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelcore.PixelColor

fun DemoScaffold(
    title: String,
    description: String,
    body: Widget,
    controls: List<Widget> = emptyList(),
): Widget {
    val rows = mutableListOf<Widget>(
        Padding(
            child = Column(
                children = listOf(
                    Text(title, style = TextStyle(color = PixelColor.fromRgb(200, 100, 0))),
                    Text(
                        description,
                        style = TextStyle.Default,
                        softWrap = false,
                        maxLines = 1,
                        overflow = PixelTextOverflow.ELLIPSIS,
                    ),
                ),
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            ),
            horizontal = 4,
            vertical = 2,
        ),
        Expanded(child = body),
    )
    if (controls.isNotEmpty()) {
        rows += Padding(
            child = Row(
                children = controls,
                spacing = 2,
            ),
            horizontal = 4,
            vertical = 2,
        )
    }
    return Column(
        children = rows,
        mainAxisSize = MainAxisSize.MAX,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}
