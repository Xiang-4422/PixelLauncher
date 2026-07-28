package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelViewportAlignment
import com.purride.pixelcore.PixelViewportFit
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelViewportQuantization
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.host.ManualFrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API 24+ ActivityScenario 上的 Host attachment 与 owner 终态验收。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostLifecycleInstrumentedTest {
    /** detached Host 初始冻结 frame scope，替换 scheduler 会 dispose 旧 provider。 */
    @Test
    fun detachedHostOwnsPausedScopeAndSchedulerReplacementDisposesOldProvider() {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val detachedHost = PixelHostView(activity)
                val oldProvider = detachedHost.tickerProvider

                assertEquals(PixelHostAttachmentState.Detached, detachedHost.lifecycleDiagnostics.attachmentState)
                assertTrue(detachedHost.frameScopeDiagnostics.isPaused)
                assertFalse(detachedHost.frameScopeDiagnostics.isDisposed)

                detachedHost.frameScheduler = ManualFrameScheduler()

                assertTrue(oldProvider.diagnostics().isDisposed)
                assertTrue(detachedHost.frameScopeDiagnostics.isPaused)
                assertFalse(detachedHost.frameScopeDiagnostics.isDisposed)

                detachedHost.destroy()

                assertTrue(detachedHost.frameScopeDiagnostics.isDisposed)
            }
        }
    }

    /** repeated detach/attach 保留同一 retained State，且只切换 attachment 轴。 */
    @Test
    fun repeatedDetachAttachPreservesRetainedTreeAndOwnerState() {
        val tracker = LifecycleProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.hostView
                host.setContent { LifecycleProbeWidget(tracker) }
                renderSynchronously(host)
            }

            scenario.onActivity { activity ->
                val host = activity.hostView
                assertEquals(1, tracker.initCount)
                assertEquals(PixelHostAttachmentState.Attached, host.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                assertTrue(host.lifecycleDiagnostics.isInteractive)
                assertFalse(host.frameScopeDiagnostics.isPaused)
                assertFalse(host.frameScopeDiagnostics.isDisposed)

                repeat(2) {
                    activity.rootView.removeView(host)

                    assertEquals(PixelHostAttachmentState.Detached, host.lifecycleDiagnostics.attachmentState)
                    assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                    assertFalse(host.lifecycleDiagnostics.isInteractive)
                    assertTrue(host.frameScopeDiagnostics.isPaused)
                    assertFalse(host.frameScopeDiagnostics.isDisposed)
                    assertEquals(0, tracker.disposeCount)

                    activity.rootView.addView(host)
                    renderSynchronously(host)

                    assertEquals(PixelHostAttachmentState.Attached, host.lifecycleDiagnostics.attachmentState)
                    assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                    assertTrue(host.lifecycleDiagnostics.isInteractive)
                    assertFalse(host.frameScopeDiagnostics.isPaused)
                    assertFalse(host.frameScopeDiagnostics.isDisposed)
                    assertEquals(1, tracker.initCount)
                    assertEquals(0, tracker.disposeCount)
                }

                assertEquals(PixelHostAttachmentState.Attached, host.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                assertTrue(host.lifecycleDiagnostics.isInteractive)
                assertEquals(3L, host.lifecycleDiagnostics.attachCount)
                assertEquals(2L, host.lifecycleDiagnostics.detachCount)
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, tracker.disposeCount)
    }

    /**
     * Pause must coalesce capability replacements and publish only the final atomic snapshot.
     *
     * Synchronous draws after every paused replacement deliberately exercise the render entry
     * point; none may rebuild the dependent subtree until resume. The resumed frame must reuse
     * the original retained State and must never expose either intermediate snapshot.
     */
    @Test
    fun pausedHostPublishesOnlyFinalCapabilitySnapshotAndRetainsState() {
        /** Shared probe records every mounted State and capability value observed during build. */
        val tracker = CapabilityLifecycleProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real attached Host exercises lifecycle gating and the production root wrapper. */
                val host = activity.hostView
                host.setContent {
                    CapabilityLifecycleProbeWidget(
                        tracker = tracker,
                        key = "capability-lifecycle-probe",
                    )
                }
                renderSynchronously(host)

                /** State mounted by the initial fallback capability snapshot. */
                val originalState = tracker.states.single()
                assertEquals(
                    listOf(CapabilityObservation(textScaleFactor = 1f, direction = TextDirection.LTR)),
                    tracker.observations,
                )

                host.pause()
                assertFalse(host.lifecycleDiagnostics.isInteractive)

                /** First intermediate value must remain invisible while rendering is paused. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 1.25f,
                    layoutDirection = TextDirection.RTL,
                )
                renderSynchronously(host)

                /** Second intermediate value proves repeated replacement is coalesced. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 1.5f,
                    layoutDirection = TextDirection.LTR,
                    highContrast = true,
                )
                renderSynchronously(host)

                /** Final value is the only override allowed to reach dependents after resume. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 2f,
                    layoutDirection = TextDirection.RTL,
                    highContrast = true,
                )
                renderSynchronously(host)

                assertSame(originalState, tracker.states.single())
                assertEquals(1, tracker.observations.size)
                assertEquals(0, tracker.disposeCount)

                host.resume()
                assertTrue(host.lifecycleDiagnostics.isInteractive)
                renderSynchronously(host)

                assertEquals(1, tracker.states.size)
                assertSame(originalState, tracker.states.single())
                /** Initial and final are the only distinct snapshots permitted across the gate. */
                val initialObservation = CapabilityObservation(
                    textScaleFactor = 1f,
                    direction = TextDirection.LTR,
                )
                /** Repeated final-state builds are harmless; no intermediate value may reappear. */
                val finalObservation = CapabilityObservation(
                    textScaleFactor = 2f,
                    direction = TextDirection.RTL,
                    highContrast = true,
                )
                assertEquals(
                    listOf(initialObservation, finalObservation),
                    tracker.observations.distinct(),
                )
                assertTrue(tracker.observations.drop(1).all { observation -> observation == finalObservation })
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, tracker.disposeCount)
    }

    /**
     * Detach must coalesce capability replacements and publish only the final snapshot on reattach.
     *
     * Unlike the pause gate, this keeps the owner lifecycle resumed while removing the View from
     * its Window. Forced detached draws must return the cached frame without rebuilding, and the
     * original retained State must remain mounted when the same View is attached again.
     */
    @Test
    fun detachedHostPublishesOnlyFinalCapabilitySnapshotAndRetainsState() {
        /** Shared probe records every State identity and capability projection across attachment. */
        val tracker = CapabilityLifecycleProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Existing Activity Host is removed and re-added without creating a replacement. */
                val host = activity.hostView
                host.setContent {
                    CapabilityLifecycleProbeWidget(
                        tracker = tracker,
                        key = "detached-capability-lifecycle-probe",
                    )
                }
                renderSynchronously(host)

                /** Initial retained identity must survive the complete detach/reattach sequence. */
                val originalState = tracker.states.single()
                assertEquals(
                    listOf(CapabilityObservation(textScaleFactor = 1f, direction = TextDirection.LTR)),
                    tracker.observations,
                )

                activity.rootView.removeView(host)
                assertEquals(PixelHostAttachmentState.Detached, host.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                assertFalse(host.lifecycleDiagnostics.isInteractive)

                /** First detached intermediate value must not rebuild the capability consumer. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 1.2f,
                    layoutDirection = TextDirection.RTL,
                )
                renderSynchronously(host)

                /** Second detached intermediate value exercises repeated replacement coalescing. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 1.6f,
                    layoutDirection = TextDirection.LTR,
                    highContrast = true,
                )
                renderSynchronously(host)

                /** Final detached value is the only override permitted after reattachment. */
                host.capabilitiesOverride = HostCapabilitiesData(
                    textScaleFactor = 2.25f,
                    layoutDirection = TextDirection.RTL,
                    highContrast = true,
                )
                renderSynchronously(host)

                assertSame(originalState, tracker.states.single())
                assertEquals(1, tracker.observations.size)
                assertEquals(0, tracker.disposeCount)

                activity.rootView.addView(host)
                assertEquals(PixelHostAttachmentState.Attached, host.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, host.lifecycleDiagnostics.lifecycleState)
                assertTrue(host.lifecycleDiagnostics.isInteractive)
                renderSynchronously(host)

                /** Initial and final are the sole distinct snapshots visible across attachment. */
                val initialObservation = CapabilityObservation(
                    textScaleFactor = 1f,
                    direction = TextDirection.LTR,
                )
                /** Every post-reattach build must agree on this final atomic projection. */
                val finalObservation = CapabilityObservation(
                    textScaleFactor = 2.25f,
                    direction = TextDirection.RTL,
                    highContrast = true,
                )
                assertEquals(1, tracker.states.size)
                assertSame(originalState, tracker.states.single())
                assertEquals(
                    listOf(initialObservation, finalObservation),
                    tracker.observations.distinct(),
                )
                assertTrue(tracker.observations.drop(1).all { observation -> observation == finalObservation })
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, tracker.disposeCount)
    }

    /** Raw physical insets are reprojected after policy and logical-profile changes. */
    @Test
    fun physicalInsetsReprojectAcrossViewportAndProfileChanges() {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real attached Host receiving deterministic physical dimensions. */
                val host = activity.hostView
                host.layout(0, 0, 100, 80)
                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(
                    logicalWidth = 10,
                    logicalHeight = 5,
                    dotSizePx = 8,
                ))
                host.viewportPolicy = PixelViewportPolicy(
                    fit = PixelViewportFit.COVER,
                    quantization = PixelViewportQuantization.INTEGER,
                    alignment = PixelViewportAlignment.TOP_RIGHT,
                )
                /** Mutable source rectangle proving the Host takes defensive cutout copies. */
                val sourceCutout = Rect(4, 0, 20, 8)

                host.applyRawPlatformInsetsForTesting(
                    windowInsets = PixelPhysicalInsets(right = 17),
                    viewInsets = PixelPhysicalInsets(bottom = 32),
                    cutoutBounds = listOf(sourceCutout),
                )
                sourceCutout.setEmpty()

                assertEquals(PixelWindowInsets(left = 4, right = 2), host.windowInsets)
                assertEquals(PixelWindowInsets(bottom = 2), host.viewInsets)
                assertEquals(Rect(4, 0, 20, 8), host.rawDisplayCutoutBoundsForCapabilities().single())
                assertEquals(
                    PixelDisplayFeature(
                        bounds = PixelLogicalRect(left = 4f, top = 0f, right = 5f, bottom = 0.5f),
                        type = PixelDisplayFeatureType.CUTOUT,
                        state = PixelDisplayFeatureState.UNKNOWN,
                    ),
                    host.hostCapabilities.displayFeatures.single(),
                )

                host.viewportPolicy = PixelViewportPolicy(
                    fit = PixelViewportFit.CONTAIN,
                    quantization = PixelViewportQuantization.INTEGER,
                    alignment = PixelViewportAlignment.BOTTOM_RIGHT,
                )

                assertEquals(PixelWindowInsets(right = 2), host.windowInsets)
                assertEquals(PixelWindowInsets(bottom = 4), host.viewInsets)

                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(
                    logicalWidth = 20,
                    logicalHeight = 10,
                    dotSizePx = 4,
                ))

                assertEquals(PixelWindowInsets(right = 4), host.windowInsets)
                assertEquals(PixelWindowInsets(bottom = 7), host.viewInsets)
            }
        }
    }

    /** Activity recreate 销毁旧 owner/Host，并让新 Host 自动绑定新 ViewTree owner。 */
    @Test
    fun ownerRecreateDestroysOldHostAndBindsNewOwner() {
        val oldTracker = LifecycleProbeTracker()
        val newTracker = LifecycleProbeTracker()
        lateinit var oldHost: PixelHostView
        lateinit var newHost: PixelHostView

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                oldHost = activity.hostView
                oldHost.setContent { LifecycleProbeWidget(oldTracker) }
                renderSynchronously(oldHost)
            }
            assertEquals(1, oldTracker.initCount)

            scenario.recreate()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                newHost = activity.hostView
                assertNotSame(oldHost, newHost)
                assertEquals(PixelHostLifecycleState.Destroyed, oldHost.lifecycleDiagnostics.lifecycleState)
                assertEquals(PixelHostLifecycleOwnerBinding.None, oldHost.lifecycleDiagnostics.ownerBinding)
                assertEquals(1L, oldHost.lifecycleDiagnostics.destroyCount)
                assertEquals(1, oldTracker.disposeCount)
                assertTrue(oldHost.frameScopeDiagnostics.isDisposed)

                assertEquals(PixelHostAttachmentState.Attached, newHost.lifecycleDiagnostics.attachmentState)
                assertEquals(PixelHostLifecycleState.Resumed, newHost.lifecycleDiagnostics.lifecycleState)
                assertEquals(PixelHostLifecycleOwnerBinding.ViewTree, newHost.lifecycleDiagnostics.ownerBinding)
                assertTrue(newHost.lifecycleDiagnostics.isInteractive)
                assertFalse(newHost.frameScopeDiagnostics.isPaused)
                assertFalse(newHost.frameScopeDiagnostics.isDisposed)
                newHost.setContent { LifecycleProbeWidget(newTracker) }
                renderSynchronously(newHost)
            }
            assertEquals(1, newTracker.initCount)
        }

        assertEquals(PixelHostLifecycleState.Destroyed, newHost.lifecycleDiagnostics.lifecycleState)
        assertEquals(1L, newHost.lifecycleDiagnostics.destroyCount)
        assertTrue(newHost.frameScopeDiagnostics.isDisposed)
        assertEquals(1, newTracker.disposeCount)
    }

    /** 在 Activity 主线程直接绘制一帧，避免测试结果依赖下一次异步 vsync。 */
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

