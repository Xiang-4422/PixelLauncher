package com.purride.pixelui.widgets.navigation

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android-device verification for the real Bundle implementation used by multi-stack restore. */
@RunWith(AndroidJUnit4::class)
class PixelMultiStackBundleInstrumentedTest {
    /** Supported fields and child bytes round-trip through an actual Android Bundle defensively. */
    @Test
    fun bundleRoundTripPreservesSchemaSelectionAndChildBytes() {
        // Mutable source arrays prove the snapshot and decoded envelope both use defensive copies.
        val homeBytes = byteArrayOf(1, 2, 3)
        val settingsBytes = byteArrayOf(4, 5, 6)
        val snapshot = PixelMultiStackSnapshot(
            schemaVersion = PixelMultiStackSnapshotSchemaVersion,
            activeStackId = "settings",
            snapshots = linkedMapOf(
                "home" to homeBytes,
                "settings" to settingsBytes,
            ),
        )
        val savedState = Bundle()

        snapshot.saveToBundle(savedState)
        homeBytes[0] = 99
        val decoded = savedState.getPixelMultiStackSnapshot()
            as PixelMultiStackSnapshotDecodeResult.Decoded

        assertEquals(PixelMultiStackSnapshotSchemaVersion, decoded.snapshot.schemaVersion)
        assertEquals("settings", decoded.snapshot.activeStackId)
        assertEquals(linkedSetOf("home", "settings"), decoded.snapshot.stackIds)
        assertArrayEquals(byteArrayOf(1, 2, 3), decoded.snapshot.snapshotBytes("home"))
        val defensiveCopy = checkNotNull(decoded.snapshot.snapshotBytes("settings"))
        defensiveCopy[0] = 88
        assertArrayEquals(byteArrayOf(4, 5, 6), decoded.snapshot.snapshotBytes("settings"))
    }

    /** Unsupported or structurally incomplete outer schemas return rejection instead of throwing. */
    @Test
    fun malformedAndUnsupportedBundleEnvelopesAreRejected() {
        // Unsupported schema requires no child decoding and must remain a stable result category.
        val unsupportedState = Bundle().apply {
            putBundle(
                PixelMultiStackSnapshotBundleKey,
                Bundle().apply { putInt("schema", 99) },
            )
        }
        val unsupported = unsupportedState.getPixelMultiStackSnapshot()
            as PixelMultiStackSnapshotDecodeResult.Rejected
        assertEquals(
            PixelMultiStackSnapshotFailureReason.UnsupportedSchema,
            unsupported.failure.reason,
        )

        // Current schema with no active stack or children is malformed but non-throwing.
        val malformedState = Bundle().apply {
            putBundle(
                PixelMultiStackSnapshotBundleKey,
                Bundle().apply { putInt("schema", PixelMultiStackSnapshotSchemaVersion) },
            )
        }
        val malformed = malformedState.getPixelMultiStackSnapshot()
            as PixelMultiStackSnapshotDecodeResult.Rejected
        assertEquals(
            PixelMultiStackSnapshotFailureReason.InvalidEnvelope,
            malformed.failure.reason,
        )
        assertTrue(malformed.failure.message.isNotBlank())
    }
}
