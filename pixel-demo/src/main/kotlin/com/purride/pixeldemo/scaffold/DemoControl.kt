package com.purride.pixeldemo.scaffold

import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Widget

fun segmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
): List<Widget> = options.mapIndexed { index, label ->
    OutlinedButton(
        text = label,
        onPressed = { onSelect(index) },
        selected = index == selectedIndex,
    )
}
