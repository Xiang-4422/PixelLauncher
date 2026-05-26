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

public fun Dialog(
    title: Widget? = null,
    content: Widget,
    actions: List<Widget> = emptyList(),
    fillColor: PixelColor = PixelColor.Black,
    borderColor: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget {
    val children = buildList {
        if (title != null) add(title)
        add(content)
        if (actions.isNotEmpty()) add(Row(children = actions, spacing = 2, mainAxisAlignment = MainAxisAlignment.END))
    }
    return Center(
        child = Container(
            width = 96,
            padding = EdgeInsets.all(3),
            fillColor = fillColor,
            borderColor = borderColor,
            child = Column(children = children, spacing = 2),
            key = key,
        ),
    )
}

public fun Toast(
    message: String,
    fillColor: PixelColor = PixelColor.Black,
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    return Center(
        child = Container(
            padding = EdgeInsets.symmetric(horizontal = 4, vertical = 2),
            fillColor = fillColor,
            borderColor = PixelColor.White,
            child = Text(message, style = textStyle),
            key = key,
        ),
    )
}

public fun Snackbar(
    message: String,
    action: Widget? = null,
    fillColor: PixelColor = PixelColor.fromRgb(40, 40, 40),
    textStyle: PixelTextStyle = PixelTextStyle.Default,
    key: Any? = null,
): Widget {
    val rowChildren = if (action == null) {
        listOf<Widget>(Expanded(child = Text(message, style = textStyle)))
    } else {
        listOf(Expanded(child = Text(message, style = textStyle)), action)
    }
    return Container(
        padding = EdgeInsets.symmetric(horizontal = 3, vertical = 2),
        fillColor = fillColor,
        borderColor = PixelColor.White,
        child = Row(children = rowChildren, spacing = 2, crossAxisAlignment = CrossAxisAlignment.CENTER),
        key = key,
    )
}

public fun Tabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
): Widget = Row(
    children = labels.mapIndexed { index, label ->
        OutlinedButton(
            text = label,
            onPressed = { onSelected(index) },
            borderColor = if (index == selectedIndex) PixelColor.fromRgb(80, 180, 110) else PixelColor.White,
        )
    },
    spacing = 1,
    key = key,
)

public fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    key: Any? = null,
): Widget = Row(
    children = labels.mapIndexed { index, label ->
        Container(
            fillColor = if (index == selectedIndex) PixelColor.White else PixelColor.Transparent,
            borderColor = PixelColor.White,
            child = GestureDetector(
                child = Padding(child = Text(label, style = TextStyle(color = if (index == selectedIndex) PixelColor.Black else PixelColor.White)), horizontal = 2, vertical = 1),
                onTap = { onSelected(index) },
            ),
        )
    },
    spacing = 0,
    key = key,
)

public fun ProgressBar(
    progress: Float,
    width: Int = 48,
    height: Int = 5,
    color: PixelColor = PixelColor.fromRgb(80, 180, 110),
    trackColor: PixelColor = PixelColor.fromRgb(60, 60, 60),
    key: Any? = null,
): Widget {
    val safeProgress = progress.coerceIn(0f, 1f)
    val fillWidth = (width * safeProgress).toInt().coerceIn(0, width)
    return Stack(
        children = listOf(
            Container(width = width, height = height, fillColor = trackColor, borderColor = PixelColor.White),
            Container(width = fillWidth, height = height, fillColor = color),
        ),
        key = key,
    )
}

public fun ActivityIndicator(
    frame: Int = 0,
    color: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget {
    val dots = List(4) { index ->
        Container(
            width = 3,
            height = 3,
            fillColor = if (index == frame.floorMod(4)) color else PixelColor.fromRgb(60, 60, 60),
        )
    }
    return Row(children = dots, spacing = 1, key = key)
}

public fun Badge(
    child: Widget,
    label: Widget,
    key: Any? = null,
): Widget = Stack(
    children = listOf(
        child,
        Positioned(
            top = 0,
            right = 0,
            child = Container(
                padding = EdgeInsets.symmetric(horizontal = 1, vertical = 0),
                fillColor = PixelColor.fromRgb(220, 90, 80),
                borderColor = PixelColor.White,
                child = label,
            ),
        ),
    ),
    key = key,
)

public fun Divider(
    color: PixelColor = PixelColor.White,
    thickness: Int = 1,
    key: Any? = null,
): Widget = Container(height = thickness.coerceAtLeast(1), fillColor = color, key = key)

public fun Gap(
    width: Int = 0,
    height: Int = 0,
    key: Any? = null,
): Widget = SizedBox(width = width, height = height, key = key)

public fun AppScaffold(
    title: Widget? = null,
    body: Widget,
    bottomBar: Widget? = null,
    key: Any? = null,
): Widget {
    val children = buildList {
        if (title != null) {
            add(Container(padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1), borderColor = PixelColor.White, child = title))
            add(Gap(height = 1))
        }
        add(Expanded(child = body))
        if (bottomBar != null) {
            add(Gap(height = 1))
            add(bottomBar)
        }
    }
    return Column(children = children, mainAxisSize = MainAxisSize.MAX, crossAxisAlignment = CrossAxisAlignment.STRETCH, key = key)
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder < 0) remainder + divisor else remainder
}
