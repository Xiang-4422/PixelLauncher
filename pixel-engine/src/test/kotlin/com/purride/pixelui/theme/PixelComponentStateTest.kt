package com.purride.pixelui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks immutable state-set semantics and the public component-state resolution contract. */
class PixelComponentStateTest {
    /** Empty is Normal, Normal never coexists with another state, and value hashes are stable. */
    @Test
    fun emptyRepresentsNormalAndCombinedSetsHaveValueEquality() {
        /** Two independently created sets with the same non-normal bits. */
        val first = PixelControlStateSet.of(
            PixelControlState.Normal,
            PixelControlState.Hovered,
            PixelControlState.Selected,
        )
        val second = PixelControlStateSet.of(
            PixelControlState.Selected,
            PixelControlState.Hovered,
        )

        assertEquals(PixelControlStateSet.Empty, PixelControlStateSet.of(PixelControlState.Normal))
        assertEquals(setOf(PixelControlState.Normal), PixelControlStateSet.Empty.toSet())
        assertTrue(PixelControlState.Normal in PixelControlStateSet.Empty)
        assertFalse(PixelControlState.Normal in first)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(
            setOf(PixelControlState.Hovered, PixelControlState.Selected),
            first.toSet(),
        )
    }

    /** State maps use the single documented priority from Disabled down to Normal. */
    @Test
    fun stateMapResolvesEveryPriorityLevelDeterministically() {
        /** Property containing an observable value for every supported state. */
        val property = PixelStateMap(
            normal = "normal",
            PixelControlState.Selected to "selected",
            PixelControlState.Hovered to "hovered",
            PixelControlState.Focused to "focused",
            PixelControlState.Pressed to "pressed",
            PixelControlState.Error to "error",
            PixelControlState.Loading to "loading",
            PixelControlState.Disabled to "disabled",
        )
        /** Initial set containing every competing non-normal state. */
        var states = PixelControlStateSet.of(
            PixelControlState.Selected,
            PixelControlState.Hovered,
            PixelControlState.Focused,
            PixelControlState.Pressed,
            PixelControlState.Error,
            PixelControlState.Loading,
            PixelControlState.Disabled,
        )

        assertEquals("disabled", property.resolve(states))
        states -= PixelControlState.Disabled
        assertEquals("loading", property.resolve(states))
        states -= PixelControlState.Loading
        assertEquals("error", property.resolve(states))
        states -= PixelControlState.Error
        assertEquals("pressed", property.resolve(states))
        states -= PixelControlState.Pressed
        assertEquals("focused", property.resolve(states))
        states -= PixelControlState.Focused
        assertEquals("hovered", property.resolve(states))
        states -= PixelControlState.Hovered
        assertEquals("selected", property.resolve(states))
        states -= PixelControlState.Selected
        assertEquals("normal", property.resolve(states))
    }

    /** Missing high-priority overrides fall through and nullable override values remain observable. */
    @Test
    fun stateMapFallsThroughMissingOverridesAndPreservesExplicitNull() {
        /** Sparse map with a nullable Disabled override. */
        val property = PixelStateMap<String?>(
            normal = "normal",
            PixelControlState.Error to "error",
            PixelControlState.Disabled to null,
        )

        assertEquals(
            "error",
            property.resolve(
                PixelControlStateSet.of(PixelControlState.Loading, PixelControlState.Error),
            ),
        )
        assertNull(
            property.resolve(
                PixelControlStateSet.of(PixelControlState.Disabled, PixelControlState.Error),
            ),
        )
    }

    /** Normal overrides and duplicate state entries fail before an ambiguous map is created. */
    @Test
    fun stateMapRejectsNormalAndDuplicateOverrides() {
        assertIllegalArgument {
            PixelStateMap(normal = "normal", PixelControlState.Normal to "invalid")
        }
        assertIllegalArgument {
            PixelStateMap(
                normal = "normal",
                PixelControlState.Hovered to "first",
                PixelControlState.Hovered to "second",
            )
        }
    }

    /** Focus remains an additive indicator while Error wins the base color priority chain. */
    @Test
    fun focusIndicatorIsIndependentFromBaseColorResolution() {
        /** Combined state whose base and focus layers must both remain visible. */
        val states = PixelControlStateSet.of(
            PixelControlState.Focused,
            PixelControlState.Error,
        )
        /** Text-field token that exposes both an error outline and focus indicator. */
        val token = PixelComponentTokens.Default.textField

        assertEquals(PixelColorRole.Danger, token.borderColor.resolve(states))
        assertNotNull(token.focusIndicatorFor(states))
        assertNull(token.focusIndicatorFor(PixelControlStateSet.of(PixelControlState.Error)))
    }

    /** Every interactive token resolves all states and distinct capability/status channels. */
    @Test
    fun interactiveComponentTokensCoverTheCompleteStateMatrix() {
        /** Named interactive component families required by the M5 state contract. */
        val tokens = PixelComponentTokens.Default.run {
            mapOf(
                "button" to button,
                "textButton" to textButton,
                "textField" to textField,
                "listTile" to listTile,
                "checkbox" to checkbox,
                "switch" to switch,
                "slider" to slider,
                "tabs" to tabs,
                "segmented" to segmented,
                "valueAdjuster" to valueAdjuster,
                "menu" to menu,
                "dropdown" to dropdown,
                "slidable" to slidable,
                "refresh" to refresh,
                "scrollbar" to scrollbar,
            )
        }
        /** Base role triple used to detect silent status fallback. */
        fun PixelComponentColorTokens.roles(state: PixelControlState): Triple<PixelColorRole?, PixelColorRole?, PixelColorRole?> {
            /** One-state immutable set consumed by all three role properties. */
            val states = PixelControlStateSet.of(state)
            return Triple(
                containerColor.resolve(states),
                contentColor.resolve(states),
                borderColor.resolve(states),
            )
        }

        tokens.forEach { (name, token) ->
            /** Every enum value must resolve without component-specific branching or exception. */
            PixelControlState.entries.forEach { state -> token.roles(state) }
            if (token.focusIndicator != null) {
                assertNotNull(
                    "$name Focused must expose its independent indicator",
                    token.focusIndicatorFor(PixelControlStateSet.of(PixelControlState.Focused)),
                )
            }
            /** Status/capability states must never silently look exactly Normal. */
            listOf(
                PixelControlState.Disabled,
                PixelControlState.Error,
                PixelControlState.Loading,
            ).forEach { state ->
                assertTrue("$name $state must differ from Normal", token.roles(state) != token.roles(PixelControlState.Normal))
            }
        }
    }

    /** Asserts that [block] rejects an invalid state-map declaration. */
    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation path.
        }
    }
}
