package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.layout.LauncherLayoutProfileFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [SettingsMenuModel] — the deterministic settings value logic
 * (cyclic option stepping, binary switches, labels and the conditional row list).
 * All JVM-safe; no Android dependencies.
 */
class SettingsMenuModelTest {

    // ── Cyclic option stepping (wrapIndex) ────────────────────────────────────

    @Test
    fun nextStyle_cyclesThroughShapes() {
        assertEquals(PixelShape.CIRCLE, SettingsMenuModel.nextStyle(PixelShape.SQUARE, 1))
        assertEquals(PixelShape.SQUARE, SettingsMenuModel.nextStyle(PixelShape.DIAMOND, 1))
        assertEquals(PixelShape.DIAMOND, SettingsMenuModel.nextStyle(PixelShape.SQUARE, -1))
    }

    @Test
    fun nextDrawerListAlignment_cyclesLeftCenterRight() {
        assertEquals(DrawerListAlignment.CENTER, SettingsMenuModel.nextDrawerListAlignment(DrawerListAlignment.LEFT, 1))
        assertEquals(DrawerListAlignment.LEFT, SettingsMenuModel.nextDrawerListAlignment(DrawerListAlignment.RIGHT, 1))
    }

    @Test
    fun nextChargeIdleEffect_cyclesThroughEffects() {
        assertEquals(ChargeIdleEffect.HORIZON, SettingsMenuModel.nextChargeIdleEffect(ChargeIdleEffect.FLUID, 1))
        assertEquals(ChargeIdleEffect.CASCADE, SettingsMenuModel.nextChargeIdleEffect(ChargeIdleEffect.FLUID, -1))
        assertEquals(ChargeIdleEffect.FLUID, SettingsMenuModel.nextChargeIdleEffect(ChargeIdleEffect.CASCADE, 1))
    }

    @Test
    fun nextPixelMatterEffectMode_cyclesThroughMatterModes() {
        assertEquals(PixelMatterEffectMode.WATER, SettingsMenuModel.nextPixelMatterEffectMode(PixelMatterEffectMode.SAND, 1))
        assertEquals(PixelMatterEffectMode.SMOKE, SettingsMenuModel.nextPixelMatterEffectMode(PixelMatterEffectMode.SAND, -1))
        assertEquals(PixelMatterEffectMode.SAND, SettingsMenuModel.nextPixelMatterEffectMode(PixelMatterEffectMode.SMOKE, 1))
    }

    @Test
    fun nextIdleTimeoutSeconds_cyclesThroughDiscreteOptions() {
        assertEquals(60, SettingsMenuModel.nextIdleTimeoutSeconds(30, 1))
        assertEquals(120, SettingsMenuModel.nextIdleTimeoutSeconds(15, -1))
        assertEquals(60, SettingsMenuModel.nextIdleTimeoutSeconds(25, 1))
    }

    @Test
    fun nextThemeMode_wrapsAtBothEndsAndRoundTrips() {
        val first = LauncherThemeMode.entries.first()
        val last = LauncherThemeMode.entries.last()
        assertEquals(last, SettingsMenuModel.nextThemeMode(first, -1))
        assertEquals(first, SettingsMenuModel.nextThemeMode(last, 1))
        // forward then backward returns to the original
        val stepped = SettingsMenuModel.nextThemeMode(first, 1)
        assertEquals(first, SettingsMenuModel.nextThemeMode(stepped, -1))
    }

    /** 主题家族与亮暗模式必须作为两个独立选项循环。 */
    @Test
    fun nextThemeFamilyWrapsIndependentlyFromMode() {
        assertEquals(
            LauncherThemeFamily.CRT,
            SettingsMenuModel.nextThemeFamily(LauncherThemeFamily.MIDNIGHT, 1),
        )
        assertEquals(
            LauncherThemeFamily.PAPER,
            SettingsMenuModel.nextThemeFamily(LauncherThemeFamily.MIDNIGHT, -1),
        )
    }

