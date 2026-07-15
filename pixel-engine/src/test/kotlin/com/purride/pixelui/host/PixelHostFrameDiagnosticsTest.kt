package com.purride.pixelui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Locks stable diagnostics value invariants and constructor-compatible Inspector attachment. */
class PixelHostFrameDiagnosticsTest {
    /** Rejects overlapping exclusive phase totals and inconsistent unattributed time. */
    @Test
    fun timingSnapshotRejectsImpossibleExclusiveTotals() {
        assertThrows(IllegalArgumentException::class.java) {
            PixelFrameTimings(
                buildNanos = 5L,
                layoutNanos = 5L,
                paintNanos = 5L,
                bufferSubmitNanos = 5L,
                androidDrawNanos = 5L,
                totalFrameNanos = 20L,
                unattributedNanos = 0L,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelFrameTimings(
                buildNanos = 1L,
                layoutNanos = 1L,
                paintNanos = 1L,
                bufferSubmitNanos = 1L,
                androidDrawNanos = 1L,
                totalFrameNanos = 10L,
                unattributedNanos = 4L,
            )
        }
    }

    /** Rejects negative dirty, pixel, runtime, and cache counters. */
    @Test
    fun workloadSnapshotRejectsNegativeCounters() {
        assertThrows(IllegalArgumentException::class.java) {
            validWorkload().copy(dirtyElementCount = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validWorkload().copy(allocatedBytes = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validWorkload().copy(bufferCacheMissCount = -1L)
        }
    }

    /** Requires a drop reason exactly when a frame crosses one or more display budgets. */
    @Test
    fun frameSnapshotRejectsInconsistentDeadlineReason() {
        assertThrows(IllegalArgumentException::class.java) {
            validDiagnostics().copy(dropReason = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validDiagnostics().copy(missedVsyncCount = 0)
        }
    }

    /** Adds diagnostics without changing the frozen Inspector constructor, copy, or equality data. */
    @Test
    fun inspectorAttachesDiagnosticsOutsideFrozenConstructorValueSemantics() {
        /** Snapshot created solely through the pre-existing primary constructor. */
        val original = emptyInspectorSnapshot()
        /** Complete frame attached through the additive compatibility helper. */
        val diagnostics = validDiagnostics()
        /** Independent constructor-compatible copy carrying the additive frame data. */
        val attached = original.withFrameDiagnostics(diagnostics)

        assertNull(original.frameDiagnostics)
        assertEquals(diagnostics, attached.frameDiagnostics)
        assertEquals(original, attached)
        assertNull(attached.copy().frameDiagnostics)
    }

    /** Creates one valid exclusive timing breakdown for invariant tests. */
    private fun validTimings(): PixelFrameTimings {
        return PixelFrameTimings(
            buildNanos = 1L,
            layoutNanos = 2L,
            paintNanos = 3L,
            bufferSubmitNanos = 4L,
            androidDrawNanos = 5L,
            totalFrameNanos = 16L,
            unattributedNanos = 1L,
        )
    }

    /** Creates one valid workload with every nullable and cache channel present. */
    private fun validWorkload(): PixelFrameWorkload {
        return PixelFrameWorkload(
            dirtyElementCount = 1,
            dirtyRenderNodeCount = 2,
            paintedPixelCount = 3L,
            submittedPixelCount = 4L,
            allocatedBytes = 5L,
            garbageCollectionCount = 0L,
            bufferCacheHitCount = 6L,
            bufferCacheMissCount = 7L,
            renderCacheHit = false,
        )
    }

    /** Creates one valid over-budget frame with a matching primary drop reason. */
    private fun validDiagnostics(): PixelHostFrameDiagnostics {
        return PixelHostFrameDiagnostics(
            frameNumber = 1L,
            frameIntervalNanos = 10L,
            frameBudgetNanos = 10L,
            timings = validTimings(),
            workload = validWorkload(),
            dropReason = PixelFrameDropReason.ANDROID_DRAW,
            missedVsyncCount = 1,
        )
    }

    /** Creates an Inspector snapshot through the unchanged pre-M6 primary constructor. */
    private fun emptyInspectorSnapshot(): PixelInspectorSnapshot {
        return PixelInspectorSnapshot(
            frameStats = null,
            allocationSample = null,
            targetCounts = PixelInspectorTargetCounts.Empty,
            targetSnapshots = emptyList(),
            elementTree = "",
            renderTree = "",
            semanticsTree = "",
            hasPendingBuild = false,
            focusedTextInput = false,
            activePagerCount = 0,
            activeListCount = 0,
            activeSlider = false,
            activeScrollbar = false,
            activeRefresh = false,
        )
    }
}
