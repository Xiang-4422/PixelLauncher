package com.purride.pixelui

import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import com.purride.pixelui.internal.PixelTextInputTarget

private const val ACTION_COPY = 1
private const val ACTION_CUT = 2
private const val ACTION_PASTE = 3
private const val ACTION_SELECT_ALL = 4

internal fun PixelHostView.showPixelTextSelectionActionMode(target: PixelTextInputTarget) {
    val callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val hasSelection = target.state.selectionStart < target.state.selectionEnd
            val editable = !target.readOnly
            if (hasSelection) {
                menu.add(Menu.NONE, ACTION_COPY, 0, "Copy").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (hasSelection && editable) {
                menu.add(Menu.NONE, ACTION_CUT, 1, "Cut").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (editable && !effectiveHostServices.clipboardTextOrNull().isNullOrEmpty()) {
                menu.add(Menu.NONE, ACTION_PASTE, 2, "Paste").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            if (target.state.text.isNotEmpty() && !isAllSelected(target)) {
                menu.add(Menu.NONE, ACTION_SELECT_ALL, 3, "Select all")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            return menu.size() > 0
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val action = when (item.itemId) {
                ACTION_COPY -> PixelTextEditAction.COPY
                ACTION_CUT -> PixelTextEditAction.CUT
                ACTION_PASTE -> PixelTextEditAction.PASTE
                ACTION_SELECT_ALL -> PixelTextEditAction.SELECT_ALL
                else -> return false
            }
            val handled = performFocusedTextEditAction(action)
            if (handled) mode.finish()
            return handled
        }

        override fun onDestroyActionMode(mode: ActionMode): Unit = Unit
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        startActionMode(callback, ActionMode.TYPE_FLOATING)
    } else {
        @Suppress("DEPRECATION")
        startActionMode(callback)
    }
}

private fun isAllSelected(target: PixelTextInputTarget): Boolean {
    return target.state.selectionStart == 0 &&
        target.state.selectionEnd == target.state.text.length
}
