package com.purride.pixelui

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelViewportFit
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.PixelViewportQuantization
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.host.AndroidPixelHostCapabilitiesSource
import com.purride.pixelui.host.PixelHostCapabilitiesSource
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Android capability collection, Host overrides and observer lifecycle on API 24+. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelHostCapabilitiesSourceInstrumentedTest {
    /** Automatic Host values must agree with one atomic read of the current Android environment. */
    @Suppress("DEPRECATION")
    @Test
    fun automaticSnapshotMatchesCurrentAndroidConfigurationAndDisplay() {
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Attached production Host whose Android source has completed its initial read. */
                val host = activity.hostView
                /** Immutable engine projection exposed to the retained widget tree. */
                val actual = host.hostCapabilities
                /** Current Android configuration used as the independent expected-value source. */
                val configuration = activity.resources.configuration
                /** Ordered and deduplicated locale preference copied into public engine values. */
                val expectedLocales = buildList {
                    for (index in 0 until configuration.locales.size()) {
                        /** Canonical locale corresponding to one Android preference entry. */
                        val locale = PixelLocale(configuration.locales[index].toLanguageTag())
                        if (none { existing -> existing == locale }) add(locale)
                    }
                }.ifEmpty { listOf(PixelLocale.Default) }
                /** Positive display refresh rate expected from the Host-selected display. */
                val expectedRefreshRate = host.display?.refreshRate
                    ?.takeIf { value -> value.isFinite() && value > 0f }

                assertEquals(expectedLocales, actual.locales)
                assertEquals(
                    if (configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        TextDirection.RTL
                    } else {
                        TextDirection.LTR
                    },
                    actual.layoutDirection,
                )
                assertEquals(configuration.fontScale, actual.textScaleFactor, 0f)
                assertEquals(activity.resources.displayMetrics.density, actual.density, 0f)
                assertEquals(expectedRefreshRate, actual.refreshRateHz)
                assertEquals(readExpectedHighContrast(activity), actual.highContrast)
            }
        }
    }

    /** A fake source proves attach/detach, atomic override priority and retained State preservation. */
    @Test
    fun fakeSourceLifecycleAndOverridesPublishNewestAtomicSnapshotWithoutResettingState() {
        /** Source reference retained after Activity destruction for exact lifecycle assertions. */
        lateinit var source: RecordingHostCapabilitiesSource
        /** Retained widget probe shared across every environment replacement. */
        val tracker = CapabilitySourceProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Logical fold feature supplied without any Android WindowManager dependency. */
                val sourceFeature = PixelDisplayFeature(
                    bounds = PixelLogicalRect(left = 20f, top = 0f, right = 21f, bottom = 80f),
                    type = PixelDisplayFeatureType.HINGE,
                    state = PixelDisplayFeatureState.HALF_OPENED,
                )
                /** First complete automatic snapshot installed while the Host is attached. */
                val first = HostCapabilitiesData(
                    locales = listOf(PixelLocale("ar-EG"), PixelLocale("en-US")),
                    layoutDirection = TextDirection.RTL,
                    textScaleFactor = 1.5f,
                    highContrast = true,
                    density = 2.5f,
                    refreshRateHz = 120f,
                    displayFeatures = listOf(sourceFeature),
                )
                source = RecordingHostCapabilitiesSource(first)
                /** Real attached Host receiving the deterministic test source. */
                val host = activity.hostView
                host.setContent {
                    CapabilitySourceProbeWidget(
                        tracker = tracker,
                        key = "capability-source-retained-state",
                    )
                }
                renderSynchronously(host)
                /** State allocated before source replacement and expected to survive every update. */
                val originalState = tracker.states.single()

                host.replaceHostCapabilitiesSourceForTesting(source)
                renderSynchronously(host)

                assertEquals(1, source.attachCount)
                assertEquals(listOf(host.display?.displayId), source.attachedDisplayIds)
                assertAutomaticFields(first, host.hostCapabilities)
                assertTrue(host.hostCapabilities.displayFeatures.contains(sourceFeature))

                // 部分覆盖同样表达为完整快照：从当前快照派生后只改动方向字段。
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(layoutDirection = TextDirection.LTR)
                renderSynchronously(host)
                assertEquals(TextDirection.LTR, host.hostCapabilities.layoutDirection)
                assertEquals(first.textScaleFactor, host.hostCapabilities.textScaleFactor, 0f)
                assertTrue(host.hostCapabilities.displayFeatures.contains(sourceFeature))

                /** Complete application override that must suppress every automatic source field. */
                val completeOverride = HostCapabilitiesData(
                    locales = listOf(PixelLocale("ja-JP")),
                    layoutDirection = TextDirection.RTL,
                    textScaleFactor = 2f,
                    highContrast = false,
                    density = 3f,
                    refreshRateHz = 90f,
                )
                host.capabilitiesOverride = completeOverride
                renderSynchronously(host)
                assertSame(completeOverride, host.hostCapabilities)

                /** Newest automatic snapshot emitted while the complete override is authoritative. */
                val second = first.copy(
                    locales = listOf(PixelLocale("de-DE")),
                    layoutDirection = TextDirection.RTL,
                    textScaleFactor = 1.75f,
                    highContrast = false,
                    density = 3.25f,
                    refreshRateHz = 60f,
                )
                source.emit(second)
                renderSynchronously(host)
                assertSame(completeOverride, host.hostCapabilities)

                // 清除唯一覆盖入口后立即完整恢复最新自动快照，不保留任何独立的方向状态。
                host.capabilitiesOverride = null
                renderSynchronously(host)
                assertEquals(second.layoutDirection, host.hostCapabilities.layoutDirection)
                assertEquals(second.textScaleFactor, host.hostCapabilities.textScaleFactor, 0f)
                assertAutomaticFields(second, host.hostCapabilities)

                activity.rootView.removeView(host)
                assertEquals(1, source.detachCount)
                assertFalse(source.isAttached)

                /** Detached update retained by the source and published immediately on reattach. */
                val detachedLatest = second.copy(
                    locales = listOf(PixelLocale("fr-FR")),
                    textScaleFactor = 2.25f,
                    density = 4f,
                    refreshRateHz = 144f,
                )
                source.emit(detachedLatest)
                assertAutomaticFields(second, host.hostCapabilities)

                activity.rootView.addView(host)
                renderSynchronously(host)
                assertEquals(2, source.attachCount)
                assertAutomaticFields(detachedLatest, host.hostCapabilities)
                assertEquals(1, tracker.states.size)
                assertSame(originalState, tracker.states.single())
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, source.destroyCount)
        assertEquals(2, source.detachCount)
        assertEquals(1, tracker.disposeCount)
    }

    /** The production source rejects lifecycle and snapshot reads from a background thread. */
    @Test
    fun productionSourceEnforcesMainThreadContract() {
        /** Source constructed and later destroyed on the Android main thread. */
        lateinit var source: AndroidPixelHostCapabilitiesSource
        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                source = AndroidPixelHostCapabilitiesSource(activity)
            }
            /** Dedicated worker used to violate the documented source thread contract. */
            val executor = Executors.newSingleThreadExecutor()
            try {
                /** Background failure returned to the instrumentation thread without races. */
                val failure = executor.submit<Throwable?> {
                    runCatching { source.currentCapabilities }.exceptionOrNull()
                }.get(5, TimeUnit.SECONDS)
                assertTrue(failure is IllegalStateException)
                assertTrue(failure?.message.orEmpty().contains("main thread"))
            } finally {
                executor.shutdownNow()
            }
            scenario.onActivity {
                source.destroy()
            }
        }
    }

    /** Adaptive profiles react to size, density and viewport strategy without replacing State. */
    @Test
    fun adaptiveProfilePoliciesReevaluateEnvironmentAndManualProfileRestoresFixedMode() {
        /** Retained widget identity shared across every profile transition. */
        val tracker = CapabilitySourceProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Deterministic two-density capability snapshot used by AdaptiveDp. */
                val initialCapabilities = HostCapabilitiesData.Default.copy(density = 2f)
                /** Fake source allowing a later density-only update. */
                val source = RecordingHostCapabilitiesSource(initialCapabilities)
                /** Attached Host with deterministic physical dimensions. */
                val host = activity.hostView
                host.layout(0, 0, 160, 80)
                host.setContent {
                    CapabilitySourceProbeWidget(
                        tracker = tracker,
                        key = "adaptive-profile-retained-state",
                    )
                }
                renderSynchronously(host)
                /** State allocated before any profile policy replacement. */
                val originalState = tracker.states.single()

                host.replaceHostCapabilitiesSourceForTesting(source)
                host.profilePolicy = PixelHostProfilePolicy.AdaptiveDp(dotSizeDp = 4f)
                renderSynchronously(host)
                assertEquals(ScreenProfile(20, 10, 8), host.screenProfile)

                source.emit(initialCapabilities.copy(density = 4f))
                renderSynchronously(host)
                assertEquals(ScreenProfile(10, 5, 16), host.screenProfile)

                host.viewportPolicy = PixelViewportPolicy(
                    fit = PixelViewportFit.CONTAIN,
                    quantization = PixelViewportQuantization.FRACTIONAL,
                )
                host.profilePolicy = PixelHostProfilePolicy.AdaptiveLogicalSize(
                    logicalWidth = 40,
                    logicalHeight = 20,
                    pixelShape = PixelShape.DIAMOND,
                )
                host.layout(0, 0, 100, 80)
                renderSynchronously(host)
                assertEquals(ScreenProfile(40, 20, 3, PixelShape.DIAMOND), host.screenProfile)

                host.viewportPolicy = PixelViewportPolicy(
                    fit = PixelViewportFit.COVER,
                    quantization = PixelViewportQuantization.INTEGER,
                )
                renderSynchronously(host)
                assertEquals(ScreenProfile(40, 20, 4, PixelShape.DIAMOND), host.screenProfile)

                host.profilePolicy = PixelHostProfilePolicy.AdaptivePixels(
                    dotSizePx = 10,
                    pixelShape = PixelShape.CIRCLE,
                )
                renderSynchronously(host)
                assertEquals(ScreenProfile(10, 8, 10, PixelShape.CIRCLE), host.screenProfile)

                /** 固定策略必须让后续所有自适应重算失效。 */
                val manual = ScreenProfile(
                    logicalWidth = 7,
                    logicalHeight = 9,
                    dotSizePx = 3,
                    pixelShape = PixelShape.SQUARE,
                )
                host.profilePolicy = PixelHostProfilePolicy.Fixed(manual)
                host.layout(0, 0, 200, 160)
                renderSynchronously(host)

                assertEquals(PixelHostProfilePolicy.Fixed(manual), host.profilePolicy)
                assertEquals(manual, host.screenProfile)
                assertEquals(1, tracker.states.size)
                assertSame(originalState, tracker.states.single())
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, tracker.disposeCount)
    }

    /** 真实 Host 通过 AdaptiveBuilder 发布物理 dp、逻辑 inset 和注入的折叠特征。 */
    @Test
    fun hostAdaptiveBuilderPublishesAtomicResizeInsetDensityAndDisplayFeatureUpdates() {
        /** Adaptive snapshots emitted by the public builder callback. */
        val snapshots = mutableListOf<PixelAdaptiveLayoutData>()
        /** Retained child identity shared across environment updates. */
        val tracker = CapabilitySourceProbeTracker()

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Initial injected fold independent from Android WindowManager availability. */
                val fold = PixelDisplayFeature(
                    bounds = PixelLogicalRect(left = 4f, top = 0f, right = 5f, bottom = 8f),
                    type = PixelDisplayFeatureType.FOLD,
                    state = PixelDisplayFeatureState.FLAT,
                )
                /** Deterministic capability source supplying density and fold metadata. */
                val source = RecordingHostCapabilitiesSource(
                    HostCapabilitiesData.Default.copy(
                        density = 2f,
                        displayFeatures = listOf(fold),
                    ),
                )
                /** Attached Host driven through real size and inherited-root code paths. */
                val host = activity.hostView
                host.layout(0, 0, 100, 80)
                host.profilePolicy = PixelHostProfilePolicy.Fixed(ScreenProfile(logicalWidth = 10, logicalHeight = 8, dotSizePx = 10))
                host.windowInsets = PixelWindowInsets(top = 2, right = 1)
                host.viewInsets = PixelWindowInsets(bottom = 3)
                host.replaceHostCapabilitiesSourceForTesting(source)
                host.setContent {
                    AdaptiveBuilder(
                        builder = { _, data ->
                            snapshots += data
                            SafeArea(
                                child = CapabilitySourceProbeWidget(
                                    tracker = tracker,
                                    key = "host-adaptive-retained-child",
                                ),
                            )
                        },
                        key = "host-adaptive-builder",
                    )
                }
                renderSynchronously(host)
                /** State allocated below SafeArea and AdaptiveBuilder during the first frame. */
                val originalState = tracker.states.single()
                /** First atomic adaptive snapshot. */
                val initial = snapshots.single()

                assertEquals(100, initial.physicalWidthPx)
                assertEquals(80, initial.physicalHeightPx)
                assertEquals(50f, initial.widthDp, 0f)
                assertEquals(40f, initial.heightDp, 0f)
                assertEquals(PixelWindowOrientation.LANDSCAPE, initial.orientation)
                assertEquals(10, initial.logicalWidth)
                assertEquals(8, initial.logicalHeight)
                assertEquals(PixelWindowInsets(top = 2, right = 1), initial.viewPadding)
                assertEquals(PixelWindowInsets(bottom = 3), initial.viewInsets)
                /** 首帧中由测试 source 注入的唯一折叠特征；平台 cutout 可以同时存在。 */
                val initialFold = initial.displayFeatures.single { feature ->
                    feature.type == PixelDisplayFeatureType.FOLD
                }
                assertEquals(fold, initialFold)

                /** Second injected hinge and density snapshot combined with portrait resize and IME. */
                val hinge = PixelDisplayFeature(
                    bounds = PixelLogicalRect(left = 0f, top = 7f, right = 10f, bottom = 8f),
                    type = PixelDisplayFeatureType.HINGE,
                    state = PixelDisplayFeatureState.HALF_OPENED,
                )
                source.emit(
                    HostCapabilitiesData.Default.copy(
                        density = 1f,
                        displayFeatures = listOf(hinge),
                    ),
                )
                host.layout(0, 0, 90, 160)
                host.windowInsets = PixelWindowInsets(left = 1, top = 3)
                host.viewInsets = PixelWindowInsets(bottom = 6)
                renderSynchronously(host)
                /** Final atomic adaptive snapshot after all coalesced Host changes. */
                val updated = snapshots.last()

                assertEquals(90, updated.physicalWidthPx)
                assertEquals(160, updated.physicalHeightPx)
                assertEquals(90f, updated.widthDp, 0f)
                assertEquals(160f, updated.heightDp, 0f)
                assertEquals(PixelWindowOrientation.PORTRAIT, updated.orientation)
                assertEquals(PixelWindowInsets(left = 1, top = 3), updated.viewPadding)
                assertEquals(PixelWindowInsets(bottom = 6), updated.viewInsets)
                /** 更新帧中由测试 source 注入的唯一铰链特征；平台 cutout 可以同时存在。 */
                val updatedHinge = updated.displayFeatures.single { feature ->
                    feature.type == PixelDisplayFeatureType.HINGE
                }
                assertEquals(hinge, updatedHinge)
                assertEquals(1, tracker.states.size)
                assertSame(originalState, tracker.states.single())
                assertEquals(0, tracker.disposeCount)
            }
        }

        assertEquals(1, tracker.disposeCount)
    }

    /** Compares every Android-owned automatic field while excluding Host-owned motion projection. */
    private fun assertAutomaticFields(
        /** Complete expected source snapshot. */
        expected: HostCapabilitiesData,
        /** Effective Host snapshot after override and source merging. */
        actual: HostCapabilitiesData,
    ) {
        assertEquals(expected.locales, actual.locales)
        assertEquals(expected.layoutDirection, actual.layoutDirection)
        assertEquals(expected.textScaleFactor, actual.textScaleFactor, 0f)
        assertEquals(expected.highContrast, actual.highContrast)
        assertEquals(expected.density, actual.density, 0f)
        assertEquals(expected.refreshRateHz, actual.refreshRateHz)
    }

    /** Draws one synchronous frame so inherited capability invalidation reaches the probe State. */
    private fun renderSynchronously(host: PixelHostView) {
        /** Temporary bitmap backing the real Android View draw. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        bitmap.recycle()
    }
}

/** Deterministic source with exact lifecycle counters and detached-value retention. */
private class RecordingHostCapabilitiesSource(
    initialCapabilities: HostCapabilitiesData,
) : PixelHostCapabilitiesSource {
    /** Latest immutable snapshot returned on read and immediate attachment. */
    private var snapshot: HostCapabilitiesData = initialCapabilities

    /** Active callback retained only inside an attached interval. */
    private var callback: ((HostCapabilitiesData) -> Unit)? = null

    /** Number of distinct attached intervals. */
    var attachCount: Int = 0
        private set

    /** Number of distinct detached intervals, including terminal destruction. */
    var detachCount: Int = 0
        private set

    /** Number of terminal destruction transitions. */
    var destroyCount: Int = 0
        private set

    /** Display ids supplied for each distinct Host attachment. */
    val attachedDisplayIds: MutableList<Int?> = mutableListOf()

    /** Whether this fake currently owns an active Host callback. */
    var isAttached: Boolean = false
        private set

    /** Whether the fake has reached its irreversible terminal state. */
    private var destroyed: Boolean = false

    /** Returns the latest snapshot even while detached so reattachment starts from current state. */
    override val currentCapabilities: HostCapabilitiesData
        get() = snapshot

    /** Starts one interval and immediately publishes the current complete snapshot. */
    override fun attach(onChanged: (HostCapabilitiesData) -> Unit, displayId: Int?) {
        if (destroyed) return
        callback = onChanged
        if (!isAttached) {
            isAttached = true
            attachCount += 1
            attachedDisplayIds += displayId
        }
        onChanged(snapshot)
    }

    /** Records no extra event because display selection is not relevant to this deterministic fake. */
    override fun updateDisplay(displayId: Int?) = Unit

    /** Re-publishes only through [emit], keeping refresh deterministic for assertions. */
    override fun refresh() = Unit

    /** Ends the active interval exactly once while retaining [snapshot]. */
    override fun detach() {
        callback = null
        if (!isAttached) return
        isAttached = false
        detachCount += 1
    }

    /** Performs terminal detach and rejects all later attachments. */
    override fun destroy() {
        if (destroyed) return
        detach()
        destroyed = true
        destroyCount += 1
    }

    /** Stores [capabilities] while detached or synchronously publishes it while attached. */
    fun emit(capabilities: HostCapabilitiesData) {
        snapshot = capabilities
        if (isAttached) callback?.invoke(capabilities)
    }
}

/** Tracks capability-dependent builds and the identity of the single retained State. */
private class CapabilitySourceProbeTracker {
    /** State instances allocated throughout the test. */
    val states: MutableList<CapabilitySourceProbeState> = mutableListOf()

    /** Complete snapshots observed during dependency-driven rebuilds. */
    val observations: MutableList<HostCapabilitiesData> = mutableListOf()

    /** Number of terminal State releases. */
    var disposeCount: Int = 0
}

/** Stable keyed widget whose State must survive source and override changes. */
private class CapabilitySourceProbeWidget(
    /** Shared lifecycle and snapshot tracker. */
    val tracker: CapabilitySourceProbeTracker,
    key: Any,
) : StatefulWidget(key = key) {
    /** Creates the sole retained probe State. */
    override fun createState(): State<out StatefulWidget> = CapabilitySourceProbeState()
}

/** Retained consumer subscribing to the complete inherited Host capability snapshot. */
private class CapabilitySourceProbeState : State<CapabilitySourceProbeWidget>() {
    /** Records the exact State identity at first mount. */
    override fun initState() {
        widget.tracker.states += this
    }

    /** Subscribes to [HostCapabilities] and records one atomic snapshot per rebuild. */
    override fun build(context: BuildContext): Widget {
        widget.tracker.observations += HostCapabilities.of(context)
        return Container(
            width = 4,
            height = 4,
            fillColor = PixelColor.White,
            borderColor = null,
        )
    }

    /** Records terminal release when the Activity destroys the Host. */
    override fun dispose() {
        widget.tracker.disposeCount += 1
    }
}

/** Reads the public contrast signal selected by the same Android-version policy as production. */
@SuppressLint("NewApi")
private fun readExpectedHighContrast(context: Context): Boolean {
    return when {
        Build.VERSION.SDK_INT >= 36 -> Api36ExpectedHighContrast.read(context)
        Build.VERSION.SDK_INT >= 34 -> Api34ExpectedHighContrast.read(context)
        else -> false
    }
}

/** API 34–35 contrast reader isolated from API 24 instrumentation class verification. */
@RequiresApi(34)
private object Api34ExpectedHighContrast {
    /** Returns whether Android requests a positive UI contrast adjustment. */
    fun read(context: Context): Boolean {
        /** UI mode service exposing the normalized contrast preference. */
        val manager = checkNotNull(context.getSystemService(UiModeManager::class.java))
        return manager.contrast > 0f
    }
}

/** API 36+ high-contrast-text reader isolated from older runtime class verification. */
@RequiresApi(36)
private object Api36ExpectedHighContrast {
    /** Returns Android's explicit accessibility high-contrast-text preference. */
    fun read(context: Context): Boolean {
        /** Accessibility service exposing the high-contrast-text state. */
        val manager = checkNotNull(context.getSystemService(AccessibilityManager::class.java))
        return manager.isHighContrastTextEnabled
    }
}
