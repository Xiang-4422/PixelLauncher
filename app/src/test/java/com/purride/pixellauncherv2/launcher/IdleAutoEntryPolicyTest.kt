package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleAutoEntryPolicyTest {

    @Test
    fun canAutoEnter_onlyAllowsHomeAndDrawerWhenEnabledAndNoLaunchIsPending() {
        assertTrue(
            IdleAutoEntryPolicy.canAutoEnter(
                state = LauncherState(mode = LauncherMode.HOME, isIdlePageEnabled = true),
                launchPending = false,
            ),
        )
        assertTrue(
            IdleAutoEntryPolicy.canAutoEnter(
                state = LauncherState(mode = LauncherMode.APP_DRAWER, isIdlePageEnabled = true),
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.canAutoEnter(
                state = LauncherState(mode = LauncherMode.SETTINGS, isIdlePageEnabled = true),
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.canAutoEnter(
                state = LauncherState(mode = LauncherMode.HOME, isIdlePageEnabled = false),
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.canAutoEnter(
                state = LauncherState(mode = LauncherMode.HOME, isIdlePageEnabled = true),
                launchPending = true,
            ),
        )
    }

    @Test
    fun shouldEnterForCharging_requiresNewChargingStateAndChargeAutoIdle() {
        val state = LauncherState(
            mode = LauncherMode.HOME,
            isIdlePageEnabled = true,
            chargeAutoIdleEnabled = true,
        )

        assertTrue(
            IdleAutoEntryPolicy.shouldEnterForCharging(
                wasCharging = false,
                isCharging = true,
                state = state,
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.shouldEnterForCharging(
                wasCharging = true,
                isCharging = true,
                state = state,
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.shouldEnterForCharging(
                wasCharging = false,
                isCharging = true,
                state = state.copy(chargeAutoIdleEnabled = false),
                launchPending = false,
            ),
        )
    }

    @Test
    fun shouldEnterForCurrentChargingReflectsSettingsChangesImmediately() {
        val state = LauncherState(
            mode = LauncherMode.HOME,
            isIdlePageEnabled = true,
            chargeAutoIdleEnabled = true,
            isCharging = true,
        )

        assertTrue(IdleAutoEntryPolicy.shouldEnterForCurrentCharging(state, launchPending = false))
        assertFalse(
            IdleAutoEntryPolicy.shouldEnterForCurrentCharging(
                state.copy(chargeAutoIdleEnabled = false),
                launchPending = false,
            ),
        )
        assertFalse(
            IdleAutoEntryPolicy.shouldEnterForCurrentCharging(
                state.copy(mode = LauncherMode.SMS_THREADS),
                launchPending = false,
            ),
        )
    }

    @Test
    fun nextInactivityDelayMsCountsDownAndClampsWhenTimedOut() {
        val state = LauncherState(
            mode = LauncherMode.HOME,
            isIdlePageEnabled = true,
            inactivityAutoIdleEnabled = true,
            idleTimeoutSeconds = 30,
            lastInteractionUptimeMs = 1_000L,
        )

        assertEquals(30_000L, IdleAutoEntryPolicy.nextInactivityDelayMs(state, nowUptimeMs = 1_000L, launchPending = false))
        assertEquals(15_000L, IdleAutoEntryPolicy.nextInactivityDelayMs(state, nowUptimeMs = 16_000L, launchPending = false))
        assertEquals(0L, IdleAutoEntryPolicy.nextInactivityDelayMs(state, nowUptimeMs = 31_000L, launchPending = false))
    }

    @Test
    fun nextInactivityDelayMsReturnsNullWhenAutoIdleIsNotAllowed() {
        val state = LauncherState(
            mode = LauncherMode.HOME,
            isIdlePageEnabled = true,
            inactivityAutoIdleEnabled = true,
            idleTimeoutSeconds = 30,
            lastInteractionUptimeMs = 1_000L,
        )

        assertNull(
            IdleAutoEntryPolicy.nextInactivityDelayMs(
                state.copy(inactivityAutoIdleEnabled = false),
                nowUptimeMs = 31_000L,
                launchPending = false,
            ),
        )
        assertNull(
            IdleAutoEntryPolicy.nextInactivityDelayMs(
                state.copy(mode = LauncherMode.SETTINGS),
                nowUptimeMs = 31_000L,
                launchPending = false,
            ),
        )
        assertNull(
            IdleAutoEntryPolicy.nextInactivityDelayMs(
                state,
                nowUptimeMs = 31_000L,
                launchPending = true,
            ),
        )
    }
}
