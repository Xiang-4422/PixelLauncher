package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [TextListSupport] / [TextListViewport] — the shared text-list
 * viewport geometry: height/visibleRows derivation and the createLayoutMetrics
 * clamps that the drawer and settings lists both build on. JVM-safe; no Android
 * dependencies.
 */
class TextListSupportTest {

    // ── TextListViewport derived metrics ──────────────────────────────────────

    @Test
    fun viewport_visibleRowsIsHeightDividedByRowHeight() {
        val viewport = TextListViewport(top = 10, bottomExclusive = 50, rowHeight = 10)
        assertEquals(40, viewport.height)
        assertEquals(4, viewport.visibleRows)
    }

    @Test
    fun viewport_truncatesPartialTrailingRow() {
        val viewport = TextListViewport(top = 0, bottomExclusive = 45, rowHeight = 10)
        assertEquals(45, viewport.height)
        assertEquals(4, viewport.visibleRows)
    }

    @Test
    fun viewport_heightFlooredToOneRowWhenSpanTooSmall() {
        val viewport = TextListViewport(top = 10, bottomExclusive = 12, rowHeight = 10)
        assertEquals(10, viewport.height) // (12 - 10) coerced up to rowHeight
        assertEquals(1, viewport.visibleRows)
    }

    // ── createLayoutMetrics clamps ────────────────────────────────────────────

    @Test
    fun createLayoutMetrics_pushesBottomBelowTopUpToOneRow() {
        val metrics = TextListSupport.createLayoutMetrics(top = 10, bottomExclusive = 5, rowHeight = 10)
        assertEquals(20, metrics.viewport.bottomExclusive)
        assertEquals(1, metrics.viewport.visibleRows)
    }

    @Test
    fun createLayoutMetrics_clampsNonPositiveRowHeightToOne() {
        val metrics = TextListSupport.createLayoutMetrics(top = 0, bottomExclusive = 10, rowHeight = 0)
        assertEquals(1, metrics.viewport.rowHeight)
        assertEquals(10, metrics.viewport.visibleRows)
    }
}
