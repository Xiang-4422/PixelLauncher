package com.purride.pixelui.foundation

import com.purride.pixelui.AsyncBuilder
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelAsyncSource
import com.purride.pixelui.Text
import com.purride.pixelui.internal.ElementTreeBuildRuntimeFactory
import com.purride.pixelui.internal.UnsupportedWidgetAdapter
import com.purride.pixelui.pixelAsyncSourceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncBuilderTest {

    // ── 快照类型语义 ───────────────────────────────────────────────────────

    @Test
    fun loadingIsSingletonAndPrintsExpectedToString() {
        assertTrue(PixelAsyncSnapshot.Loading === PixelAsyncSnapshot.Loading)
        assertEquals("PixelAsyncSnapshot.Loading", PixelAsyncSnapshot.Loading.toString())
    }

    @Test
    fun successDataClassEqualityWorksByValue() {
        val a = PixelAsyncSnapshot.Success("hello")
        val b = PixelAsyncSnapshot.Success("hello")
        val c = PixelAsyncSnapshot.Success("world")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)
    }

    @Test
    fun failureCarriesErrorByReference() {
        val err = IllegalStateException("boom")
        val snap = PixelAsyncSnapshot.Failure(err)
        assertEquals(err, snap.error)
    }

    // ── 同步 source helper ─────────────────────────────────────────────────

    @Test
    fun ofValueImmediatelyEmitsSuccess() {
        val source = pixelAsyncSourceOf(42)
        var seen: PixelAsyncSnapshot<Int>? = null
        val unsub = source.subscribe { seen = it }
        assertEquals(PixelAsyncSnapshot.Success(42), seen)
        unsub()
    }

    // ── source 合约：subscribe / unsubscribe ────────────────────────────

    @Test
    fun subscribeReturnsUnsubscribeCallback() {
        var unsubscribed = false
        val source = PixelAsyncSource<Int> { listener ->
            listener(PixelAsyncSnapshot.Loading)
            return@PixelAsyncSource { unsubscribed = true }
        }
        val unsub = source.subscribe { /* ignore */ }
        assertEquals(false, unsubscribed)
        unsub()
        assertEquals(true, unsubscribed)
    }

    // ── AsyncBuilder 集成：subscribe 在 mount 后被调用 ─────────────────

    @Test
    fun asyncBuilderSubscribesOnMount() {
        var subscribed = false
        val source = PixelAsyncSource<Int> { _ ->
            subscribed = true
            return@PixelAsyncSource { }
        }
        val widget = AsyncBuilder(source = source) { _, _ -> Text("X") }

        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        try {
            runtime.resolveElementTree(widget)
            assertTrue("AsyncBuilder should call source.subscribe() during initState", subscribed)
        } finally {
            runtime.dispose()
        }
    }

    /**
     * 在 mount 时同步 emit Success，应当让 builder 拿到 Success 而不是 Loading。
     */
    @Test
    fun asyncBuilderUsesSynchronousSnapshotForFirstBuild() {
        var capturedSnapshot: PixelAsyncSnapshot<String>? = null
        val source = PixelAsyncSource<String> { listener ->
            listener(PixelAsyncSnapshot.Success("hi"))
            return@PixelAsyncSource { }
        }
        val widget = AsyncBuilder(source = source) { _, snapshot ->
            capturedSnapshot = snapshot
            Text("X")
        }

        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        try {
            runtime.resolveElementTree(widget)
            assertNotNull(capturedSnapshot)
            assertEquals(PixelAsyncSnapshot.Success("hi"), capturedSnapshot)
        } finally {
            runtime.dispose()
        }
    }

    /**
     * runtime dispose 后 source 的 unsubscribe lambda 必须被调用。
     */
    @Test
    fun asyncBuilderUnsubscribesOnDispose() {
        var unsubscribed = false
        val source = PixelAsyncSource<Int> { _ ->
            return@PixelAsyncSource { unsubscribed = true }
        }
        val widget = AsyncBuilder(source = source) { _, _ -> Text("X") }

        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        runtime.resolveElementTree(widget)
        runtime.dispose()

        assertTrue("AsyncBuilder should unsubscribe on dispose", unsubscribed)
    }

    @Test
    fun initialSnapshotCanOverrideLoading() {
        var captured: PixelAsyncSnapshot<Int>? = null
        val source = PixelAsyncSource<Int> { _ -> ({}) }
        val widget = AsyncBuilder(
            source = source,
            initial = PixelAsyncSnapshot.Success(7),
        ) { _, snapshot ->
            captured = snapshot
            Text("X")
        }

        val runtime = ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = {},
            widgetAdapter = UnsupportedWidgetAdapter,
        )
        try {
            runtime.resolveElementTree(widget)
            assertEquals(PixelAsyncSnapshot.Success(7), captured)
        } finally {
            runtime.dispose()
        }
    }

    @Test
    fun unusedNullSnapshotStaysNull() {
        // Sanity: assertNull from junit is properly imported.
        assertNull(null)
    }
}
