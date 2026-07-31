package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.BuildConfig
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
    fun rows_keepOnlyPrimarySettingsAndMoreEntry() {
        val rows = SettingsMenuModel.rows(
            LauncherState(
                isIdlePageEnabled = true,
                chargeAutoIdleEnabled = true,
                inactivityAutoIdleEnabled = true,
                idleTimeoutSeconds = 60,
                chargeIdleEffect = ChargeIdleEffect.TANK,
                selectedThemeFamily = LauncherThemeFamily.CRT,
                selectedThemeMode = LauncherThemeMode.NIGHT,
            ),
        )
        val items = rows.map { it.item }

        assertTrue(items.contains(SettingsMenuItem.IDLE_PAGE))
        assertTrue(items.contains(SettingsMenuItem.CHARGE_AUTO_IDLE))
        assertTrue(items.contains(SettingsMenuItem.INACTIVITY_AUTO_IDLE))
        assertTrue(items.contains(SettingsMenuItem.IDLE_TIMEOUT))
        assertTrue(items.contains(SettingsMenuItem.CHARGE_IDLE_EFFECT))
        assertTrue(items.contains(SettingsMenuItem.MORE))
        assertTrue(items.contains(SettingsMenuItem.FONT))
        assertTrue(items.contains(SettingsMenuItem.FONT_WIDTH))
        assertTrue(items.contains(SettingsMenuItem.FONT_SIZE))
        assertFalse(items.contains(SettingsMenuItem.NOTIFICATIONS))
        assertFalse(items.contains(SettingsMenuItem.DATA_HEALTH))
        assertFalse(items.contains(SettingsMenuItem.PIXEL_MATTER_EFFECT))
        assertFalse(items.contains(SettingsMenuItem.LOADING_PREVIEW))
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.IDLE_PAGE }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.CHARGE_AUTO_IDLE }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.INACTIVITY_AUTO_IDLE }.value)
        assertEquals("60S", rows.first { it.item == SettingsMenuItem.IDLE_TIMEOUT }.value)
        assertEquals("TANK", rows.first { it.item == SettingsMenuItem.CHARGE_IDLE_EFFECT }.value)
        assertEquals("OPEN", rows.first { it.item == SettingsMenuItem.MORE }.value)
        assertEquals("FUSION", rows.first { it.item == SettingsMenuItem.FONT }.value)
        assertEquals("PROP", rows.first { it.item == SettingsMenuItem.FONT_WIDTH }.value)
        assertEquals("10PX", rows.first { it.item == SettingsMenuItem.FONT_SIZE }.value)
        assertEquals("CRT", rows.first { it.item == SettingsMenuItem.THEME }.value)
        assertEquals("NIGHT", rows.first { it.item == SettingsMenuItem.THEME_MODE }.value)
    }

    @Test
    fun moreRows_includeLowFrequencyAndDebugOnlySettings() {
        val rows = SettingsMenuModel.moreRows(
            LauncherState(
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
                isPixelMatterHandControlEnabled = true,
            ),
        )
        val items = rows.map { it.item }

        assertEquals("M1 P1", rows.first { it.item == SettingsMenuItem.NOTIFICATIONS }.value)
        assertEquals("OK", rows.first { it.item == SettingsMenuItem.DATA_HEALTH }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT }.value)
        assertEquals("SAND", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE }.value)
        assertEquals("ON", rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_HAND_CONTROL }.value)
        assertEquals(BuildConfig.DEBUG, items.contains(SettingsMenuItem.LOADING_PREVIEW))
        assertEquals(BuildConfig.DEBUG, items.contains(SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG))
        assertEquals(BuildConfig.DEBUG, items.contains(SettingsMenuItem.ADVANCED))
    }

    @Test
    fun rows_areGroupedBySettingsProductAreas() {
        val state = LauncherState(
            isPixelGapEnabled = true,
            apps = listOf(AppEntry(label = "Bank", packageName = "com.bank", activityName = "BankActivity")),
        )
        val rows = SettingsMenuModel.rows(state)

        val expectedSections = buildList {
            add(SettingsSection.DISPLAY)
            add(SettingsSection.THEME)
            add(SettingsSection.DRAWER)
            add(SettingsSection.IDLE)
            add(SettingsSection.MORE)
        }
        assertEquals(expectedSections, SettingsMenuModel.sections(state))
        assertEquals(SettingsSection.DISPLAY, rows.first { it.item == SettingsMenuItem.RESOLUTION }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.THEME }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.THEME_MODE }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT_WIDTH }.section)
        assertEquals(SettingsSection.THEME, rows.first { it.item == SettingsMenuItem.FONT_SIZE }.section)
        assertEquals(SettingsSection.DRAWER, rows.first { it.item == SettingsMenuItem.APP_LIST_ALIGNMENT }.section)
        assertEquals(SettingsSection.IDLE, rows.first { it.item == SettingsMenuItem.IDLE_PAGE }.section)
        assertEquals(SettingsSection.MORE, rows.first { it.item == SettingsMenuItem.MORE }.section)
        assertEquals("DISPLAY", SettingsMenuModel.sectionLabel(SettingsSection.DISPLAY))
        assertEquals("THEME", SettingsMenuModel.sectionLabel(SettingsSection.THEME))
        assertEquals("MORE", SettingsMenuModel.sectionLabel(SettingsSection.MORE))
    }

    @Test
    fun moreRows_areGroupedByLowFrequencyProductAreas() {
        val state = LauncherState()
        val rows = SettingsMenuModel.moreRows(state)
        val expectedSections = buildList {
            add(SettingsSection.NOTIFICATIONS)
            add(SettingsSection.ACCESS)
            add(SettingsSection.EXPERIMENTAL)
            if (BuildConfig.DEBUG) add(SettingsSection.DEVELOPER)
        }

        assertEquals(expectedSections, SettingsMenuModel.moreSections(state))
        assertEquals(SettingsSection.NOTIFICATIONS, rows.first { it.item == SettingsMenuItem.NOTIFICATIONS }.section)
        assertEquals(SettingsSection.ACCESS, rows.first { it.item == SettingsMenuItem.DATA_HEALTH }.section)
        assertEquals(SettingsSection.EXPERIMENTAL, rows.first { it.item == SettingsMenuItem.PIXEL_MATTER_EFFECT }.section)
        if (BuildConfig.DEBUG) {
            assertEquals(SettingsSection.DEVELOPER, rows.first { it.item == SettingsMenuItem.LOADING_PREVIEW }.section)
        }
        assertEquals("NOTIFICATIONS", SettingsMenuModel.sectionLabel(SettingsSection.NOTIFICATIONS))
        assertEquals("ACCESS", SettingsMenuModel.sectionLabel(SettingsSection.ACCESS))
        assertEquals("EXPERIMENTAL", SettingsMenuModel.sectionLabel(SettingsSection.EXPERIMENTAL))
        assertEquals("DEVELOPER", SettingsMenuModel.sectionLabel(SettingsSection.DEVELOPER))
    }

    @Test
    fun rows_hideDependentSettingsWhenParentFeaturesAreDisabled() {
        val state = LauncherState(
            isIdlePageEnabled = false,
            inactivityAutoIdleEnabled = false,
            isPixelMatterEffectEnabled = false,
            isPixelMatterHandControlEnabled = false,
        )
        val rows = SettingsMenuModel.rows(state)
        val moreRows = SettingsMenuModel.moreRows(state)
        val items = rows.map { it.item }
        val moreItems = moreRows.map { it.item }

        assertFalse(items.contains(SettingsMenuItem.CHARGE_AUTO_IDLE))
        assertFalse(items.contains(SettingsMenuItem.INACTIVITY_AUTO_IDLE))
        assertFalse(items.contains(SettingsMenuItem.IDLE_TIMEOUT))
        assertFalse(items.contains(SettingsMenuItem.CHARGE_IDLE_EFFECT))
        assertFalse(moreItems.contains(SettingsMenuItem.PIXEL_MATTER_EFFECT_MODE))
        assertFalse(moreItems.contains(SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG))
    }

    @Test
    fun moreRows_hideDeveloperToolsWhenDeveloperVisibilityIsDisabled() {
        val rows = SettingsMenuModel.moreRows(
            state = LauncherState(isPixelMatterHandControlEnabled = true),
            includeDeveloperTools = false,
        )
        val items = rows.map { it.item }

        assertFalse(items.contains(SettingsMenuItem.LOADING_PREVIEW))
        assertFalse(items.contains(SettingsMenuItem.ADVANCED))
        assertFalse(items.contains(SettingsMenuItem.PIXEL_MATTER_HAND_DEBUG))
        assertFalse(
            SettingsMenuModel.moreSections(
                LauncherState(),
                includeDeveloperTools = false,
            ).contains(SettingsSection.DEVELOPER),
        )
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
