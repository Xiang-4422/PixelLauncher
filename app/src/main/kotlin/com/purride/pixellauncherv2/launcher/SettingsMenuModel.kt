package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelFontCatalog
import com.purride.pixellauncherv2.render.PixelFontId
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.PixelTheme
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory
import com.purride.pixellauncherv2.render.ChargeIdleEffect

enum class SettingsMenuItem {
    FONT,
    RESOLUTION,
    STYLE,
    THEME,
    APP_LIST_ALIGNMENT,
    IDLE_PAGE,
    DRAWER_AUTO_SEARCH,
    ADVANCED,
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
                item = SettingsMenuItem.APP_LIST_ALIGNMENT,
                title = "APP ALIGN",
                value = drawerListAlignmentLabel(state.drawerListAlignment),
            ),
            SettingsMenuRow(
                item = SettingsMenuItem.DRAWER_AUTO_SEARCH,
                title = "DRAWER SEARCH",
                value = onOffLabel(state.openDrawerInSearchMode),
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

    fun nextResolution(current: Int, direction: Int, screenProfile: ScreenProfile? = null): Int {
        val resolutionOptions = ScreenProfileFactory.resolutionOptions(screenProfile)
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
            PixelTheme.MONO_LCD -> "MONO"
        }
    }

    fun nextDrawerListAlignment(current: DrawerListAlignment, direction: Int): DrawerListAlignment {
        val currentIndex = DrawerListAlignment.entries.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, DrawerListAlignment.entries.size)
        return DrawerListAlignment.entries[nextIndex]
    }

    fun nextChargeIdleEffect(current: ChargeIdleEffect, direction: Int): ChargeIdleEffect {
        val currentIndex = ChargeIdleEffect.entries.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, ChargeIdleEffect.entries.size)
        return ChargeIdleEffect.entries[nextIndex]
    }

    fun drawerListAlignmentLabel(alignment: DrawerListAlignment): String {
        return when (alignment) {
            DrawerListAlignment.LEFT -> "LEFT"
            DrawerListAlignment.CENTER -> "CENTER"
            DrawerListAlignment.RIGHT -> "RIGHT"
        }
    }

    fun chargeIdleEffectLabel(effect: ChargeIdleEffect): String {
        return when (effect) {
            ChargeIdleEffect.FLUID -> "FLUID"
            ChargeIdleEffect.HORIZON -> "HORIZON"
            ChargeIdleEffect.STACK -> "STACK"
            ChargeIdleEffect.DOT_MATRIX -> "DOT MATRIX"
            ChargeIdleEffect.TANK -> "TANK"
            ChargeIdleEffect.CASCADE -> "CASCADE"
        }
    }

    fun toggle(value: Boolean): Boolean = !value

    fun onOffLabel(value: Boolean): String {
        return if (value) "ON" else "OFF"
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
