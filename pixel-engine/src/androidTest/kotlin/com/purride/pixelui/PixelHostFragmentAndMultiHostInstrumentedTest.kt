package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API 24+ 真实 Fragment view lifecycle 与多 Host 隔离验收。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostFragmentAndMultiHostInstrumentedTest {
    /**
     * Fragment detach/attach 必须销毁旧 view owner、Host、State 与 ticker，随后创建全新一代。
     */
    @Test
    fun fragmentViewLifecycleDestroyAndRecreateUsesNewOwnerHostAndState() {
        val tracker = FragmentLifecycleProbeTracker()
        lateinit var newHost: PixelHostView
        lateinit var newState: FragmentLifecycleProbeState

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = PixelHostViewLifecycleFragment().apply {
                    this.tracker = tracker
                }
                activity.supportFragmentManager.beginTransaction()
                    .add(activity.rootView.id, fragment, FRAGMENT_TAG)
                    .commitNow()

                val oldHost = fragment.requireHostView()
                renderSynchronously(oldHost)
                val oldViewOwner = requireNotNull(oldHost.findViewTreeLifecycleOwner())
                val oldState = tracker.states.single()
                val oldScheduler = ManualFrameScheduler()
                oldHost.frameScheduler = oldScheduler
                val oldProvider = oldHost.tickerProvider
                val oldTicker = oldProvider.createTicker { }
                oldTicker.start()

                assertSame(fragment.viewLifecycleOwner, oldViewOwner)
                assertNotSame(activity, oldViewOwner)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, oldHost.lifecycleDiagnostics.ownerBinding)
                assertEquals(PixelHostLifecycleState.Resumed, oldHost.lifecycleDiagnostics.lifecycleState)
                assertTrue(oldHost.lifecycleDiagnostics.isInteractive)
                assertEquals(1, oldScheduler.pendingCount)
                assertEquals(1, oldHost.frameScopeDiagnostics.activeTickerCount)
                assertEquals(1, oldHost.frameScopeDiagnostics.liveTickerCount)

                activity.supportFragmentManager.beginTransaction()
                    .detach(fragment)
                    .commitNow()

                assertEquals(Lifecycle.State.DESTROYED, oldViewOwner.lifecycle.currentState)
                assertEquals(PixelHostLifecycleState.Destroyed, oldHost.lifecycleDiagnostics.lifecycleState)
                assertEquals(PixelHostLifecycleOwnerBinding.None, oldHost.lifecycleDiagnostics.ownerBinding)
                assertEquals(1L, oldHost.lifecycleDiagnostics.destroyCount)
                assertEquals(1, oldState.disposeCount)
                assertEquals(1, tracker.totalDisposeCount)
                assertTrue(oldTicker.isDisposed)
                assertTrue(oldProvider.diagnostics().isDisposed)
                assertTrue(oldHost.frameScopeDiagnostics.isDisposed)
                assertEquals(0, oldHost.frameScopeDiagnostics.pendingCallbackCount)
                assertEquals(0, oldHost.frameScopeDiagnostics.activeTickerCount)
                assertEquals(0, oldHost.frameScopeDiagnostics.liveTickerCount)
                assertFalse(oldHost.frameScopeDiagnostics.sourceFramePending)
                assertEquals(0, oldScheduler.pendingCount)
                val oldTransitionSequence = oldHost.lifecycleDiagnostics.transitionSequence

                activity.supportFragmentManager.beginTransaction()
                    .attach(fragment)
                    .commitNow()

                newHost = fragment.requireHostView()
                renderSynchronously(newHost)
                val newViewOwner = requireNotNull(newHost.findViewTreeLifecycleOwner())
                newState = tracker.states.last()

                assertNotSame(oldHost, newHost)
                assertNotSame(oldViewOwner, newViewOwner)
                assertNotSame(oldState, newState)
                assertSame(fragment.viewLifecycleOwner, newViewOwner)
                assertNotSame(activity, newViewOwner)
                assertEquals(2, tracker.states.size)
                assertEquals(0, newState.disposeCount)
                assertEquals(1, tracker.totalDisposeCount)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, newHost.lifecycleDiagnostics.ownerBinding)
                assertEquals(PixelHostLifecycleState.Resumed, newHost.lifecycleDiagnostics.lifecycleState)
                assertTrue(newHost.lifecycleDiagnostics.isInteractive)
                assertFalse(newHost.frameScopeDiagnostics.isDisposed)
                assertEquals(oldTransitionSequence, oldHost.lifecycleDiagnostics.transitionSequence)
            }
        }

        assertEquals(PixelHostLifecycleState.Destroyed, newHost.lifecycleDiagnostics.lifecycleState)
        assertTrue(newHost.frameScopeDiagnostics.isDisposed)
        assertEquals(1, newState.disposeCount)
        assertEquals(2, tracker.totalDisposeCount)
    }

    /** 两个同时 attached Host 的 scheduler、ticker、lifecycle 与 back handler 必须完全隔离。 */
    @Test
    fun twoAttachedHostsPauseAndDestroyIndependently() {
        val firstScheduler = ManualFrameScheduler()
        val secondScheduler = ManualFrameScheduler()
        val firstDispatcher = PixelBackDispatcher()
        val secondDispatcher = PixelBackDispatcher()
        var firstBackCount = 0
        var secondBackCount = 0
        var firstTickCount = 0
        var secondTickCount = 0
        lateinit var secondHost: PixelHostView
        lateinit var secondTicker: PixelTicker

        firstDispatcher.register {
            firstBackCount += 1
            true
        }
        secondDispatcher.register {
            secondBackCount += 1
            true
        }

        ActivityScenario.launch(PixelHostFragmentTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val firstHost = PixelHostView(activity).apply {
                    frameScheduler = firstScheduler
                    backDispatcher = firstDispatcher
                }
                secondHost = PixelHostView(activity).apply {
                    frameScheduler = secondScheduler
                    backDispatcher = secondDispatcher
                }
                activity.rootView.addView(firstHost, fullSizeLayoutParams())
                activity.rootView.addView(secondHost, fullSizeLayoutParams())

                val firstTicker = firstHost.tickerProvider.createTicker { firstTickCount += 1 }
                secondTicker = secondHost.tickerProvider.createTicker { secondTickCount += 1 }
                firstTicker.start()
                secondTicker.start()

                assertTrue(firstHost.isAttachedToWindow)
                assertTrue(secondHost.isAttachedToWindow)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, firstHost.lifecycleDiagnostics.ownerBinding)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, secondHost.lifecycleDiagnostics.ownerBinding)
                assertTrue(firstHost.lifecycleDiagnostics.isInteractive)
                assertTrue(secondHost.lifecycleDiagnostics.isInteractive)
                assertEquals(1, firstScheduler.pendingCount)
                assertEquals(1, secondScheduler.pendingCount)

                assertTrue(firstHost.handleBackPressed())
                assertEquals(1, firstBackCount)
                assertEquals(0, secondBackCount)
                assertTrue(secondHost.handleBackPressed())
                assertEquals(1, firstBackCount)
                assertEquals(1, secondBackCount)

                firstHost.pause()

                assertEquals(PixelHostLifecycleState.Paused, firstHost.lifecycleDiagnostics.lifecycleState)
                assertFalse(firstHost.lifecycleDiagnostics.isInteractive)
                assertTrue(firstHost.frameScopeDiagnostics.isPaused)
                assertEquals(0, firstScheduler.pendingCount)
                assertEquals(PixelHostLifecycleState.Resumed, secondHost.lifecycleDiagnostics.lifecycleState)
                assertTrue(secondHost.lifecycleDiagnostics.isInteractive)
                assertFalse(secondHost.frameScopeDiagnostics.isPaused)
                assertEquals(1, secondScheduler.pendingCount)
                assertFalse(firstHost.handleBackPressed())
                assertTrue(secondHost.handleBackPressed())
                assertEquals(1, firstBackCount)
                assertEquals(2, secondBackCount)

                secondScheduler.advanceFrame(16_000_000L)
                assertEquals(0, firstTickCount)
                assertEquals(1, secondTickCount)
                assertEquals(0, firstScheduler.pendingCount)
                assertEquals(1, secondScheduler.pendingCount)

                firstHost.destroy()

                assertEquals(PixelHostLifecycleState.Destroyed, firstHost.lifecycleDiagnostics.lifecycleState)
                assertTrue(firstHost.frameScopeDiagnostics.isDisposed)
                assertTrue(firstTicker.isDisposed)
                assertEquals(0, firstScheduler.pendingCount)
                assertNull(firstHost.backDispatcher)
                assertEquals(PixelHostLifecycleState.Resumed, secondHost.lifecycleDiagnostics.lifecycleState)
                assertFalse(secondHost.frameScopeDiagnostics.isDisposed)
                assertSame(secondDispatcher, secondHost.backDispatcher)
                assertTrue(secondHost.handleBackPressed())
                assertEquals(1, firstBackCount)
                assertEquals(3, secondBackCount)
            }
        }

        assertEquals(PixelHostLifecycleState.Destroyed, secondHost.lifecycleDiagnostics.lifecycleState)
        assertTrue(secondHost.frameScopeDiagnostics.isDisposed)
        assertTrue(secondTicker.isDisposed)
        assertEquals(0, secondScheduler.pendingCount)
    }

    /** 在 Activity 主线程同步构建一帧 retained tree。 */
    private fun renderSynchronously(host: PixelHostView) {
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }

    /** 创建覆盖测试容器的标准 Host 布局参数。 */
    private fun fullSizeLayoutParams(): ViewGroup.LayoutParams {
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private companion object {
        /** FragmentManager 中稳定定位 view lifecycle 测试 Fragment 的 tag。 */
        const val FRAGMENT_TAG: String = "pixel-host-view-lifecycle"
    }
}
