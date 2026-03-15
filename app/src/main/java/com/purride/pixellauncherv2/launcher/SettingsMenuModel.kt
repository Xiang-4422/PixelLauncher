package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory

enum class SettingsMenuItem {
    FONT,
    RESOLUTION,
    STYLE,
    THEME,
    ADVANCED,
    CLOSE,
}

data class SettingsMenuRow(
    val item: SettingsMenuItem,
    val title: String,
    val value: String = "",
)

object SettingsMenuModel {

    val fontOptions: List<PixelFontId> = PixelFontCatalog.settingsFontOptions()
    val styleOptions: List<PixelShape> = listOf(
        PixelShape.SQUARE,
        PixelShape.CIRCLE,
        PixelShape.DIAMOND,
    )
    val themeOptions: List<PixelTheme> = PixelTheme.entries
    val resolutionOptions: List<Int> = ScreenProfileFactory.supportedDotSizePxOptions

    fun rows(state: LauncherState, screenProfile: ScreenProfile? = null): List<SettingsMenuRow> {
        return listOf(
            SettingsMenuRow(
                item = SettingsMenuItem.FONT,
                title = "FONT",
                value = PixelFontCatalog.settingsLabel(state.selectedFontId),
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.RESOLUTION,
                title = "RESOLUTION",
                value = resolutionLabel(state.selectedDotSizePx, screenProfile),
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.STYLE,
                title = "STYLE",
                value = styleLabel(state.selectedPixelShape),
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.THEME,
                title = "THEME",
                value = themeLabel(state.selectedTheme),
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.ADVANCED,
                title = "ADVANCED",
                value = "OPEN",
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.CLOSE,
                title = "CLOSE",
                value = "BACK",
            ),
        )
    }

    fun selectedItem(state: LauncherState): SettingsMenuItem {
        val rows = rows(state)
        return rows[state.settingsSelectedIndex.coerceIn(0, rows.lastIndex)].item
    }

    fun nextFont(current: PixelFontId, direction: Int): PixelFontId {
        val currentIndex = fontOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, fontOptions.size)
        return fontOptions[nextIndex]
    }

    fun nextStyle(current: PixelShape, direction: Int): PixelShape {
        val currentIndex = styleOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, styleOptions.size)
        return styleOptions[nextIndex]
    }

    fun nextResolution(current: Int, direction: Int): Int {
        val currentIndex = resolutionOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, resolutionOptions.size)
        return resolutionOptions[nextIndex]
    }

    fun nextTheme(current: PixelTheme, direction: Int): PixelTheme {
        val currentIndex = themeOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, themeOptions.size)
        return themeOptions[nextIndex]
    }

    fun styleLabel(pixelShape: PixelShape): String {
        return when (pixelShape) {
            PixelShape.SQUARE -> "SQUARE"
            PixelShape.CIRCLE -> "CIRCLE"
            PixelShape.DIAMOND -> "DIAMOND"
        }
    }

    fun themeLabel(theme: PixelTheme): String {
        return when (theme) {
            PixelTheme.GREEN_PHOSPHOR -> "GREEN"
            PixelTheme.AMBER_CRT -> "AMBER"
            PixelTheme.ICE_LCD -> "ICE"
        }
    }

    fun resolutionLabel(dotSizePx: Int, screenProfile: ScreenProfile? = null): String {
        if (screenProfile != null && screenProfile.dotSizePx == dotSizePx) {
            return "${screenProfile.logicalWidth}X${screenProfile.logicalHeight}"
        }
        return "${dotSizePx}PX"
    }

    fun displayValue(row: SettingsMenuRow): String {
        if (row.value.isBlank()) {
            return ""
        }
        return "<${row.value}>"
    }

    private fun wrapIndex(index: Int, size: Int): Int {
        if (size <= 0) {
            return 0
        }
        val mod = index % size
        return if (mod < 0) mod + size else mod
    }
}
