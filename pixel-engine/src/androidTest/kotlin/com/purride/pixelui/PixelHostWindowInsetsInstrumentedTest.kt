package com.purride.pixelui

import android.graphics.Insets
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the real [WindowInsets] decoding branches used by [PixelHostView] on Android 7–16.
 *
 * The fixtures intentionally enter through [PixelHostView.onApplyWindowInsets] instead of the
 * engine's raw-inset test seam, so API 24–29 legacy splitting and API 30+ typed channels are both
 * exercised by the platform runtime that owns each API surface.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostWindowInsetsInstrumentedTest {
    /**
     * System bars and IME remain separate logical channels on every supported implementation
     * branch, including API 24 where Android only exposes a combined current inset rectangle.
     */
    @Test
    fun platformWindowInsetsExposeSeparateSystemBarsAndImeChannels() {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Attached Activity Host supplying a real platform object for the legacy copy API. */
                val attachedHost = activity.hostView
                /** Detached production Host isolated from an asynchronous second framework dispatch. */
                val host = PixelHostView(activity)
                try {
                    host.layout(0, 0, FIXTURE_PHYSICAL_SIZE, FIXTURE_PHYSICAL_SIZE)

                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> verifyTypedInsets(host)
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> verifyApi29Insets(host)
                        else -> verifyLegacyInsets(
                            host = host,
                            baseInsets = requireNotNull(attachedHost.rootWindowInsets),
                        )
                    }
                } finally {
                    host.destroy()
                }
            }
        }
    }

    /** Verifies API 30+ `Type.systemBars()` and `Type.ime()` remain independent. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun verifyTypedInsets(host: PixelHostView) {
        /** Typed platform fixture with disjoint, non-zero system-bar and IME edges. */
        val insets = Api30WindowInsetsFixture.create()

        host.dispatchApplyWindowInsets(insets)

        assertEquals(EXPECTED_SYSTEM_BARS, host.windowInsets)
        assertEquals(EXPECTED_IME, host.viewInsets)
    }

    /** Verifies API 29 Builder data flows through the legacy current-versus-stable split. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun verifyApi29Insets(host: PixelHostView) {
        /** API 29 fixture whose current bottom edge contains a transient IME extent. */
        val insets = Api29WindowInsetsFixture.create()

        host.dispatchApplyWindowInsets(insets)

        assertEquals(EXPECTED_SYSTEM_BARS, host.windowInsets)
        assertEquals(EXPECTED_IME, host.viewInsets)
    }

    /**
     * Verifies API 24–28 using an isolated test-only fixture, because those platform versions expose
     * no public Builder capable of preserving separate current and stable inset rectangles.
     */
    @Suppress("DEPRECATION")
    private fun verifyLegacyInsets(host: PixelHostView, baseInsets: WindowInsets) {
        /** Real platform object with independently controlled legacy stable/current rectangles. */
        val insets = LegacyWindowInsetsFixture.create(baseInsets)

        host.dispatchApplyWindowInsets(insets)

        /** Physical channels retained before any logical viewport projection. */
        val rawChannels = host.rawPlatformInsetChannelsForTesting()
        assertEquals(EXPECTED_PHYSICAL_SYSTEM_BARS, rawChannels.systemBars)
        assertEquals(EXPECTED_PHYSICAL_IME, rawChannels.ime)
        assertEquals(EXPECTED_SYSTEM_BARS, host.windowInsets)
        assertEquals(EXPECTED_IME, host.viewInsets)
    }

    private companion object {
        /** Physical extent matching the default 96-cell profile at its configured 8px dot size. */
        const val FIXTURE_PHYSICAL_SIZE: Int = 768

        /** Stable physical bars expressed as exact multiples of the default 8px dot. */
        val EXPECTED_PHYSICAL_SYSTEM_BARS: PixelPhysicalInsets = PixelPhysicalInsets(
            left = 8,
            top = 16,
            right = 24,
            bottom = 32,
        )

        /** Physical keyboard extent expressed as an exact multiple of the default 8px dot. */
        val EXPECTED_PHYSICAL_IME: PixelPhysicalInsets = PixelPhysicalInsets(bottom = 80)

        /** Logical stable bars after the shared 8px viewport projection. */
        val EXPECTED_SYSTEM_BARS: PixelWindowInsets = PixelWindowInsets(
            left = 1,
            top = 2,
            right = 3,
            bottom = 4,
        )

        /** Logical keyboard extent after the shared 8px viewport projection. */
        val EXPECTED_IME: PixelWindowInsets = PixelWindowInsets(bottom = 10)
    }
}

/**
 * Creates a real pre-API 29 [WindowInsets] with a stable rectangle unavailable through public APIs.
 *
 * Reflection is confined to the instrumentation fixture and never enters production code. The
 * fixture validates the public stable getters before returning, so a platform field-layout change
 * fails at setup instead of producing a false behavioral result.
 */
private object LegacyWindowInsetsFixture {
    /** Builds a combined current IME rectangle while retaining smaller stable system bars. */
    @Suppress("DEPRECATION")
    fun create(baseInsets: WindowInsets): WindowInsets {
        /** Real legacy object created through the public platform copy constructor. */
        val insets = WindowInsets(baseInsets)
        /** Test-only platform field backing the public current system-window inset getters. */
        val systemWindowInsetsField = WindowInsets::class.java.getDeclaredField("mSystemWindowInsets")
        systemWindowInsetsField.isAccessible = true
        systemWindowInsetsField.set(insets, Rect(8, 16, 24, 80))
        /** Test-only platform field backing the public stable-inset getters. */
        val stableInsetsField = WindowInsets::class.java.getDeclaredField("mStableInsets")
        stableInsetsField.isAccessible = true
        stableInsetsField.set(insets, Rect(8, 16, 24, 32))
        check(insets.systemWindowInsetLeft == 8)
        check(insets.systemWindowInsetTop == 16)
        check(insets.systemWindowInsetRight == 24)
        check(insets.systemWindowInsetBottom == 80)
        check(insets.stableInsetLeft == 8)
        check(insets.stableInsetTop == 16)
        check(insets.stableInsetRight == 24)
        check(insets.stableInsetBottom == 32)
        return insets
    }
}

/** API 29-only fixture isolated so API 24 class verification never resolves Builder symbols. */
@RequiresApi(Build.VERSION_CODES.Q)
private object Api29WindowInsetsFixture {
    /** Builds current and stable rectangles that deterministically exercise legacy IME splitting. */
    @Suppress("DEPRECATION")
    fun create(): WindowInsets {
        /** Stable system-bar dimensions retained after transient IME separation. */
        val stableBars = Insets.of(8, 16, 24, 32)
        /** Legacy combined rectangle whose larger bottom edge represents the IME. */
        val combinedCurrent = Insets.of(8, 16, 24, 80)
        return WindowInsets.Builder()
            .setStableInsets(stableBars)
            .setSystemWindowInsets(combinedCurrent)
            .build()
    }
}

/** API 30+-only fixture isolated so older runtimes never resolve typed WindowInsets APIs. */
@RequiresApi(Build.VERSION_CODES.R)
private object Api30WindowInsetsFixture {
    /** Builds independent typed system-bar and IME channels for the production Host branch. */
    fun create(): WindowInsets {
        /** Stable system-bar edges consumed through `Type.systemBars()`. */
        val stableBars = Insets.of(8, 16, 24, 32)
        /** Visible keyboard extent consumed through `Type.ime()`. */
        val ime = Insets.of(0, 0, 0, 80)
        return WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), stableBars)
            .setInsets(WindowInsets.Type.ime(), ime)
            .build()
    }
}
