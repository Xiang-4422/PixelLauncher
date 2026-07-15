package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises full-frame diagnostics through a real attached PixelHostView and Android Canvas. */
@RunWith(AndroidJUnit4::class)
class PixelHostFrameDiagnosticsInstrumentedTest {
    /** Verifies opt-in gating, real Canvas submit timing, Inspector exposure, and cache-hit work. */
    @Test
    fun attachedHostReportsCompleteFrameOnlyWhenDiagnosticsAreEnabled() {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Attached production Host whose draw path is measured end to end. */
                val host = activity.hostView
                /** Stable retained content reused to force a complete render-result cache hit. */
                val content = Container(
                    width = 48,
                    height = 32,
                    fillColor = PixelColor.fromRgb(0x33, 0x66, 0x99),
                )
                host.frameDiagnosticsEnabled = false
                host.frameDiagnosticsObserver = null

                drawSynchronously(host)

                assertNull(host.latestFrameDiagnostics)

                /** Full diagnostics snapshots received synchronously on the Activity main thread. */
                val observedDiagnostics = mutableListOf<PixelHostFrameDiagnostics>()
                /** Legacy snapshots proving paintTimeNanos now includes the Android submit phase. */
                val observedLegacyStats = mutableListOf<PixelHostFrameStats>()
                host.frameDiagnosticsObserver = { diagnostics ->
                    assertTrue(Looper.myLooper() === Looper.getMainLooper())
                    observedDiagnostics += diagnostics
                }
                host.frameStatsObserver = { stats -> observedLegacyStats += stats }
                host.setContent { content }

                drawSynchronously(host)

                /** First opt-in snapshot emitted by the real Host draw. */
                val first = observedDiagnostics.last()
                assertTrue(first.timings.buildNanos > 0L)
                assertTrue(first.timings.layoutNanos >= 0L)
                assertTrue(first.timings.paintNanos > 0L)
                assertTrue(first.timings.bufferSubmitNanos > 0L)
                assertTrue(first.timings.androidDrawNanos > 0L)
                assertTrue(first.timings.totalFrameNanos >= first.timings.bufferSubmitNanos)
                assertTrue(first.workload.dirtyElementCount > 0)
                assertTrue(first.workload.dirtyRenderNodeCount > 0)
                assertTrue(first.workload.paintedPixelCount > 0L)
                assertTrue(first.workload.submittedPixelCount > 0L)
                assertNotNull(first.workload.allocatedBytes)
                assertNotNull(first.workload.garbageCollectionCount)
                assertEquals(first, host.latestFrameDiagnostics)
                assertEquals(first, host.inspect().frameDiagnostics)
                assertTrue(observedLegacyStats.last().paintTimeNanos >= first.timings.bufferSubmitNanos)

                drawSynchronously(host)

                /** Unchanged second frame still submits Canvas pixels but reuses engine paint output. */
                val cached = observedDiagnostics.last()
                assertTrue(cached.frameNumber > first.frameNumber)
                assertTrue(cached.workload.renderCacheHit)
                assertEquals(0L, cached.workload.paintedPixelCount)
                assertTrue(cached.workload.submittedPixelCount > 0L)
                assertTrue(cached.timings.bufferSubmitNanos > 0L)

                host.frameDiagnosticsObserver = null
                host.frameDiagnosticsEnabled = false
                /** Last completed snapshot remains inspectable but no new frame may replace it. */
                val lastEnabledFrame = host.latestFrameDiagnostics
                drawSynchronously(host)
                assertEquals(lastEnabledFrame, host.latestFrameDiagnostics)
                assertFalse(observedDiagnostics.isEmpty())
            }
        }
    }

    /** Draws the attached Host synchronously into a temporary real Android bitmap Canvas. */
    private fun drawSynchronously(host: PixelHostView) {
        /** Physical bitmap matching the currently laid-out Host view. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            host.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
    }
}
