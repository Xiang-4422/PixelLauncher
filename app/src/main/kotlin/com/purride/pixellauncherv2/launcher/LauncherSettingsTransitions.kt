package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape

/**
 * Settings 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载设置列表焦点/窗口、外观与字体选择、UI 行为偏好的写入。对外入口仍是
 * [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherSettingsTransitions {

    /** 选中设置页中的某一行，并按当前可视行数重排设置窗口。 */
    fun selectSettingsIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState {
        val maxIndex = (SettingsMenuModel.rows(state).size - 1).coerceAtLeast(0)
        return syncSettingsWindow(
            state = state.copy(settingsSelectedIndex = index.coerceIn(0, maxIndex)),
            visibleRows = visibleRows,
        )
    }

    /** 按相对行数移动设置页内部焦点。 */
    fun moveSettingsSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        return selectSettingsIndex(
            state = state,
            index = state.settingsSelectedIndex + delta,
            visibleRows = visibleRows,
        )
    }

    /**
     * 滚动设置页可视窗口，并尽量让当前焦点保持在相同的相对位置。
     */
    fun scrollSettingsWindow(state: LauncherState, delta: Int, visibleRows: Int): LauncherState {
        val rows = SettingsMenuModel.rows(state)
        if (rows.isEmpty() || delta == 0) {
            return reflowSettingsWindow(state, visibleRows)
        }

        val safeVisibleRows = visibleRows.coerceAtLeast(1)
        val maxStartIndex = (rows.size - safeVisibleRows).coerceAtLeast(0)
        val safeListStartIndex = state.settingsListStartIndex.coerceIn(0, maxStartIndex)
        val nextListStartIndex = (safeListStartIndex + delta).coerceIn(0, maxStartIndex)
        val relativeFocusIndex = (state.settingsSelectedIndex - safeListStartIndex)
            .coerceIn(0, safeVisibleRows - 1)
        val maxVisibleIndex = (nextListStartIndex + safeVisibleRows - 1).coerceAtMost(rows.lastIndex)
        val nextSelectedIndex = (nextListStartIndex + relativeFocusIndex)
            .coerceIn(nextListStartIndex, maxVisibleIndex)

        return state.copy(
            settingsSelectedIndex = nextSelectedIndex,
            settingsListStartIndex = nextListStartIndex,
        )
    }

    /** 在 viewport 或内容变化后，重新校正设置页的焦点和窗口。 */
    fun reflowSettingsWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val maxIndex = (SettingsMenuModel.rows(state).size - 1).coerceAtLeast(0)
        return syncSettingsWindow(
            state = state.copy(settingsSelectedIndex = state.settingsSelectedIndex.coerceIn(0, maxIndex)),
            visibleRows = visibleRows,
        )
    }

    /** 把当前外观选择写回状态。 */
    fun updateAppearance(
        state: LauncherState,
        selectedPixelShape: PixelShape = state.selectedPixelShape,
        selectedDotSizePx: Int = state.selectedDotSizePx,
        isPixelGapEnabled: Boolean = state.isPixelGapEnabled,
        selectedThemeFamily: LauncherThemeFamily = state.selectedThemeFamily,
        selectedThemeMode: LauncherThemeMode = state.selectedThemeMode,
        fontSelection: LauncherFontSelection = state.fontSelection,
    ): LauncherState {
        return state.copy(
            selectedPixelShape = selectedPixelShape,
            selectedDotSizePx = selectedDotSizePx,
            isPixelGapEnabled = isPixelGapEnabled,
            selectedThemeFamily = selectedThemeFamily,
            selectedThemeMode = selectedThemeMode,
            fontSelection = PixelFontCatalog.normalize(fontSelection),
        )
    }

    /** 只更新字体后台准备状态，不提前改变当前激活字体。 */
    fun updateFontLoading(state: LauncherState, isLoading: Boolean): LauncherState =
        state.copy(isFontLoading = isLoading)

    /** 更新字体资源缓存诊断文本，不改变字体选择。 */
    fun updateFontCacheSummary(state: LauncherState, summary: String): LauncherState =
        state.copy(fontCacheSummary = summary.trim().ifBlank { "0/0K" })

    /** 把抽屉对齐、Idle 开关等非视觉行为偏好写回状态。 */
    fun updateUiBehavior(
        state: LauncherState,
        drawerListAlignment: DrawerListAlignment = state.drawerListAlignment,
        isIdlePageEnabled: Boolean = state.isIdlePageEnabled,
        chargeAutoIdleEnabled: Boolean = state.chargeAutoIdleEnabled,
        inactivityAutoIdleEnabled: Boolean = state.inactivityAutoIdleEnabled,
        idleTimeoutSeconds: Int = state.idleTimeoutSeconds,
        openDrawerInSearchMode: Boolean = state.openDrawerInSearchMode,
        chargeIdleEffect: ChargeIdleEffect = state.chargeIdleEffect,
        isPixelMatterEffectEnabled: Boolean = state.isPixelMatterEffectEnabled,
        pixelMatterEffectMode: PixelMatterEffectMode = state.pixelMatterEffectMode,
        isPixelMatterHandControlEnabled: Boolean = state.isPixelMatterHandControlEnabled,
        isPixelMatterHandDebugEnabled: Boolean = state.isPixelMatterHandDebugEnabled,
    ): LauncherState {
        return state.copy(
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            chargeAutoIdleEnabled = chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = inactivityAutoIdleEnabled,
            idleTimeoutSeconds = IdleSettings.normalizeTimeoutSeconds(idleTimeoutSeconds),
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
            isPixelMatterEffectEnabled = isPixelMatterEffectEnabled,
            pixelMatterEffectMode = pixelMatterEffectMode,
            isPixelMatterHandControlEnabled = isPixelMatterHandControlEnabled,
            isPixelMatterHandDebugEnabled = isPixelMatterHandDebugEnabled,
        )
    }

    /**
     * 保持设置页焦点与窗口的一致性。
     *
     * internal：Shell 流（showSettings）打开设置页时也需要同一窗口不变量。
     */
    internal fun syncSettingsWindow(state: LauncherState, visibleRows: Int): LauncherState {
        val rows = SettingsMenuModel.rows(state)
        if (rows.isEmpty()) {
            return state.copy(
                settingsSelectedIndex = 0,
                settingsListStartIndex = 0,
            )
        }

        val safeVisibleRows = visibleRows.coerceAtLeast(1)
        val safeSelectedIndex = state.settingsSelectedIndex.coerceIn(0, rows.lastIndex)
        val maxStartIndex = (rows.size - safeVisibleRows).coerceAtLeast(0)
        val safeListStartIndex = state.settingsListStartIndex.coerceIn(0, maxStartIndex)
        val nextListStartIndex = when {
            safeSelectedIndex < safeListStartIndex -> safeSelectedIndex
            safeSelectedIndex >= safeListStartIndex + safeVisibleRows -> {
                (safeSelectedIndex - safeVisibleRows + 1).coerceIn(0, maxStartIndex)
            }
            else -> safeListStartIndex
        }

        return state.copy(
            settingsSelectedIndex = safeSelectedIndex,
            settingsListStartIndex = nextListStartIndex,
        )
    }
}
