package com.purride.pixeldemo.scaffold

import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Widget
import com.purride.pixelcore.PixelColor

fun segmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
): List<Widget> = options.mapIndexed { index, label ->
    OutlinedButton(
        text = label,
        onPressed = { onSelect(index) },
        borderColor = if (index == selectedIndex) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
    )
}
