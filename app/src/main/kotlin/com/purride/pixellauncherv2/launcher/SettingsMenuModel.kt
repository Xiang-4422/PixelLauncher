package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.render.ScreenProfileFactory

enum class SettingsMenuItem {
    RESOLUTION,
    PIXEL_GAP,
    STYLE,
    THEME,
    HOME_STATUS,
    APP_LIST_ALIGNMENT,
    IDLE_PAGE,
    CHARGE_AUTO_IDLE,
    INACTIVITY_AUTO_IDLE,
    IDLE_TIMEOUT,
    CHARGE_IDLE_EFFECT,
    DRAWER_AUTO_SEARCH,
    APP_MANAGEMENT,
    NOTIFICATIONS,
    DATA_HEALTH,
    ADVANCED,
}

enum class SettingsSection {
    DISPLAY,
    HOME,
    DRAWER,
    IDLE,
    DATA,
    ADVANCED,
}

data class SettingsMenuRow(
    val item: SettingsMenuItem,
    val title: String,
    val value: String = "",
    val section: SettingsSection = SettingsSection.DISPLAY,
)

object SettingsMenuModel {

    val styleOptions: List<PixelShape> = listOf(
        PixelShape.SQUARE,
        PixelShape.CIRCLE,
        PixelShape.DIAMOND,
    )
    val pixelSizePresetLabels: List<String> = listOf("S", "M", "L")
    val pixelSizePresetValues: List<Int> = listOf(7, 8, 10)
    val themeOptions: List<PixelTheme> = PixelTheme.entries
    fun rows(state: LauncherState, screenProfile: ScreenProfile? = null): List<SettingsMenuRow> {
        return buildList {
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.RESOLUTION,
                    title = "PIXEL SIZE",
                    value = resolutionLabel(state.selectedDotSizePx, screenProfile),
                    section = SettingsSection.DISPLAY,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.PIXEL_GAP,
                    title = "GAP",
                    value = onOffLabel(state.isPixelGapEnabled),
                    section = SettingsSection.DISPLAY,
                ),
            )
            if (state.isPixelGapEnabled) {
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.STYLE,
                        title = "STYLE",
                        value = styleLabel(state.selectedPixelShape),
                        section = SettingsSection.DISPLAY,
                    ),
                )
            }
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.THEME,
                    title = "THEME",
                    value = themeLabel(state.selectedTheme),
                    section = SettingsSection.DISPLAY,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.HOME_STATUS,
                    title = "HOME STATUS",
                    value = HomeInfoModel.summary(state),
                    section = SettingsSection.HOME,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.APP_LIST_ALIGNMENT,
                    title = "APP ALIGN",
                    value = drawerListAlignmentLabel(state.drawerListAlignment),
                    section = SettingsSection.DRAWER,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.DRAWER_AUTO_SEARCH,
                    title = "DRAWER SEARCH",
                    value = onOffLabel(state.openDrawerInSearchMode),
                    section = SettingsSection.DRAWER,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.APP_MANAGEMENT,
                    title = "APP MANAGE",
                    value = if (state.apps.isEmpty()) "EMPTY" else "OPEN",
                    section = SettingsSection.DRAWER,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.IDLE_PAGE,
                    title = "IDLE PAGE",
                    value = onOffLabel(state.isIdlePageEnabled),
                    section = SettingsSection.IDLE,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.CHARGE_AUTO_IDLE,
                    title = "CHARGE IDLE",
                    value = onOffLabel(state.chargeAutoIdleEnabled),
                    section = SettingsSection.IDLE,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.INACTIVITY_AUTO_IDLE,
                    title = "AUTO IDLE",
                    value = onOffLabel(state.inactivityAutoIdleEnabled),
                    section = SettingsSection.IDLE,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.IDLE_TIMEOUT,
                    title = "IDLE TIME",
                    value = idleTimeoutLabel(state.idleTimeoutSeconds),
                    section = SettingsSection.IDLE,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.CHARGE_IDLE_EFFECT,
                    title = "IDLE EFFECT",
                    value = chargeIdleEffectLabel(state.chargeIdleEffect),
                    section = SettingsSection.IDLE,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.NOTIFICATIONS,
                    title = "NOTIFY",
                    value = NotificationSettingsModel.summary(
                        mutedSourceIds = state.mutedNotificationSourceIds,
                        prioritySourceIds = state.priorityNotificationSourceIds,
                    ),
                    section = SettingsSection.DATA,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.DATA_HEALTH,
                    title = "DATA HEALTH",
                    value = DataHealthModel.summary(state),
                    section = SettingsSection.DATA,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.ADVANCED,
                    title = "ADVANCED",
                    value = "OPEN",
                    section = SettingsSection.ADVANCED,
                ),
            )
        }
    }

    fun sections(state: LauncherState, screenProfile: ScreenProfile? = null): List<SettingsSection> {
        return rows(state, screenProfile).map { it.section }.distinct()
    }

    fun selectedItem(state: LauncherState): SettingsMenuItem {
        val rows = rows(state)
        return rows[state.settingsSelectedIndex.coerceIn(0, rows.lastIndex)].item
    }

    fun nextStyle(current: PixelShape, direction: Int): PixelShape {
        val currentIndex = styleOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, styleOptions.size)
        return styleOptions[nextIndex]
    }

    fun nextResolution(current: Int, direction: Int, screenProfile: ScreenProfile? = null): Int {
        val options = resolutionOptions(screenProfile)
        val nextIndex = wrapIndex(resolutionIndex(current, screenProfile) + direction, options.size)
        return options[nextIndex]
    }

    fun resolutionOptions(screenProfile: ScreenProfile? = null): List<Int> {
        return ScreenProfileFactory.resolutionOptions(screenProfile)
    }

    fun resolutionIndex(current: Int, screenProfile: ScreenProfile? = null): Int {
        return resolutionOptions(screenProfile).indexOf(current).takeIf { it >= 0 } ?: 0
    }

    fun pixelSizePresetIndex(current: Int): Int {
        return pixelSizePresetValues.indexOf(current)
    }

    fun pixelSizePresetValue(index: Int): Int {
        return pixelSizePresetValues[index.coerceIn(0, pixelSizePresetValues.lastIndex)]
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
        return theme.displayLabel
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

    fun nextIdleTimeoutSeconds(current: Int, direction: Int): Int {
        return IdleSettings.nextTimeoutSeconds(current = current, direction = direction)
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

    fun idleTimeoutLabel(seconds: Int): String = IdleSettings.timeoutLabel(seconds)

    fun sectionLabel(section: SettingsSection): String {
        return when (section) {
            SettingsSection.DISPLAY -> "DISPLAY"
            SettingsSection.HOME -> "HOME"
            SettingsSection.DRAWER -> "DRAWER"
            SettingsSection.IDLE -> "IDLE"
            SettingsSection.DATA -> "DATA"
            SettingsSection.ADVANCED -> "ADVANCED"
        }
    }

    fun toggle(value: Boolean): Boolean = !value

    fun onOffLabel(value: Boolean): String {
        return if (value) "ON" else "OFF"
    }

    fun resolutionLabel(dotSizePx: Int, screenProfile: ScreenProfile? = null): String {
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
