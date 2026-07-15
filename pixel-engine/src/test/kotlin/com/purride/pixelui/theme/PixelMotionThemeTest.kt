package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Locks motion token resolution, accessibility replacement, and inherited scope behavior. */
class PixelMotionThemeTest {
    /** Zero animator scale removes duration, delay, transition, and spring for every role. */
    @Test
    fun zeroScaleAlwaysResolvesToImmediateTerminalState() {
        PixelMotionRole.entries.forEach { role ->
            val resolved = PixelMotionSpec(
                duration = 400.milliseconds,
                curve = Curves.EaseIn,
                delay = 80.milliseconds,
                transition = PixelMotionTransitionPreset.FadeScale,
                spring = PixelSpringSpec(),
                role = role,
            ).resolve(PixelMotionSettings(animatorDurationScale = 0f, reduceMotion = false))

            assertEquals(Duration.ZERO, resolved.duration)
            assertEquals(Duration.ZERO, resolved.delay)
            assertEquals(PixelMotionTransitionPreset.None, resolved.transition)
            assertNull(resolved.spring)
            assertTrue(resolved.isImmediate)
        }
    }

    /** Reduce motion uses role-specific replacements instead of one unsafe global zeroing rule. */
    @Test
    fun reduceMotionUsesRoleSpecificReplacementPolicy() {
        val settings = PixelMotionSettings(animatorDurationScale = 2f, reduceMotion = true)
        val feedback = spec(PixelMotionRole.Feedback).resolve(settings)
        val selection = spec(PixelMotionRole.Selection).resolve(settings)
        val continuous = spec(PixelMotionRole.Continuous).resolve(settings)
        val spatial = spec(PixelMotionRole.Spatial).resolve(settings)

        listOf(feedback, selection, continuous).forEach { resolved ->
            assertTrue(resolved.isImmediate)
            assertEquals(Duration.ZERO, resolved.duration)
            assertEquals(Duration.ZERO, resolved.delay)
            assertEquals(PixelMotionTransitionPreset.None, resolved.transition)
        }
        assertFalse(spatial.isImmediate)
        assertEquals(80.milliseconds, spatial.duration)
        assertEquals(Duration.ZERO, spatial.delay)
        assertEquals(PixelMotionTransitionPreset.Fade, spatial.transition)
        assertEquals(Curves.Linear, spatial.curve)
        assertNull(spatial.spring)
    }

    /** A no-transition spatial token remains no-transition under reduce motion. */
    @Test
    fun reducedSpatialNonePresetDoesNotInventAVisualTransition() {
        val resolved = PixelMotionSpec(
            duration = 40.milliseconds,
            transition = PixelMotionTransitionPreset.None,
            role = PixelMotionRole.Spatial,
        ).resolve(PixelMotionSettings(reduceMotion = true))

        assertEquals(40.milliseconds, resolved.duration)
        assertEquals(PixelMotionTransitionPreset.None, resolved.transition)
    }

    /** Invalid inputs fail at construction and huge multiplication saturates without wraparound. */
    @Test
    fun validationAndHugeScalePreventInvalidOrOverflowedDurations() {
        assertIllegalArgument { PixelMotionSettings(animatorDurationScale = -0.1f) }
        assertIllegalArgument { PixelMotionSettings(animatorDurationScale = Float.NaN) }
        assertIllegalArgument { PixelMotionSpec(duration = (-1).milliseconds) }
        assertIllegalArgument { PixelMotionSpec(duration = 1.milliseconds, delay = (-1).milliseconds) }
        assertIllegalArgument { PixelSpringSpec(stiffness = 0f) }
        assertIllegalArgument { PixelSpringSpec(dampingRatio = Float.NaN) }

        val resolved = PixelMotionSpec(duration = Duration.INFINITE, delay = 1.milliseconds)
            .resolve(PixelMotionSettings(animatorDurationScale = Float.MAX_VALUE))
        assertEquals(Duration.INFINITE, resolved.duration)
        assertFalse(resolved.delay.isNegative())

        val largeFinite = Duration.parse("100000000d")
        assertFalse(largeFinite.isInfinite())
        val saturated = PixelMotionSpec(duration = largeFinite, delay = largeFinite)
            .resolve(PixelMotionSettings(animatorDurationScale = Float.MAX_VALUE))
        assertEquals(Duration.INFINITE, saturated.duration)
        assertEquals(Duration.INFINITE, saturated.delay)

        val infiniteScale = PixelMotionSpec(duration = 1.milliseconds, delay = 2.milliseconds)
            .resolve(PixelMotionSettings(animatorDurationScale = Float.POSITIVE_INFINITY))
        assertEquals(Duration.INFINITE, infiniteScale.duration)
        assertEquals(Duration.INFINITE, infiniteScale.delay)
    }

