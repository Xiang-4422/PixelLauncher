package com.purride.pixelui.internal

import com.purride.pixelui.TextInputSelectionHandle

internal object PixelTextInputSelectionGesture {
    fun resolveSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): Int {
        target.textIndexAt?.let { mapper ->
            return mapper(logicalX, logicalY).coerceIn(0, target.state.text.length)
        }
        val text = target.state.text
        if (text.isEmpty()) return 0
        val lines = text.split('\n')
        val lineCount = lines.size.coerceAtLeast(1)
        val lineHeight = (target.bounds.height / lineCount).coerceAtLeast(1)
        val localY = (logicalY - target.bounds.top).coerceAtLeast(0)
        val lineIndex = (localY / lineHeight).coerceIn(0, lineCount - 1)
        val line = lines[lineIndex]
        val localX = (logicalX - target.bounds.left).coerceIn(0, target.bounds.width)
        val column = if (line.isEmpty()) {
            0
        } else {
            (localX.toLong() * line.length / target.bounds.width.coerceAtLeast(1)).toInt()
        }
        var index = 0
        repeat(lineIndex) { lineOffset ->
            index += lines[lineOffset].length + 1
        }
        return (index + column.coerceIn(0, line.length)).coerceIn(0, text.length)
    }

    fun nearestHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): TextInputSelectionHandle? {
        val state = target.state
        if (state.selectionStart == state.selectionEnd) return null
        val selection = resolveSelection(target, logicalX, logicalY)
        val startDistance = kotlin.math.abs(selection - state.selectionStart)
        val endDistance = kotlin.math.abs(selection - state.selectionEnd)
        return if (startDistance <= endDistance) {
            TextInputSelectionHandle.START
        } else {
            TextInputSelectionHandle.END
        }
    }

    fun setCollapsedSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int) {
        val selection = resolveSelection(target, logicalX, logicalY)
        target.controller.setSelection(target.state, selection)
    }

    fun dragHandle(
        target: PixelTextInputTarget,
        handle: TextInputSelectionHandle?,
        logicalX: Int,
        logicalY: Int,
    ): Boolean {
        if (target.readOnly || handle == null) return false
        val selection = resolveSelection(target, logicalX, logicalY)
        when (handle) {
            TextInputSelectionHandle.START -> target.controller.setSelection(
                target.state,
                selection.coerceAtMost(target.state.selectionEnd),
                target.state.selectionEnd,
            )
            TextInputSelectionHandle.END -> target.controller.setSelection(
                target.state,
                target.state.selectionStart,
                selection.coerceAtLeast(target.state.selectionStart),
            )
        }
        return true
    }
}
