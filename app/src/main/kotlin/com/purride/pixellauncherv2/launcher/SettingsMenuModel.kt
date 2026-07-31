package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.BuildConfig
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory

enum class SettingsMenuItem {
    RESOLUTION,
    PIXEL_GAP,
    STYLE,
    THEME,
    THEME_MODE,
    FONT,
    FONT_WIDTH,
    FONT_SIZE,
    APP_LIST_ALIGNMENT,
    IDLE_PAGE,
    CHARGE_AUTO_IDLE,
    INACTIVITY_AUTO_IDLE,
    IDLE_TIMEOUT,
    CHARGE_IDLE_EFFECT,
    DRAWER_AUTO_SEARCH,
    MORE,
    NOTIFICATIONS,
    DATA_HEALTH,
    LOADING_PREVIEW,
    PIXEL_MATTER_EFFECT,
    PIXEL_MATTER_EFFECT_MODE,
    PIXEL_MATTER_HAND_CONTROL,
    PIXEL_MATTER_HAND_DEBUG,
    ADVANCED,
}

enum class SettingsSection {
    /** 点阵尺度、像素间隙与像素形状等显示网格设置。 */
    DISPLAY,
    /** 颜色主题、明暗模式与字体排版设置。 */
    THEME,
    /** 应用抽屉的布局与打开行为设置。 */
    DRAWER,
    /** 待机页及其自动进入条件设置。 */
    IDLE,
    /** 进入低频设置二级页面的入口分类。 */
    MORE,
    /** 通知来源的展示优先级设置。 */
    NOTIFICATIONS,
    /** 系统权限、角色与数据访问状态入口。 */
    ACCESS,
    /** 面向用户的实验性交互效果。 */
    EXPERIMENTAL,
    /** 仅调试构建可见的组件预览与诊断工具。 */
    DEVELOPER,
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
    /** 设置页允许循环选择的主题家族。 */
    val themeFamilyOptions: List<LauncherThemeFamily> = LauncherThemeFamily.entries
    /** 设置页允许循环选择的亮暗模式。 */
    val themeModeOptions: List<LauncherThemeMode> = LauncherThemeMode.entries
    /** 设置页允许循环选择的字体家族。 */
    val fontOptions: List<LauncherFontFamily> = PixelFontCatalog.fontFamilyOptions()