    /** Theme and Host motion scopes expose their exact immutable values to retained descendants. */
    @Test
    fun inheritedThemeAndScopeExposeTokensSettingsAndVsync() {
        val tester = PixelTester()
        val scheduler = ManualFrameScheduler()
        val expectedVsync = com.purride.pixelui.animation.PixelTickerProvider(scheduler)
        val expectedSettings = PixelMotionSettings(animatorDurationScale = 1.5f, reduceMotion = true)
        val expectedTheme = PixelMotionThemeData(
            feedback = PixelMotionSpec(17.milliseconds, role = PixelMotionRole.Feedback),
        )
        val capture = MotionScopeCapture()

        tester.pumpWidget(
            PixelMotionTheme(
                data = expectedTheme,
                child = PixelMotionScope(
                    vsync = expectedVsync,
                    settings = expectedSettings,
                    child = MotionScopeProbe(capture),
                ),
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(expectedTheme, capture.theme)
        assertEquals(expectedSettings, capture.settings)
        assertSame(expectedVsync, capture.vsync)
        tester.dispose()
        expectedVsync.dispose()
    }

    /** Without a Host or explicit scope, maybeOf is null and theme falls back to Default. */
    @Test
    fun missingScopeIsObservableWhileThemeHasSafeDefault() {
        val tester = PixelTester()
        val capture = MotionScopeCapture()
        tester.pumpWidget(MotionScopeProbe(capture), logicalWidth = 4, logicalHeight = 4)

        assertEquals(PixelMotionThemeData.Default, capture.theme)
        assertNull(capture.settings)
        assertNull(capture.vsync)
        tester.dispose()
    }

    /** Creates a representative token for the requested accessibility role. */
    private fun spec(role: PixelMotionRole): PixelMotionSpec {
        return PixelMotionSpec(
            duration = 200.milliseconds,
            curve = Curves.EaseIn,
            delay = 50.milliseconds,
            transition = PixelMotionTransitionPreset.SlideHorizontal,
            spring = PixelSpringSpec(),
            role = role,
        )
    }

    /** Asserts that [block] rejects invalid public motion input. */
    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation path.
        }
    }
}

/** Mutable assertion sink populated during retained widget build. */
private class MotionScopeCapture {
    /** Theme resolved by the probe, or null before its first build. */
    var theme: PixelMotionThemeData? = null

    /** Motion settings resolved by the probe, or null without a scope. */
    var settings: PixelMotionSettings? = null

    /** Ticker provider resolved by the probe, or null without a scope. */
    var vsync: com.purride.pixelui.animation.PixelTickerProvider? = null
}

/** Stateless retained probe that reads both motion inherited environments. */
private class MotionScopeProbe(
    /** Sink receiving values read from [BuildContext]. */
    val capture: MotionScopeCapture,
) : StatelessWidget() {
    /** Captures inherited values and returns a fixed paintable leaf. */
    override fun build(context: BuildContext): Widget {
        val scope = PixelMotionScope.maybeOf(context)
        capture.theme = PixelMotionTheme.of(context)
        capture.settings = scope?.settings
        capture.vsync = scope?.vsync
        return Container(width = 1, height = 1, fillColor = PixelColor.White, borderColor = null)
    }
}
