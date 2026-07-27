package com.purride.pixelui.regression

import com.purride.pixelui.AsyncBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelAsyncSource
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.host.PixelHostFrameScope
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.PixelRouteTransition
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic resource soak for complete retained runtime and Host-like route lifecycles.
 *
 * Every cycle allocates a fresh runtime and Host-owned frame scope, mounts a real Navigator route,
 * creates one AsyncBuilder subscription and one active Host-owned ticker, dispatches a frame, and
 * performs terminal disposal. Assertions use synchronous counters and primitive diagnostics only;
 * no garbage collection or weak-reference behavior participates in acceptance.
 */
class EngineResourceLifecycleStressTest {
    /** Verifies 10,000 complete Host/route cycles leave every owned resource count at zero. */
    @Test
    fun tenThousandRetainedHostAndRouteCyclesReleaseAllOwnedResources() {
        // One source scheduler models the platform frame source shared by successive Host views.
        val scheduler = ManualFrameScheduler()
        // Counters retain only primitive totals across cycles and never retain runtime objects.
        val counters = ResourceStressCounters()
        // Shared source proves each mounted AsyncBuilder owns an independent subscription lifetime.
        val asyncSource = CountingAsyncSource(counters)

        // Measured work includes construction, retained build/layout/paint, diagnostics, and cleanup.
        val elapsedNanos = measureNanoTime {
            repeat(StressBatchCount) { batchIndex ->
                repeat(StressBatchSize) { cycleInBatch ->
                    // Stable global cycle index makes every route and frame timestamp unique.
                    val cycleIndex = batchIndex * StressBatchSize + cycleInBatch
                    // Each cycle owns an isolated Host-like callback/ticker lifetime boundary.
                    val frameScope = PixelHostFrameScope(scheduler)
                    // Each cycle owns a new retained Element and RenderObject runtime.
                    val runtime = PixelUiRuntime()
                    // Root tree includes Navigator, route lifecycle, subscription, and owned ticker.
                    val root = buildCycleRoot(
                        cycleIndex = cycleIndex,
                        frameScope = frameScope,
                        asyncSource = asyncSource,
                        counters = counters,
                    )

                    runtime.render(
                        root = root,
                        logicalWidth = StressLogicalWidth,
                        logicalHeight = StressLogicalHeight,
                    )

                    // Mounted diagnostics prove this iteration exercised a real retained tree.
                    val mountedRuntime = runtime.collectResourceDiagnostics()
                    // Provider diagnostics prove the ticker is live and requests a Host frame.
                    val mountedTicker = frameScope.tickerProvider.diagnostics()
                    if (
                        mountedRuntime.hasRetainedElementRoot &&
                        mountedRuntime.retainedElementCount > 0 &&
                        mountedRuntime.retainedListenableDependencyCount > 0 &&
                        mountedRuntime.pendingDirtyElementCount == 0 &&
                        mountedRuntime.attachedRenderObjectCount > 0
                    ) {
                        counters.mountedRuntimeCycleCount += 1
                    }
                    if (
                        mountedTicker.activeTickerCount == 1 &&
                        mountedTicker.liveTickerCount == 1 &&
                        mountedTicker.pendingFrameCallbackCount == 1 &&
                        mountedTicker.createdTickerCount == 1L &&
                        mountedTicker.disposedTickerCount == 0L &&
                        scheduler.pendingCount == 1 &&
                        asyncSource.activeSubscriptionCount == 1
                    ) {
                        counters.mountedOwnedResourceCycleCount += 1
                    }

                    // One real source frame exercises ticker dispatch and re-scheduling.
                    scheduler.advanceFrame((cycleIndex + 1L) * StressFrameIntervalNanos)
                    runtime.dispose()

                    // Post-dispose runtime snapshot must be empty before its local reference expires.
                    val disposedRuntime = runtime.collectResourceDiagnostics()
                    counters.retainedElementCountAfterDispose +=
                        disposedRuntime.retainedElementCount.toLong()
                    counters.retainedListenableCountAfterDispose +=
                        disposedRuntime.retainedListenableDependencyCount.toLong()
                    counters.pendingDirtyCountAfterDispose +=
                        disposedRuntime.pendingDirtyElementCount.toLong()
                    counters.attachedRenderCountAfterDispose +=
                        disposedRuntime.attachedRenderObjectCount.toLong()
                    if (
                        !disposedRuntime.hasRetainedElementRoot &&
                        disposedRuntime.retainedElementCount == 0 &&
                        disposedRuntime.retainedListenableDependencyCount == 0 &&
                        disposedRuntime.pendingDirtyElementCount == 0 &&
                        disposedRuntime.attachedRenderObjectCount == 0
                    ) {
                        counters.cleanRuntimeCycleCount += 1
                    }

                    // Runtime disposal must release the widget-owned ticker before Host scope cleanup.
                    val tickerAfterRuntimeDispose = frameScope.tickerProvider.diagnostics()
                    if (
                        tickerAfterRuntimeDispose.activeTickerCount == 0 &&
                        tickerAfterRuntimeDispose.liveTickerCount == 0 &&
                        tickerAfterRuntimeDispose.pendingFrameCallbackCount == 0 &&
                        tickerAfterRuntimeDispose.createdTickerCount == 1L &&
                        tickerAfterRuntimeDispose.disposedTickerCount == 1L &&
                        scheduler.pendingCount == 0
                    ) {
                        counters.cleanTickerBeforeHostDisposeCycleCount += 1
                    }
                    counters.createdTickerCount += tickerAfterRuntimeDispose.createdTickerCount
                    counters.disposedTickerCount += tickerAfterRuntimeDispose.disposedTickerCount

                    frameScope.dispose()

                    // Terminal Host diagnostics must expose no callback, listener, ticker, or frame.
                    val disposedScope = frameScope.diagnostics()
                    if (
                        disposedScope.isDisposed &&
                        disposedScope.pendingCallbackCount == 0 &&
                        disposedScope.frameListenerCount == 0 &&
                        disposedScope.activeTickerCount == 0 &&
                        disposedScope.liveTickerCount == 0 &&
                        !disposedScope.sourceFramePending &&
                        scheduler.pendingCount == 0
                    ) {
                        counters.cleanHostScopeCycleCount += 1
                    }
                }
            }
        }

        assertEquals(TotalStressCycles, counters.routeCreatedCount)
        assertEquals(TotalStressCycles, counters.routeEnteredCount)
        assertEquals(TotalStressCycles, counters.routeBuildCount)
        assertEquals(TotalStressCycles, counters.routeDisposedCount)
        assertEquals(TotalStressCycles, counters.subscriptionCount)
        assertEquals(TotalStressCycles, counters.unsubscriptionCount)
        assertEquals(0, asyncSource.activeSubscriptionCount)
        assertEquals(TotalStressCycles, counters.tickerWidgetMountCount)
        assertEquals(TotalStressCycles, counters.tickerWidgetDisposeCount)
        assertEquals(TotalStressCycles, counters.tickerTickCount)
        assertEquals(TotalStressCycles.toLong(), counters.createdTickerCount)
        assertEquals(TotalStressCycles.toLong(), counters.disposedTickerCount)
        assertEquals(TotalStressCycles, counters.mountedRuntimeCycleCount)
        assertEquals(TotalStressCycles, counters.mountedOwnedResourceCycleCount)
        assertEquals(TotalStressCycles, counters.cleanRuntimeCycleCount)
        assertEquals(TotalStressCycles, counters.cleanTickerBeforeHostDisposeCycleCount)
        assertEquals(TotalStressCycles, counters.cleanHostScopeCycleCount)
        assertEquals(0L, counters.retainedElementCountAfterDispose)
        assertEquals(0L, counters.retainedListenableCountAfterDispose)
        assertEquals(0L, counters.pendingDirtyCountAfterDispose)
        assertEquals(0L, counters.attachedRenderCountAfterDispose)
        assertEquals(0, scheduler.pendingCount)
        assertTrue("10,000-cycle stress duration must be positive", elapsedNanos > 0L)
        assertFalse("No subscription may survive terminal disposal", asyncSource.hasActiveSubscriptions)

        // Stable marker is retained in XML test output for CI timing inspection.
        println(
            "PIXEL_RESOURCE_STRESS cycles=$TotalStressCycles " +
                "elapsedMs=${elapsedNanos / NanosPerMillisecond}",
        )
    }