    /** 生成与当前状态一致的顶层设置菜单行。 */
    fun rows(state: LauncherState): List<SettingsMenuRow> {
        return buildList {
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.RESOLUTION,
                    title = "PIXEL SIZE",
                    value = resolutionLabel(state.selectedDotSizePx),
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
                    value = themeFamilyLabel(state.selectedThemeFamily),
                    section = SettingsSection.THEME,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.THEME_MODE,
                    title = "MODE",
                    value = themeModeLabel(state.selectedThemeMode),
                    section = SettingsSection.THEME,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.FONT,
                    title = "FONT",
                    value = fontLabel(state.fontSelection.family),
                    section = SettingsSection.THEME,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.FONT_WIDTH,
                    title = "WIDTH",
                    value = fontWidthLabel(state.fontSelection.widthMode),
                    section = SettingsSection.THEME,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.FONT_SIZE,
                    title = "SIZE",
                    value = fontSizeLabel(state.fontSelection.size),
                    section = SettingsSection.THEME,
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
                    item = SettingsMenuItem.IDLE_PAGE,
                    title = "ENABLE",
                    value = onOffLabel(state.isIdlePageEnabled),
                    section = SettingsSection.IDLE,
                ),
            )
            if (state.isIdlePageEnabled) {
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.CHARGE_AUTO_IDLE,
                        title = "CHARGE",
                        value = onOffLabel(state.chargeAutoIdleEnabled),
                        section = SettingsSection.IDLE,
                    ),
                )
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.INACTIVITY_AUTO_IDLE,
                        title = "AUTO",
                        value = onOffLabel(state.inactivityAutoIdleEnabled),
                        section = SettingsSection.IDLE,
                    ),
                )
                if (state.inactivityAutoIdleEnabled) {
                    add(
                        SettingsMenuRow(
                            item = SettingsMenuItem.IDLE_TIMEOUT,
                            title = "TIMEOUT",
                            value = idleTimeoutLabel(state.idleTimeoutSeconds),
                            section = SettingsSection.IDLE,
                        ),
                    )
                }
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.CHARGE_IDLE_EFFECT,
                        title = "EFFECT",
                        value = chargeIdleEffectLabel(state.chargeIdleEffect),
                        section = SettingsSection.IDLE,
                    ),
                )
            }
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.MORE,
                    title = "MORE",
                    value = "OPEN",
                    section = SettingsSection.MORE,
                ),
            )
        }
    }

    /**
     * 生成 MORE 二级页面的低频设置行。
     *
     * @param includeDeveloperTools 是否包含只供开发验收使用的预览与诊断入口。
     */
    fun moreRows(
        state: LauncherState,
        includeDeveloperTools: Boolean = BuildConfig.DEBUG,
    ): List<SettingsMenuRow> {
        return buildList {
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.NOTIFICATIONS,
                    title = "WHITELIST",
                    value = NotificationSettingsModel.summary(state.allowedNotificationSourceIds),
                    section = SettingsSection.NOTIFICATIONS,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.DATA_HEALTH,
                    title = "PERMISSIONS",
                    value = DataHealthModel.summary(state),
                    section = SettingsSection.ACCESS,
                ),
            )
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.PIXEL_MATTER_EFFECT,
                    title = "SHAKE",
                    value = onOffLabel(state.isPixelMatterEffectEnabled),
                    section = SettingsSection.EXPERIMENTAL,
                ),
            )
            if (state.isPixelMatterEffectEnabled) {
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE,
                        title = "SHAKE MODE",
                        value = pixelMatterEffectModeLabel(state.pixelMatterEffectMode),
                        section = SettingsSection.EXPERIMENTAL,
                    ),
                )
            }
            add(
                SettingsMenuRow(
                    item = SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL,
                    title = "HAND",
                    value = onOffLabel(state.isPixelMatterHandControlEnabled),
                    section = SettingsSection.EXPERIMENTAL,
                ),
            )
            if (includeDeveloperTools) {
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.LOADING_PREVIEW,
                        title = "LOADING PREVIEW",
                        value = "OPEN",
                        section = SettingsSection.DEVELOPER,
                    ),
                )
                add(
                    SettingsMenuRow(
                        item = SettingsMenuItem.ADVANCED,
                        title = "DIAGNOSTICS",
                        value = "OPEN",
                        section = SettingsSection.DEVELOPER,
                    ),
                )
                if (state.isPixelMatterHandControlEnabled) {
                    add(
                        SettingsMenuRow(
                            item = SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG,
                            title = "HAND DEBUG",
                            value = onOffLabel(state.isPixelMatterHandDebugEnabled),
                            section = SettingsSection.DEVELOPER,
                        ),
                    )
                }
            }
        }
    }

    /** 返回当前可见设置项对应的有序分类列表。 */
    fun sections(state: LauncherState): List<SettingsSection> {
        return rows(state).map { it.section }.distinct()
    }

    /** 返回 MORE 二级页面当前可见设置项对应的有序分类列表。 */
    fun moreSections(
        state: LauncherState,
        includeDeveloperTools: Boolean = BuildConfig.DEBUG,
    ): List<SettingsSection> {
        return moreRows(state, includeDeveloperTools).map { it.section }.distinct()
    }

    /** 根据当前索引返回实际可见的设置项，并将越界索引收敛到列表边界。 */
    fun selectedItem(state: LauncherState): SettingsMenuItem {
        val rows = rows(state)
        return rows[state.settingsSelectedIndex.coerceIn(0, rows.lastIndex)].item
    }

    fun nextStyle(current: PixelShape, direction: Int): PixelShape {
        val currentIndex = styleOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, styleOptions.size)
        return styleOptions[nextIndex]
    }

    fun nextResolution(current: Int, direction: Int): Int {
        val options = resolutionOptions()
        val nextIndex = wrapIndex(resolutionIndex(current) + direction, options.size)
        return options[nextIndex]
    }

    fun resolutionOptions(): List<Int> {
        return LauncherLayoutProfileFactory.resolutionOptions()
    }

    fun resolutionIndex(current: Int): Int {
        return resolutionOptions().indexOf(current).takeIf { it >= 0 } ?: 0
    }

    /** 按设置页方向循环选择主题家族。 */
    fun nextThemeFamily(
        current: LauncherThemeFamily,
        direction: Int,
    ): LauncherThemeFamily {
        val currentIndex = themeFamilyOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, themeFamilyOptions.size)
        return themeFamilyOptions[nextIndex]
    }

    /** 按设置页方向循环选择亮暗模式。 */
    fun nextThemeMode(current: LauncherThemeMode, direction: Int): LauncherThemeMode {
        val currentIndex = themeModeOptions.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, themeModeOptions.size)
        return themeModeOptions[nextIndex]
    }

    /** 按设置页方向循环选择字体家族，并收敛到新家族支持的最近组合。 */
    fun nextFontFamily(current: LauncherFontSelection, direction: Int): LauncherFontSelection {
        val currentIndex = fontOptions.indexOf(current.family).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, fontOptions.size)
        return PixelFontCatalog.normalize(current.copy(family = fontOptions[nextIndex]))
    }

    /** 只在当前字体家族真实提供的宽度模式之间循环。 */
    fun nextFontWidth(current: LauncherFontSelection, direction: Int): LauncherFontSelection {
        /** 当前字体家族公开的宽度模式。 */
        val options = PixelFontCatalog.widthModeOptions(current.family)
        val currentIndex = options.indexOf(current.widthMode).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, options.size)
        return PixelFontCatalog.normalize(current.copy(widthMode = options[nextIndex]))
    }

    /** 只在当前字体家族和宽度模式真实提供的字号之间循环。 */
    fun nextFontSize(current: LauncherFontSelection, direction: Int): LauncherFontSelection {
        /** 当前字体家族与宽度模式公开的字号。 */
        val options = PixelFontCatalog.fontSizeOptions(current.family, current.widthMode)
        val currentIndex = options.indexOf(current.size).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, options.size)
        return current.copy(size = options[nextIndex])
    }

    fun styleLabel(pixelShape: PixelShape): String {
        return when (pixelShape) {
            PixelShape.SQUARE -> "SQUARE"
            PixelShape.CIRCLE -> "CIRCLE"
            PixelShape.DIAMOND -> "DIAMOND"
        }
    }

    /** 返回设置页展示的主题家族名称。 */
    fun themeFamilyLabel(family: LauncherThemeFamily): String {
        return family.displayLabel
    }

    /** 返回设置页展示的亮暗模式名称。 */
    fun themeModeLabel(mode: LauncherThemeMode): String {
        return mode.displayLabel
    }

    /** 返回设置页展示的字体名称。 */
    fun fontLabel(fontFamily: LauncherFontFamily): String {
        return PixelFontCatalog.familyLabel(fontFamily)
    }

    /** 返回设置页展示的字体宽度模式。 */
    fun fontWidthLabel(widthMode: LauncherFontWidthMode): String {
        return PixelFontCatalog.widthModeLabel(widthMode)
    }

    /** 返回设置页展示的字体字号。 */
    fun fontSizeLabel(size: PixelFontSize): String {
        return PixelFontCatalog.sizeLabel(size)
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

    fun nextPixelMatterEffectMode(
        current: PixelMatterEffectMode,
        direction: Int,
    ): PixelMatterEffectMode {
        val currentIndex = PixelMatterEffectMode.entries.indexOf(current).takeIf { it >= 0 } ?: 0
        val nextIndex = wrapIndex(currentIndex + direction, PixelMatterEffectMode.entries.size)
        return PixelMatterEffectMode.entries[nextIndex]
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
            SettingsSection.THEME -> "THEME"
            SettingsSection.DRAWER -> "DRAWER"
            SettingsSection.IDLE -> "IDLE"
            SettingsSection.MORE -> "MORE"
            SettingsSection.NOTIFICATIONS -> "NOTIFICATIONS"
            SettingsSection.ACCESS -> "ACCESS"
            SettingsSection.EXPERIMENTAL -> "EXPERIMENTAL"
            SettingsSection.DEVELOPER -> "DEVELOPER"
        }
    }

    fun toggle(value: Boolean): Boolean = !value

    fun onOffLabel(value: Boolean): String {
        return if (value) "ON" else "OFF"
    }

    fun resolutionLabel(dotSizePx: Int): String {
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
