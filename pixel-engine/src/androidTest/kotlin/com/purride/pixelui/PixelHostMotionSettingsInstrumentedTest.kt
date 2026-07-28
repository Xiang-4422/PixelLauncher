package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelMotionSettingsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** Host motion source attachment, override, inheritance, and terminal cleanup acceptance test. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostMotionSettingsInstrumentedTest {
    /** Fake source follows attach/detach and Host override wins without stopping source updates. */
    @Test
    fun fakeMotionSourceIsLifecycleBoundAndInjectedIntoRootScope() {
        val initialSettings = PixelMotionSettings(animatorDurationScale = 1.5f, reduceMotion = false)
        val fakeSource = FakeMotionSettingsSource(initialSettings)
        val capture = HostMotionCapture()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.replaceMotionSettingsSourceForTesting(fakeSource)
                host.setContent { HostMotionProbe(capture) }
                renderSynchronously(host)

                assertEquals(1, fakeSource.attachCount)
                assertEquals(initialSettings, capture.settings)
                assertSame(host.tickerProvider, capture.vsync)

                val platformUpdate = PixelMotionSettings(animatorDurationScale = 0.75f, reduceMotion = true)
                fakeSource.emit(platformUpdate)
                renderSynchronously(host)
                assertEquals(platformUpdate, capture.settings)

                val explicitOverride = PixelMotionSettings(animatorDurationScale = 0f, reduceMotion = false)
                // 环境覆盖只通过完整快照表达，从当前快照派生即可只改动 motion 字段。
                host.capabilitiesOverride = host.hostCapabilities.copy(motionSettings = explicitOverride)
                fakeSource.emit(PixelMotionSettings(animatorDurationScale = 2f, reduceMotion = false))
                renderSynchronously(host)
                assertEquals(explicitOverride, capture.settings)

                host.capabilitiesOverride = null
                renderSynchronously(host)
                assertEquals(PixelMotionSettings(animatorDurationScale = 2f), capture.settings)

                activity.rootView.removeView(host)
                assertEquals(1, fakeSource.detachCount)
                activity.rootView.addView(host)
                renderSynchronously(host)
                assertEquals(2, fakeSource.attachCount)

                host.destroy()
                assertEquals(1, fakeSource.destroyCount)
                assertEquals(2, fakeSource.detachCount)
            }
        }

        assertEquals(1, fakeSource.destroyCount)
    }

    /** Draws a retained Host frame synchronously without waiting for a platform vsync. */
    private fun renderSynchronously(host: PixelHostView) {
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}

/** Injectable settings source with explicit lifecycle counters and synchronous emissions. */
private class FakeMotionSettingsSource(
    /** Initial settings returned before the first fake emission. */
    initialSettings: PixelMotionSettings,
) : PixelMotionSettingsSource {
    /** Mutable current snapshot returned to Host attachment. */
    private var settings: PixelMotionSettings = initialSettings

    /** Active Host callback, retained only while attached. */
    private var callback: ((PixelMotionSettings) -> Unit)? = null

    /** Whether terminal fake destruction has completed. */
    private var destroyed: Boolean = false

    /** Number of effective listener attachments. */
    var attachCount: Int = 0
        private set

    /** Number of effective listener detachments. */
    var detachCount: Int = 0
        private set

    /** Number of effective terminal destroys. */
    var destroyCount: Int = 0
        private set

    /** Latest settings available without listener registration. */
    override val currentSettings: PixelMotionSettings
        get() = settings

    /** Attaches once and immediately synchronizes the current snapshot. */
    override fun attach(onChanged: (PixelMotionSettings) -> Unit) {
        if (destroyed || callback != null) return
        attachCount += 1
        callback = onChanged
        onChanged(settings)
    }

    /** Detaches once and clears the callback reference. */
    override fun detach() {
        if (callback == null) return
        detachCount += 1
        callback = null
    }

    /** Performs terminal cleanup once. */
    override fun destroy() {
        if (destroyed) return
        detach()
        destroyed = true
        destroyCount += 1
    }

    /** Emits [nextSettings] only to an attached Host while retaining it for reattachment. */
    fun emit(nextSettings: PixelMotionSettings) {
        settings = nextSettings
        callback?.invoke(nextSettings)
    }
}

/** Mutable values captured from the current Host-injected motion scope. */
private class HostMotionCapture {
    /** Last inherited settings snapshot. */
    var settings: PixelMotionSettings? = null

    /** Last inherited lifecycle-bound ticker provider. */
    var vsync: PixelTickerProvider? = null
}

/** Retained probe that reads Host motion scope values during build. */
private class HostMotionProbe(
    /** Sink receiving every inherited value update. */
    val capture: HostMotionCapture,
) : StatelessWidget() {
    /** Captures the required Host scope and returns a fixed pixel leaf. */
    override fun build(context: BuildContext): Widget {
        val scope = PixelMotionScope.of(context)
        capture.settings = scope.settings
        capture.vsync = scope.vsync
        return Container(width = 1, height = 1, fillColor = PixelColor.White, borderColor = null)
    }
}