/** 记录 instrumentation retained State 的创建与终态释放次数。 */
private class LifecycleProbeTracker {
    /** Probe State 完成 initState 的次数。 */
    var initCount: Int = 0

    /** Probe State 完成 terminal dispose 的次数。 */
    var disposeCount: Int = 0
}

/** 为 lifecycle instrumentation 提供一个可观察终态释放的 retained widget。 */
private class LifecycleProbeWidget(
    /** 该 widget State 写入的共享计数器。 */
    val tracker: LifecycleProbeTracker,
) : StatefulWidget() {
    /** 创建唯一的 retained probe State。 */
    override fun createState(): State<out StatefulWidget> = LifecycleProbeState()
}

/** 在真实 Host runtime 内记录 mount 与 dispose 的 State。 */
private class LifecycleProbeState : State<LifecycleProbeWidget>() {
    /** 记录首次 mount。 */
    override fun initState() {
        widget.tracker.initCount += 1
    }

    /** 构建一个足以驱动 retained runtime mount 的固定像素节点。 */
    override fun build(context: BuildContext): Widget {
        return Container(
            width = 4,
            height = 4,
            fillColor = PixelColor.White,
            borderColor = null,
        )
    }

    /** 记录 owner destroy 触发的 terminal retained-tree 释放。 */
    override fun dispose() {
        widget.tracker.disposeCount += 1
    }
}

