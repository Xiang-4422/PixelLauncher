package com.purride.pixelui

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor

public data class PixelIconData(
    val bitmap: PixelBitmap,
)

public fun Icon(
    icon: PixelIconData,
    key: Any? = null,
): Widget = Image(bitmap = icon.bitmap, key = key)

public fun ListTile(
    title: Widget,
    subtitle: Widget? = null,
    leading: Widget? = null,
    trailing: Widget? = null,
    onTap: (() -> Unit)? = null,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    val texts = if (subtitle == null) title else Column(children = listOf(title, subtitle), spacing = 1)
    val rowChildren = buildList {
        if (leading != null) add(leading)
        add(Expanded(child = texts))
        if (trailing != null) add(trailing)
    }
    val content = Container(
        child = Row(children = rowChildren, spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER),
        padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
        fillColor = if (enabled) null else PixelColor.fromArgb(80, 80, 80, 80),
        key = key,
    )
    return if (enabled && onTap != null) GestureDetector(child = content, onTap = onTap, key = key) else content
}

public fun Checkbox(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor = PixelColor.White,
    inactiveColor: PixelColor = PixelColor.fromRgb(120, 120, 120),
    key: Any? = null,
): Widget {
    val color = if (!enabled) PixelColor.fromRgb(80, 80, 80) else if (checked) activeColor else inactiveColor
    val mark = if (checked) "X" else " "
    val box = Container(
        width = 9,
        height = 9,
        borderColor = color,
        child = Center(child = Text(mark, style = TextStyle(color = color))),
    )
    return if (enabled && onChanged != null) {
        GestureDetector(child = box, onTap = { onChanged(!checked) }, key = key)
    } else {
        box
    }
}

public fun Switch(
    checked: Boolean,
    onChanged: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    activeColor: PixelColor = PixelColor.fromRgb(80, 180, 110),
    inactiveColor: PixelColor = PixelColor.fromRgb(120, 120, 120),
    key: Any? = null,
): Widget {
    val border = if (!enabled) PixelColor.fromRgb(80, 80, 80) else if (checked) activeColor else inactiveColor
    val thumb = Container(width = 5, height = 5, fillColor = border)
    val spacer = SizedBox(width = 5, height = 5)
    val track = Container(
        width = 14,
        height = 7,
        borderColor = border,
        padding = EdgeInsets.all(1),
        child = Row(children = if (checked) listOf(spacer, thumb) else listOf(thumb, spacer)),
    )
    return if (enabled && onChanged != null) {
        GestureDetector(child = track, onTap = { onChanged(!checked) }, key = key)
    } else {
        track
    }
}
