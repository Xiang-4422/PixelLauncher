package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfileFactory
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
    fun nextTheme_wrapsAtBothEndsAndRoundTrips() {
        val first = PixelTheme.entries.first()
        val last = PixelTheme.entries.last()
        assertEquals(last, SettingsMenuModel.nextTheme(first, -1))
        assertEquals(first, SettingsMenuModel.nextTheme(last, 1))
        // forward then backward returns to the original
        val stepped = SettingsMenuModel.nextTheme(first, 1)
        assertEquals(first, SettingsMenuModel.nextTheme(stepped, -1))
    }

    @Test
    fun nextResolution_keepsOriginalDiscreteOptionsAndWraps() {
        val options = ScreenProfileFactory.resolutionOptions(currentProfile = null)
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
                SettingsSection.HOME,
                SettingsSection.DRAWER,
                SettingsSection.IDLE,
                SettingsSection.DATA,
                SettingsSection.ADVANCED,
            ),
            SettingsMenuModel.sections(state),
        )
        assertEquals(SettingsSection.DISPLAY, rows.first { it.item == SettingsMenuItem.RESOLUTION }.section)
        assertEquals(SettingsSection.DISPLAY, rows.first { it.item == SettingsMenuItem.THEME }.section)
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
