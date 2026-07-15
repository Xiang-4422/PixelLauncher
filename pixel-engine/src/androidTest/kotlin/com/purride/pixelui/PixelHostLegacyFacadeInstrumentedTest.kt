package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** API 37 real-Host acceptance for scope-less legacy facade pixel compatibility. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostLegacyFacadeInstrumentedTest {
    /**
     * The scope-less ProgressBar must match its reviewed historical fixture exactly, while mounting
     * the same old facade below an explicit PixelTheme must consume the new token colors and size.
     */
    @Test
    fun legacyProgressFacadeMatchesFixtureAndExplicitThemeChangesRealHostPixels() {
        /** Physical Android frame produced by the public scope-less compatibility facade. */
        lateinit var legacyFacadeFrame: HostPixelFrame
        /** Physical Android frame produced by the explicit historical primitive fixture. */
        lateinit var legacyFixtureFrame: HostPixelFrame
        /** Physical Android frame produced by the old facade below an explicit theme provider. */
        lateinit var explicitlyThemedFrame: HostPixelFrame

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Attached production PixelHostView used for every compared physical frame. */
                val host = activity.hostView
                host.motionSettingsOverride = PixelMotionSettings(animatorDurationScale = 0f)
                host.setPixelGapEnabled(false)
                host.bezelColor = PixelColor.Black
                host.offPixelColor = OFF_PIXEL_COLOR

                legacyFacadeFrame = captureFrame(host) {
                    ProgressBar(progress = LEGACY_PROGRESS)
                }
                legacyFixtureFrame = captureFrame(host, ::reviewedLegacyProgressFixture)
                explicitlyThemedFrame = captureFrame(host) {
                    PixelTheme(
                        tokens = EXPLICIT_PROGRESS_THEME,
                        child = ProgressBar(progress = LEGACY_PROGRESS),
                    )
                }
            }
        }

        assertArrayEquals(
            "Scope-less old facade must remain pixel-identical to the reviewed legacy fixture",
            legacyFixtureFrame.pixels,
            legacyFacadeFrame.pixels,
        )
        assertEquals(LEGACY_ACTIVE_COLOR.argb, legacyFacadeFrame.logicalArgb(0, 0))
        assertEquals(LEGACY_ACTIVE_COLOR.argb, legacyFacadeFrame.logicalArgb(23, 4))
        assertEquals(PixelColor.White.argb, legacyFacadeFrame.logicalArgb(24, 0))
        assertEquals(LEGACY_TRACK_COLOR.argb, legacyFacadeFrame.logicalArgb(25, 2))
        assertEquals(PixelColor.White.argb, legacyFacadeFrame.logicalArgb(47, 4))
        assertEquals(OFF_PIXEL_COLOR.argb, legacyFacadeFrame.logicalArgb(48, 2))

        assertFalse(
            "An explicit PixelTheme must opt the old facade out of scope-less legacy rendering",
            legacyFacadeFrame.pixels.contentEquals(explicitlyThemedFrame.pixels),
        )
        assertEquals(THEMED_ACTIVE_COLOR.argb, explicitlyThemedFrame.logicalArgb(0, 0))
        assertEquals(THEMED_ACTIVE_COLOR.argb, explicitlyThemedFrame.logicalArgb(30, 10))
        assertEquals(THEMED_TRACK_COLOR.argb, explicitlyThemedFrame.logicalArgb(31, 5))
        assertEquals(THEMED_TRACK_COLOR.argb, explicitlyThemedFrame.logicalArgb(62, 10))
        assertEquals(OFF_PIXEL_COLOR.argb, explicitlyThemedFrame.logicalArgb(63, 5))
        assertNotEquals(
            "The explicit provider must change the corresponding active pixel",
            legacyFacadeFrame.logicalArgb(0, 0),
            explicitlyThemedFrame.logicalArgb(0, 0),
        )
        assertNotEquals(
            "The explicit provider must expand the token-controlled progress geometry",
            legacyFacadeFrame.logicalArgb(48, 2),
            explicitlyThemedFrame.logicalArgb(48, 2),
        )
    }

    /** Builds the reviewed pre-theme ProgressBar stack without calling either public facade. */
    private fun reviewedLegacyProgressFixture(): Widget {
        return Stack(
            children = listOf(
                Container(
                    width = LEGACY_WIDTH,
                    height = LEGACY_HEIGHT,
                    fillColor = LEGACY_TRACK_COLOR,
                    borderColor = PixelColor.White,
                ),
                Container(
                    width = LEGACY_FILLED_WIDTH,
                    height = LEGACY_HEIGHT,
                    fillColor = LEGACY_ACTIVE_COLOR,
                ),
            ),
        )
    }

    /** Replaces Host content, draws the attached View, and returns its immutable physical pixels. */
    private fun captureFrame(host: PixelHostView, content: () -> Widget): HostPixelFrame {
        host.setContent(content)
        host.invalidate()
        /** Current grid geometry used by the same production draw call sampled below. */
        val geometry = checkNotNull(host.resolveGridGeometry())
        /** Android bitmap receiving the real attached View draw. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        /** Defensive row-major copy retained after the temporary bitmap is recycled. */
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        /** Immutable frame dimensions required for deterministic logical-cell sampling. */
        val frameWidth = bitmap.width
        /** Immutable frame height retained to clamp logical-cell sampling safely. */
        val frameHeight = bitmap.height
        bitmap.recycle()
        return HostPixelFrame(
            pixels = pixels,
            width = frameWidth,
            height = frameHeight,
            cellSize = geometry.cellSize,
            originX = geometry.originX,
            originY = geometry.originY,
        )
    }

    /** Immutable physical Host draw plus the production logical-to-Android grid transform. */
    private data class HostPixelFrame(
        /** Android ARGB pixels in row-major order. */
        val pixels: IntArray,
        /** Physical bitmap width in Android pixels. */
        val width: Int,
        /** Physical bitmap height in Android pixels. */
        val height: Int,
        /** Physical size of one logical pixel cell. */
        val cellSize: Float,
        /** Physical horizontal origin of the centered logical screen. */
        val originX: Float,
        /** Physical vertical origin of the centered logical screen. */
        val originY: Float,
    ) {
        /** Returns the Android ARGB value at the center of one production logical cell. */
        fun logicalArgb(logicalX: Int, logicalY: Int): Int {
            /** Horizontal physical center clamped to the captured bitmap. */
            val physicalX = (originX + (logicalX + 0.5f) * cellSize)
                .toInt()
                .coerceIn(0, width - 1)
            /** Vertical physical center clamped to the captured bitmap. */
            val physicalY = (originY + (logicalY + 0.5f) * cellSize)
                .toInt()
                .coerceIn(0, height - 1)
            return pixels[physicalY * width + physicalX]
        }
    }

    /** Reviewed legacy colors, geometry, and explicit-theme sentinels. */
    private companion object {
        /** Historical determinate progress fraction used by the public old facade. */
        const val LEGACY_PROGRESS: Float = 0.5f

        /** Historical default ProgressBar width in logical pixels. */
        const val LEGACY_WIDTH: Int = 48

        /** Historical default ProgressBar height in logical pixels. */
        const val LEGACY_HEIGHT: Int = 5

        /** Historical integer filled width for [LEGACY_PROGRESS]. */
        const val LEGACY_FILLED_WIDTH: Int = 24

        /** Explicit-theme minimum width proving geometry leaves legacy compatibility mode. */
        const val THEMED_MINIMUM_WIDTH: Int = 63

        /** Explicit-theme minimum height proving geometry leaves legacy compatibility mode. */
        const val THEMED_MINIMUM_HEIGHT: Int = 11

        /** Historical default active progress color. */
        val LEGACY_ACTIVE_COLOR: PixelColor = PixelColor.fromRgb(80, 180, 110)

        /** Historical default progress track color. */
        val LEGACY_TRACK_COLOR: PixelColor = PixelColor.fromRgb(60, 60, 60)

        /** Distinct Host off-pixel sentinel used to prove component extents. */
        val OFF_PIXEL_COLOR: PixelColor = PixelColor.fromRgb(3, 5, 7)

        /** Distinct explicit-theme active color consumed through the Primary role. */
        val THEMED_ACTIVE_COLOR: PixelColor = PixelColor.fromRgb(13, 173, 241)

        /** Distinct explicit-theme track color consumed through the Track role. */
        val THEMED_TRACK_COLOR: PixelColor = PixelColor.fromRgb(227, 41, 109)

        /** Consumer theme whose progress colors and dimensions cannot match legacy defaults. */
        val EXPLICIT_PROGRESS_THEME: PixelThemeTokens = PixelThemeTokens.Dark.copy(
            colors = PixelThemeTokens.Dark.colors.copy(
                primary = THEMED_ACTIVE_COLOR,
                track = THEMED_TRACK_COLOR,
            ),
            components = PixelThemeTokens.Dark.components.copy(
                progress = PixelThemeTokens.Dark.components.progress.copy(
                    minimumWidth = THEMED_MINIMUM_WIDTH,
                    minimumHeight = THEMED_MINIMUM_HEIGHT,
                ),
            ),
        )
    }
}