/** Capability fields relevant to deterministic pause/resume publication assertions. */
private data class CapabilityObservation(
    /** Text scale observed by the retained dependent during build. */
    val textScaleFactor: Float,
    /** Layout direction observed from the same atomic capability snapshot. */
    val direction: TextDirection,
    /** High-contrast preference observed from the same atomic capability snapshot. */
    val highContrast: Boolean = false,
)

/** Records capability-dependent builds and retained State lifecycle events. */
private class CapabilityLifecycleProbeTracker {
    /** Every State allocation in mount order; a pause/resume cycle must keep exactly one. */
    val states: MutableList<CapabilityLifecycleProbeState> = mutableListOf()

    /** Atomic capability projections observed by the dependent subtree in build order. */
    val observations: MutableList<CapabilityObservation> = mutableListOf()

    /** Terminal State releases; pause and resume must not increment this value. */
    var disposeCount: Int = 0
}

/** Stable keyed widget whose State depends on [HostCapabilities]. */
private class CapabilityLifecycleProbeWidget(
    /** Shared lifecycle and observation sink owned by the instrumentation test. */
    val tracker: CapabilityLifecycleProbeTracker,
    key: Any,
) : StatefulWidget(key = key) {
    /** Creates the single State expected to survive paused capability replacements. */
    override fun createState(): State<out StatefulWidget> = CapabilityLifecycleProbeState()
}

/** Retained capability consumer used to detect intermediate snapshot publication. */
private class CapabilityLifecycleProbeState : State<CapabilityLifecycleProbeWidget>() {
    /** Exposes this exact State identity when it first mounts. */
    override fun initState() {
        widget.tracker.states += this
    }

    /** Records one projection from the single capability snapshot inherited by this build. */
    override fun build(context: BuildContext): Widget {
        /** 原子快照必须自身提供全部记录字段，不再回落到其他继承 scope。 */
        val capabilities = HostCapabilities.of(context)
        widget.tracker.observations += CapabilityObservation(
            textScaleFactor = capabilities.textScaleFactor,
            direction = capabilities.layoutDirection,
            highContrast = capabilities.highContrast,
        )
        return Container(
            width = 4,
            height = 4,
            fillColor = PixelColor.White,
            borderColor = null,
        )
    }

    /** Records the sole terminal release after ActivityScenario closes. */
    override fun dispose() {
        widget.tracker.disposeCount += 1
    }
}