    /** Creates one real Navigator route tree for a single Host-like lifecycle cycle. */
    private fun buildCycleRoot(
        cycleIndex: Int,
        frameScope: PixelHostFrameScope,
        asyncSource: CountingAsyncSource,
        counters: ResourceStressCounters,
    ): Widget {
        counters.routeCreatedCount += 1
        // Route callbacks provide exact creation/activation/disposal accounting.
        val route = testRouteRequest(
            name = "resource-stress-$cycleIndex",
            transition = PixelRouteTransition.None,
            onEnter = { counters.routeEnteredCount += 1 },
            onDispose = { counters.routeDisposedCount += 1 },
            builder = {
                counters.routeBuildCount += 1
                AsyncBuilder(source = asyncSource) { _, snapshot ->
                    // Synchronous Success proves the subscription callback participated in build.
                    val value = (snapshot as PixelAsyncSnapshot.Success).value
                    HostOwnedTickerProbe(
                        label = "C$value",
                        vsync = frameScope.tickerProvider,
                        counters = counters,
                    )
                }
            },
        )
        return PixelNavigator(
            initialRequest = route,
            vsync = frameScope.tickerProvider,
            defaultTransition = PixelRouteTransition.None,
        )
    }

    /** Number of independent Host-like lifecycle batches. */
    private companion object {
        /** Cycles per batch keep loop structure explicit while preserving exactly 10,000 cycles. */
        const val StressBatchSize: Int = 100

        /** Number of deterministic batches executed by the resource soak. */
        const val StressBatchCount: Int = 100

        /** Exact lifecycle count required by the M2-3 acceptance contract. */
        const val TotalStressCycles: Int = StressBatchSize * StressBatchCount

        /** Logical render width for the deliberately small retained tree. */
        const val StressLogicalWidth: Int = 16

        /** Logical render height for the deliberately small retained tree. */
        const val StressLogicalHeight: Int = 8

        /** Monotonic source-frame interval used for each newly created Host scope. */
        const val StressFrameIntervalNanos: Long = 16_000_000L

        /** Nanoseconds per millisecond used only for the human-readable timing marker. */
        const val NanosPerMillisecond: Long = 1_000_000L
    }
}

