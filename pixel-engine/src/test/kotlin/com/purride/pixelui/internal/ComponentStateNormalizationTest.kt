package com.purride.pixelui.internal

import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies terminal capability states cannot retain impossible pointer or focus ownership. */
class ComponentStateNormalizationTest {
    /** Disabled removes every transient state while preserving orthogonal semantic states. */
    @Test
    fun disabledRemovesTransientStatesAndPreservesOrthogonalStates() {
        /** Caller states intentionally containing every transient ownership flag. */
        val persistent = PixelControlStateSet.of(
            PixelControlState.Selected,
            PixelControlState.Error,
            PixelControlState.Loading,
            PixelControlState.Focused,
            PixelControlState.Pressed,
            PixelControlState.Hovered,
        )

        /** Canonical state set returned when runtime capability is disabled. */
        val actual = mergeControlStates(
            persistent = persistent,
            disabled = true,
            pressed = true,
            hovered = true,
            focused = true,
        )

        assertTrue(PixelControlState.Disabled in actual)
        assertTrue(PixelControlState.Selected in actual)
        assertTrue(PixelControlState.Error in actual)
        assertTrue(PixelControlState.Loading in actual)
        assertFalse(PixelControlState.Focused in actual)
        assertFalse(PixelControlState.Pressed in actual)
        assertFalse(PixelControlState.Hovered in actual)
    }

    /** A caller-authored Disabled state is terminal even when the runtime flag was not normalized. */
    @Test
    fun persistentDisabledAlsoClearsTransientStates() {
        /** Deliberately contradictory caller state used to test canonicalization at the boundary. */
        val persistent = PixelControlStateSet.of(
            PixelControlState.Disabled,
            PixelControlState.Focused,
        )

        /** Canonical state set returned without a separate runtime Disabled flag. */
        val actual = mergeControlStates(
            persistent = persistent,
            disabled = false,
            pressed = false,
            hovered = false,
            focused = true,
        )

        assertTrue(PixelControlState.Disabled in actual)
        assertFalse(PixelControlState.Focused in actual)
    }
}