    /** 字体家族切换后应保留受支持维度，并把不支持字号收敛到最近选项。 */
    @Test
    fun nextFontFamily_wrapsAndNormalizesUnsupportedSize() {
        val fusion8 = LauncherFontSelection(
            family = LauncherFontFamily.FUSION,
            widthMode = LauncherFontWidthMode.PROPORTIONAL,
            size = PixelFontSize.PX_8,
        )
        assertEquals(
            LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.PROPORTIONAL,
                size = PixelFontSize.PX_10,
            ),
            SettingsMenuModel.nextFontFamily(fusion8, 1),
        )
        assertEquals(
            LauncherFontSelection(
                family = LauncherFontFamily.CUBIC_11,
                widthMode = LauncherFontWidthMode.PROPORTIONAL,
                size = PixelFontSize.PX_12,
            ),
            SettingsMenuModel.nextFontFamily(
                fusion8.copy(family = LauncherFontFamily.ARK, size = PixelFontSize.PX_10),
                1,
            ),
        )
        assertEquals(
            LauncherFontSelection(
                family = LauncherFontFamily.FUSION,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_12,
            ),
            SettingsMenuModel.nextFontFamily(
                LauncherFontSelection(
                    family = LauncherFontFamily.PIX32,
                    widthMode = LauncherFontWidthMode.MONOSPACED,
                    size = PixelFontSize.PX_12,
                ),
                1,
            ),
        )
    }

    /** 宽度模式与字号只在当前字体实际资源矩阵内循环。 */
    @Test
    fun nextFontWidthAndSize_useCurrentFamilyCapabilityMatrix() {
        val ark10 = LauncherFontSelection(
            family = LauncherFontFamily.ARK,
            widthMode = LauncherFontWidthMode.PROPORTIONAL,
            size = PixelFontSize.PX_10,
        )
        assertEquals(
            ark10.copy(widthMode = LauncherFontWidthMode.MONOSPACED),
            SettingsMenuModel.nextFontWidth(ark10, 1),
        )
        assertEquals(ark10.copy(size = PixelFontSize.PX_12), SettingsMenuModel.nextFontSize(ark10, 1))
        assertEquals(
            ark10,
            SettingsMenuModel.nextFontSize(ark10.copy(size = PixelFontSize.PX_16), 1),
        )
    }

    @Test
    fun nextResolution_keepsOriginalDiscreteOptionsAndWraps() {
        val options = LauncherLayoutProfileFactory.resolutionOptions()
        assertEquals(options, SettingsMenuModel.resolutionOptions())
        assertEquals(0, SettingsMenuModel.resolutionIndex(options.first()))
        assertEquals(options.lastIndex, SettingsMenuModel.resolutionIndex(options.last()))
        assertEquals(options[1], SettingsMenuModel.nextResolution(options.first(), 1))
        assertEquals(options.first(), SettingsMenuModel.nextResolution(options.last(), 1))
        assertEquals(options.last(), SettingsMenuModel.nextResolution(options.first(), -1))
    }

    // ── Labels + toggles ──────────────────────────────────────────────────────

    @Test
    fun labelsAndToggleAreStable() {
        assertEquals("ON", SettingsMenuModel.onOffLabel(true))
        assertEquals("OFF", SettingsMenuModel.onOffLabel(false))
        assertFalse(SettingsMenuModel.toggle(true))
        assertEquals("SQUARE", SettingsMenuModel.styleLabel(PixelShape.SQUARE))
        assertEquals("CENTER", SettingsMenuModel.drawerListAlignmentLabel(DrawerListAlignment.CENTER))
        assertEquals("DOT MATRIX", SettingsMenuModel.chargeIdleEffectLabel(ChargeIdleEffect.DOT_MATRIX))
        assertEquals("30S", SettingsMenuModel.idleTimeoutLabel(30))
        assertEquals("ARK", SettingsMenuModel.fontLabel(LauncherFontFamily.ARK))
        assertEquals("MONO", SettingsMenuModel.fontWidthLabel(LauncherFontWidthMode.MONOSPACED))
        assertEquals("16PX", SettingsMenuModel.fontSizeLabel(PixelFontSize.PX_16))
        assertEquals(
            15,
            PixelFontCatalog.estimatedTextWidth(
                "ABC",
                selection = LauncherFontSelection(
                    family = LauncherFontFamily.FUSION,
                    widthMode = LauncherFontWidthMode.MONOSPACED,
                    size = PixelFontSize.PX_10,
                ),
            ),
        )
    }

    @Test
    fun displayValue_wrapsNonBlankInAngleBrackets() {
        assertEquals("<10PX>", SettingsMenuModel.displayValue(SettingsMenuRow(SettingsMenuItem.RESOLUTION, "PIXEL SIZE", "10PX")))
        assertEquals("", SettingsMenuModel.displayValue(SettingsMenuRow(SettingsMenuItem.RESOLUTION, "PIXEL SIZE", "")))
    }

    // ── rows() conditional content + selectedItem clamping ────────────────────

    @Test
    fun rows_includeStyleRowOnlyWhenPixelGapEnabled() {
        val enabledRows = SettingsMenuModel.rows(LauncherState(isPixelGapEnabled = true))
        val disabledRows = SettingsMenuModel.rows(LauncherState(isPixelGapEnabled = false))
        val withGap = enabledRows.map { it.item }
        val withoutGap = disabledRows.map { it.item }
        assertTrue(withGap.contains(SettingsMenuItem.STYLE))
        assertFalse(withoutGap.contains(SettingsMenuItem.STYLE))
        assertEquals("ON", enabledRows.first { it.item == SettingsMenuItem.PIXEL_GAP }.value)
        assertEquals("OFF", disabledRows.first { it.item == SettingsMenuItem.PIXEL_GAP }.value)
    }

    @Test
    fun rows_includeHomeIdleDataHealthAndAdvancedActions() {
        val rows = SettingsMenuModel.rows(
            LauncherState(
                screenUsageTimeText = "00:20",
                screenOpenCountText = "2",
                isIdlePageEnabled = true,
                chargeAutoIdleEnabled = true,
                inactivityAutoIdleEnabled = true,
                idleTimeoutSeconds = 60,
                chargeIdleEffect = ChargeIdleEffect.TANK,
                hasUsageAccess = true,
                hasLocationPermission = true,
                hasCallLogPermission = true,
                hasSmsReadPermission = true,
                isDefaultSmsApp = true,
                smsPermissionState = SmsPermissionState.READY,
                hasPostNotificationPermission = true,
                hasNotificationListenerAccess = true,
                mutedNotificationSourceIds = setOf("com.noisy"),
                priorityNotificationSourceIds = setOf("com.bank"),
                apps = listOf(AppEntry(label = "Bank", packageName = "com.bank", activityName = "BankActivity")),
                selectedThemeFamily = LauncherThemeFamily.CRT,
                selectedThemeMode = LauncherThemeMode.NIGHT,
            ),
        )
        val items = rows.map { it.item }

        assertTrue(items.contains(SettingsMenuItem.HOME_STATUS))
        assertTrue(items.contains(SettingsMenuItem.IDLE_PAGE))
        assertTrue(items.contains(SettingsMenuItem.CHARGE_AUTO_IDLE))
        assertTrue(items.contains(SettingsMenuItem.INACTIVITY_AUTO_IDLE))
        assertTrue(items.contains(SettingsMenuItem.IDLE_TIMEOUT))
        assertTrue(items.contains(SettingsMenuItem.CHARGE_IDLE_EFFECT))
        assertTrue(items.contains(SettingsMenuItem.APP_MANAGEMENT))
        assertTrue(items.contains(SettingsMenuItem.NOTIFICATIONS))
        assertTrue(items.contains(SettingsMenuItem.DATA_HEALTH))
        assertTrue(items.contains(SettingsMenuItem.LOADING_PREVIEW))
        assertTrue(items.contains(SettingsMenuItem.PIXEL_MATTER_EFFECT))
        assertTrue(items.contains(SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE))
        assertTrue(items.contains(SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL))
        assertTrue(items.contains(SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG))
        assertTrue(items.contains(SettingsMenuItem.ADVANCED))
        assertTrue(items.contains(SettingsMenuItem.FONT))
        assertTrue(items.contains(SettingsMenuItem.FONT_WIDTH))
        assertTrue(items.contains(SettingsMenuItem.FONT_SIZE))
        assertEquals("1 ROW", rows.first { it.item == SettingsMenuItem.HOME_STATUS }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.IDLE_PAGE }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.CHARGE_AUTO_IDLE }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.INACTIVITY_AUTO_IDLE }.value)
        assertEquals("60S", rows.first { it.item == SettingsMenuItem.IDLE_TIMEOUT }.value)
        assertEquals("TANK", rows.first { it.item == SettingsMenuItem.CHARGE_IDLE_EFFECT }.value)
        assertEquals("OPEN", rows.first { it.item == SettingsMenuItem.APP_MANAGEMENT }.value)
        assertEquals("M1 P1", rows.first { it.item == SettingsMenuItem.NOTIFICATIONS }.value)
        assertEquals("OK", rows.first { it.item == SettingsMenuItem.DATA_HEALTH }.value)
        assertEquals("OPEN", rows.first { it.item == SettingsMenuItem.LOADING_PREVIEW }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT }.value)
        assertEquals("SAND", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE }.value)
        assertEquals("OFF", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG }.value)
        assertEquals("OPEN", rows.first { it.item == SettingsMenuItem.ADVANCED }.value)
        assertEquals("FUSION", rows.first { it.item == SettingsMenuItem.FONT }.value)
        assertEquals("PROP", rows.first { it.item == SettingsMenuItem.FONT_WIDTH }.value)
        assertEquals("10PX", rows.first { it.item == SettingsMenuItem.FONT_SIZE }.value)
        assertEquals("CRT", rows.first { it.item == SettingsMenuItem.THEME }.value)
        assertEquals("NIGHT", rows.first { it.item == SettingsMenuItem.THEME_MODE }.value)
    }

    @Test
    fun rows_areGroupedBySettingsProductAreas() {
        val state = LauncherState(
            isPixelGapEnabled = true,
            apps = listOf(AppEntry(label = "Bank", packageName = "com.bank", activityName = "BankActivity")),
        )
        val rows = SettingsMenuModel.rows(state)

        assertEquals(
            listOf(
                SettingsSection.DISPLAY,
                SettingsSection.THEME,
                SettingsSection.HOME,
                SettingsSection.DRAWER,
                SettingsSection.IDLE,
                SettingsSection.DATA,
                SettingsSection.ADVANCED,
            ),
            SettingsMenuModel.sections(state),
        )
        assertEquals(SettingsSection.DISPLAY, rows.first { it.item == SettingsMenuItem.RESOLUTION }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.THEME }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.THEME_MODE }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT_WIDTH }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT_SIZE }.section)
        assertEquals(SettingsSection.HOME, rows.first { it.item == SettingsMenuItem.HOME_STATUS }.section)
        assertEquals(SettingsSection.DRAWER, rows.first { it.item == SettingsMenuItem.APP_LIST_ALIGNMENT }.section)
        assertEquals(SettingsSection.DRAWER, rows.first { it.item == SettingsMenuItem.APP_MANAGEMENT }.section)
        assertEquals(SettingsSection.IDLE, rows.first { it.item == SettingsMenuItem.IDLE_TIMEOUT }.section)
        assertEquals(SettingsSection.DATA, rows.first { it.item == SettingsMenuItem.NOTIFICATIONS }.section)
        assertEquals(SettingsSection.DATA, rows.first { it.item == SettingsMenuItem.DATA_HEALTH }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.LOADING_PREVIEW }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG }.section)
        assertEquals(SettingsSection.ADVANCED, rows.first { it.item == SettingsMenuItem.ADVANCED }.section)
        assertEquals("DISPLAY", SettingsMenuModel.sectionLabel(SettingsSection.DISPLAY))
        assertEquals("THEME", SettingsMenuModel.sectionLabel(SettingsSection.THEME))
        assertEquals("HOME", SettingsMenuModel.sectionLabel(SettingsSection.HOME))
    }

    @Test
    fun rows_marksAppManagementEmptyWhenNoAppsAreLoaded() {
        val rows = SettingsMenuModel.rows(LauncherState(apps = emptyList()))

        assertEquals("EMPTY", rows.first { it.item == SettingsMenuItem.APP_MANAGEMENT }.value)
    }

    @Test
    fun selectedItem_clampsOutOfRangeIndexToLastRow() {
        val state = LauncherState(settingsSelectedIndex = 999)
        val expected = SettingsMenuModel.rows(state).last().item
        assertEquals(expected, SettingsMenuModel.selectedItem(state))
    }

    @Test
    fun selectedItem_firstRowIsResolution() {
        assertEquals(SettingsMenuItem.RESOLUTION, SettingsMenuModel.selectedItem(LauncherState(settingsSelectedIndex = 0)))
    }
}