/** Exact primitive counters accumulated without retaining any lifecycle-owned SDK object. */
private class ResourceStressCounters {
    /** Number of root route definitions created by the fixture. */
    var routeCreatedCount: Int = 0

    /** Number of root entries that reached their enter callback. */
    var routeEnteredCount: Int = 0

    /** Number of root route builders executed by retained mounting. */
    var routeBuildCount: Int = 0

    /** Number of root entries that reached terminal route disposal. */
    var routeDisposedCount: Int = 0

    /** Number of independent AsyncBuilder subscriptions created. */
    var subscriptionCount: Int = 0

    /** Number of independent AsyncBuilder subscriptions cancelled. */
    var unsubscriptionCount: Int = 0

    /** Number of ticker-owning widget State instances mounted. */
    var tickerWidgetMountCount: Int = 0

    /** Number of ticker-owning widget State instances disposed. */
    var tickerWidgetDisposeCount: Int = 0

    /** Number of active ticker callbacks delivered by manual Host frames. */
    var tickerTickCount: Int = 0

    /** Aggregate provider-reported ticker creations across all isolated scopes. */
    var createdTickerCount: Long = 0L

    /** Aggregate provider-reported ticker disposals across all isolated scopes. */
    var disposedTickerCount: Long = 0L

    /** Cycles that mounted both Element and attached RenderObject diagnostics. */
    var mountedRuntimeCycleCount: Int = 0

