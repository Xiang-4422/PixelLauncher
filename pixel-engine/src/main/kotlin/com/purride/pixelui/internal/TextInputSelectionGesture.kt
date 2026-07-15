package com.purride.pixelui.internal

import com.purride.pixelui.TextInputSelectionHandle

/** 集中提供 `TextInputSelectionGesture` 共享的工厂、常量或无状态辅助入口。 */
public object PixelTextInputSelectionGesture {
    /** 按 `TextInputSelectionGesture` 的规则解析 `resolveSelection` 目标，并返回稳定的匹配结果。 */
    public fun resolveSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): Int {
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

    /** 查询 `TextInputSelectionGesture` 的 `nearestHandle` 派生结果；该读取不会改变已保存状态。 */
    public fun nearestHandle(target: PixelTextInputTarget, logicalX: Int, logicalY: Int): TextInputSelectionHandle? {
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

    /** 更新 `TextInputSelectionGesture` 的 `setCollapsedSelection` 状态，并保持相关边界与派生状态一致。 */
    public fun setCollapsedSelection(target: PixelTextInputTarget, logicalX: Int, logicalY: Int) {
        val selection = resolveSelection(target, logicalX, logicalY)
        target.controller.setSelection(target.state, selection)
    }

    /** 处理 `TextInputSelectionGesture` 的 `dragHandle` 手势阶段，并保持命中目标与消费状态一致。 */
    public fun dragHandle(
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
