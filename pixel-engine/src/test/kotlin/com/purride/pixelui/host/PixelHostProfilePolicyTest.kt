package com.purride.pixelui

import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelViewportFit
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelViewportQuantization
import com.purride.pixelcore.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/** Verifies all fixed and adaptive profile policies through the pure Host resolver. */
class PixelHostProfilePolicyTest {
    /** Fixed 策略返回调用方拥有的 profile，不受视口与密度影响。 */
    @Test
    fun fixedProfileRemainsExactAcrossViewportAndDensityInputs() {
        /** Exact manual profile pinned by the fixed policy. */
        val profile = ScreenProfile(
            logicalWidth = 37,
            logicalHeight = 19,
            dotSizePx = 7,
            pixelShape = PixelShape.DIAMOND,
        )
        /** Result resolved against deliberately unrelated environment values. */
        val resolved = PixelHostProfileResolver.resolve(
            policy = PixelHostProfilePolicy.Fixed(profile),
            widthPx = 1,
            heightPx = 1,
            density = 4f,
            viewportPolicy = PixelViewportPolicy(
                fit = PixelViewportFit.COVER,
                quantization = PixelViewportQuantization.FRACTIONAL,
            ),
        )

        assertSame(profile, resolved)
    }

    /** AdaptivePixels recomputes logical dimensions on resize and ignores density. */
    @Test
    fun adaptivePixelsRecomputesFromPhysicalDotSize() {
        /** Eight-physical-pixel circular policy shared by both resolutions. */
        val policy = PixelHostProfilePolicy.AdaptivePixels(
            dotSizePx = 8,
            pixelShape = PixelShape.CIRCLE,
        )
        /** Initial 100×80 viewport projection. */
        val initial = resolve(policy = policy, widthPx = 100, heightPx = 80, density = 1f)
        /** Wider viewport projection proving logical width re-evaluation. */
        val resized = resolve(policy = policy, widthPx = 160, heightPx = 80, density = 4f)

        assertEquals(ScreenProfile(12, 10, 8, PixelShape.CIRCLE), initial)
        assertEquals(ScreenProfile(20, 10, 8, PixelShape.CIRCLE), resized)
    }

    /** AdaptiveDp converts dp to pixels for every density snapshot before resolving dimensions. */
    @Test
    fun adaptiveDpRecomputesDotSizeAndLogicalDimensionsWhenDensityChanges() {
        /** Four-dp square policy whose physical dot size depends on density. */
        val policy = PixelHostProfilePolicy.AdaptiveDp(dotSizeDp = 4f)
        /** Two-density result producing an eight-pixel dot. */
        val densityTwo = resolve(policy = policy, widthPx = 160, heightPx = 80, density = 2f)
        /** Four-density result producing a sixteen-pixel dot. */
        val densityFour = resolve(policy = policy, widthPx = 160, heightPx = 80, density = 4f)

        assertEquals(ScreenProfile(20, 10, 8), densityTwo)
        assertEquals(ScreenProfile(10, 5, 16), densityFour)
    }

    /** AdaptiveLogicalSize keeps its grid fixed while dot diagnostics follow viewport strategy. */
    @Test
    fun adaptiveLogicalSizeKeepsDimensionsAndReevaluatesViewportScale() {
        /** Stable logical grid evaluated against non-integral contain and integral cover scales. */
        val policy = PixelHostProfilePolicy.AdaptiveLogicalSize(
            logicalWidth = 40,
            logicalHeight = 20,
            pixelShape = PixelShape.DIAMOND,
        )
        /** Fractional contain chooses 2.5 physical pixels per logical pixel and rounds diagnostics. */
        val contain = PixelHostProfileResolver.resolve(
            policy = policy,
            widthPx = 100,
            heightPx = 80,
            density = 1f,
            viewportPolicy = PixelViewportPolicy(
                fit = PixelViewportFit.CONTAIN,
                quantization = PixelViewportQuantization.FRACTIONAL,
            ),
        )
        /** Integer cover chooses four physical pixels per logical pixel. */
        val cover = PixelHostProfileResolver.resolve(
            policy = policy,
            widthPx = 100,
            heightPx = 80,
            density = 1f,
            viewportPolicy = PixelViewportPolicy(
                fit = PixelViewportFit.COVER,
                quantization = PixelViewportQuantization.INTEGER,
            ),
        )

        assertEquals(ScreenProfile(40, 20, 3, PixelShape.DIAMOND), contain)
        assertEquals(ScreenProfile(40, 20, 4, PixelShape.DIAMOND), cover)
    }

    /** Public adaptive policies reject values that cannot define a valid logical viewport. */
    @Test
    fun invalidAdaptiveInputsFailAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            PixelHostProfilePolicy.AdaptivePixels(dotSizePx = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelHostProfilePolicy.AdaptiveDp(dotSizeDp = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelHostProfilePolicy.AdaptiveLogicalSize(logicalWidth = 0, logicalHeight = 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelHostProfilePolicy.AdaptiveLogicalSize(logicalWidth = 10, logicalHeight = -1)
        }
    }

    /** Resolves one adaptive policy with the stable legacy viewport strategy. */
    private fun resolve(
        /** Policy under test. */
        policy: PixelHostProfilePolicy,
        /** Physical width supplied to the resolver. */
        widthPx: Int,
        /** Physical height supplied to the resolver. */
        heightPx: Int,
        /** Density supplied to dp conversion. */
        density: Float,
    ): ScreenProfile {
        return PixelHostProfileResolver.resolve(
            policy = policy,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            viewportPolicy = PixelViewportPolicy(),
        )
    }
}
