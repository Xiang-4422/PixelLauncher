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

/** API 37 真机 Host 验收：简洁入口与状态化入口等价，以及 token 解析。 API 37 real-Host acceptance for concise/state-aware component equivalence and token resolution. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostComponentTokenInstrumentedTest {
    /**
     * 简洁 ProgressBar 与状态化实现必须在真实 Host 上渲染完全相同的物理像素；
     * 同一简洁入口挂载显式 PixelTheme 后必须消费新的 token 颜色与几何。
     */
    @Test
    fun conciseProgressFacadeMatchesStateAwareAndExplicitThemeChangesRealHostPixels() {
        /** 简洁公开入口产生的真实 Android 帧。 Physical Android frame produced by the concise public facade. */
        lateinit var conciseFrame: HostPixelFrame
        /** 状态化重载在相同输入下产生的真实 Android 帧。 Physical Android frame produced by the state-aware overload with identical inputs. */
        lateinit var stateAwareFrame: HostPixelFrame
        /** 简洁入口在显式主题提供者下产生的真实 Android 帧。 Physical Android frame produced by the concise facade below an explicit theme provider. */
        lateinit var explicitlyThemedFrame: HostPixelFrame

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Attached production PixelHostView used for every compared physical frame. */
                val host = activity.hostView
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                host.setPixelGapEnabled(false)
                host.bezelColor = PixelColor.Black
                host.offPixelColor = OFF_PIXEL_COLOR

                conciseFrame = captureFrame(host) {
                    ProgressBar(progress = SAMPLED_PROGRESS)
                }
                stateAwareFrame = captureFrame(host) {
                    ProgressBar(progress = SAMPLED_PROGRESS, states = PixelControlStateSet.Normal)
                }
                explicitlyThemedFrame = captureFrame(host) {
                    PixelTheme(
                        tokens = EXPLICIT_PROGRESS_THEME,
                        child = ProgressBar(progress = SAMPLED_PROGRESS),
                    )
                }
            }
        }

        assertArrayEquals(
            "Concise facade must remain pixel-identical to the state-aware implementation",
            stateAwareFrame.pixels,
            conciseFrame.pixels,
        )
        assertEquals(DEFAULT_ACTIVE_COLOR.argb, conciseFrame.logicalArgb(0, 0))
        assertEquals(DEFAULT_ACTIVE_COLOR.argb, conciseFrame.logicalArgb(23, 6))
        assertEquals(DEFAULT_TRACK_COLOR.argb, conciseFrame.logicalArgb(24, 3))
        assertEquals(DEFAULT_TRACK_COLOR.argb, conciseFrame.logicalArgb(47, 6))
        assertEquals(OFF_PIXEL_COLOR.argb, conciseFrame.logicalArgb(48, 3))

        assertFalse(
            "An explicit PixelTheme must change the concise facade's resolved token output",
            conciseFrame.pixels.contentEquals(explicitlyThemedFrame.pixels),
        )
        assertEquals(THEMED_ACTIVE_COLOR.argb, explicitlyThemedFrame.logicalArgb(0, 0))
        assertEquals(THEMED_ACTIVE_COLOR.argb, explicitlyThemedFrame.logicalArgb(30, 10))
        assertEquals(THEMED_TRACK_COLOR.argb, explicitlyThemedFrame.logicalArgb(31, 5))
        assertEquals(THEMED_TRACK_COLOR.argb, explicitlyThemedFrame.logicalArgb(62, 10))
        assertEquals(OFF_PIXEL_COLOR.argb, explicitlyThemedFrame.logicalArgb(63, 5))
        assertNotEquals(
            "The explicit provider must change the corresponding active pixel",
            conciseFrame.logicalArgb(0, 0),
            explicitlyThemedFrame.logicalArgb(0, 0),
        )
        assertNotEquals(
            "The explicit provider must expand the token-controlled progress geometry",
            conciseFrame.logicalArgb(48, 3),
            explicitlyThemedFrame.logicalArgb(48, 3),
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

    /** 默认 token 颜色、几何与显式主题哨兵值。 Default-token colors, geometry, and explicit-theme sentinels. */
    private companion object {
        /** 两个公开入口共同采样的确定性进度比例。 Determinate progress fraction sampled by both public entry points. */
        const val SAMPLED_PROGRESS: Float = 0.5f

        /** 显式主题最小宽度，证明组件几何 token 已被消费。 Explicit-theme minimum width proving component geometry tokens are consumed. */
        const val THEMED_MINIMUM_WIDTH: Int = 63

        /** 显式主题最小高度，证明组件几何 token 已被消费。 Explicit-theme minimum height proving component geometry tokens are consumed. */
        const val THEMED_MINIMUM_HEIGHT: Int = 11

        /** 默认深色主题由 Primary 角色解析出的进度前景色。 Default dark-theme active progress color resolved from the Primary role. */
        val DEFAULT_ACTIVE_COLOR: PixelColor = PixelColorScheme.Dark.primary

        /** 默认深色主题由 Track 角色解析出的进度轨道色。 Default dark-theme progress track color resolved from the Track role. */
        val DEFAULT_TRACK_COLOR: PixelColor = PixelColorScheme.Dark.track

        /** Distinct Host off-pixel sentinel used to prove component extents. */
        val OFF_PIXEL_COLOR: PixelColor = PixelColor.fromRgb(3, 5, 7)

        /** Distinct explicit-theme active color consumed through the Primary role. */
        val THEMED_ACTIVE_COLOR: PixelColor = PixelColor.fromRgb(13, 173, 241)

        /** Distinct explicit-theme track color consumed through the Track role. */
        val THEMED_TRACK_COLOR: PixelColor = PixelColor.fromRgb(227, 41, 109)

        /** 消费者主题，其进度颜色与尺寸不可能与默认 token 相同。 Consumer theme whose progress colors and dimensions cannot match default tokens. */
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