    /** Cycles that exposed exactly one live subscription, ticker, and pending Host frame. */
    var mountedOwnedResourceCycleCount: Int = 0

    /** Cycles whose runtime diagnostics were fully empty immediately after disposal. */
    var cleanRuntimeCycleCount: Int = 0

    /** Cycles whose widget ticker was gone before the outer Host scope was disposed. */
    var cleanTickerBeforeHostDisposeCycleCount: Int = 0

    /** Cycles whose terminal Host scope diagnostics and upstream scheduler were empty. */
    var cleanHostScopeCycleCount: Int = 0

    /** Aggregate retained Element nodes observed after runtime disposal. */
    var retainedElementCountAfterDispose: Long = 0L

    /** Aggregate Element-to-Listenable dependencies observed after runtime disposal. */
    var retainedListenableCountAfterDispose: Long = 0L

    /** Aggregate dirty Elements observed after runtime disposal. */
    var pendingDirtyCountAfterDispose: Long = 0L

    /** Aggregate pipeline-attached RenderObjects observed after runtime disposal. */
    var attachedRenderCountAfterDispose: Long = 0L
}

/** Counting callback source whose cancellation is synchronous, idempotent, and observable. */
private class CountingAsyncSource(
    /** Primitive counters shared with the enclosing stress assertion. */
    private val counters: ResourceStressCounters,
) : PixelAsyncSource<Int> {
    /** Number of subscriptions that have not yet received their cancellation callback. */
    var activeSubscriptionCount: Int = 0
        private set

    /** Whether at least one subscription still survives terminal runtime disposal. */
    val hasActiveSubscriptions: Boolean
        get() = activeSubscriptionCount != 0

    /** Creates one independent subscription and emits its stable sequence synchronously. */
    override fun subscribe(listener: (PixelAsyncSnapshot<Int>) -> Unit): () -> Unit {
        counters.subscriptionCount += 1
        activeSubscriptionCount += 1
        // Sequence value proves the current subscription delivered into its AsyncBuilder.
        val sequence = counters.subscriptionCount
        listener(PixelAsyncSnapshot.Success(sequence))
        // Local guard makes accidental duplicate cancellation visible without corrupting totals.
        var isCancelled = false
        return {
            if (!isCancelled) {
                isCancelled = true
                activeSubscriptionCount -= 1
                counters.unsubscriptionCount += 1
            }
        }
    }
}

/** Stateful leaf that owns exactly one ticker from the current Host frame scope. */
private class HostOwnedTickerProbe(
    /** Small text label rendered by this lifecycle probe. */
    val label: String,
    /** Host-owned provider that must own and release the probe ticker. */
    val vsync: PixelTickerProvider,
    /** Primitive counters updated by mount, tick, and dispose callbacks. */
    val counters: ResourceStressCounters,
    /** Optional retained-widget identity. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one State instance that owns the cycle's ticker. */
    override fun createState(): State<out StatefulWidget> = HostOwnedTickerProbeState()
}

/** State implementation that proves widget disposal releases its Host-owned ticker eagerly. */
private class HostOwnedTickerProbeState : State<HostOwnedTickerProbe>() {
    /** Ticker allocated on mount and disposed synchronously with this State. */
    private lateinit var ticker: PixelTicker

    /** Allocates and starts the one Host-owned ticker for this mounted cycle. */
    override fun initState() {
        widget.counters.tickerWidgetMountCount += 1
        ticker = widget.vsync.createTicker {
            widget.counters.tickerTickCount += 1
        }
        ticker.start()
    }

    /** Builds the minimal renderable leaf required for a genuine retained render pass. */
    override fun build(context: BuildContext): Widget = Text(widget.label)

    /** Releases the ticker before the enclosing Host frame scope performs fallback cleanup. */
    override fun dispose() {
        ticker.dispose()
        widget.counters.tickerWidgetDisposeCount += 1
    }
}
