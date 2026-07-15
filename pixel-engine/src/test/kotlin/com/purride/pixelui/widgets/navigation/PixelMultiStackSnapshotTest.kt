package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Process-recreation contracts for typed multi-stack Navigator hosts. */
class PixelMultiStackSnapshotTest {
    /** All tab stacks, entry IDs, typed arguments, approved state, and selection survive recreation. */
    @Test
    fun processRecreationRestoresEveryTypedStackAndActiveSelection() {
        // Original destinations represent application definitions before process death.
        val originalHome = destination("home-page")
        val originalSettings = destination("settings-page")
        // Per-stack registries keep identical destination IDs in independent namespaces.
        val originalRegistries = registries(originalHome, originalSettings)
        // Original host mounts both typed roots even though only home is presented.
        val originalController = PixelMultiStackNavigatorController(initialStackId = "home")
        val originalTester = PixelTester()
        originalTester.pumpWidget(
            multiStackWidget(
                controller = originalController,
                home = originalHome,
                settings = originalSettings,
                tester = originalTester,
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )
        val originalHomeState = checkNotNull(originalController.navigatorState("home"))
        val originalSettingsState = checkNotNull(originalController.navigatorState("settings"))
        // Each child stack receives an independent foreground entry and local counter.
        val homeDetail = originalHomeState.push(PixelRouteRequest(originalHome, "home-detail"))
        homeDetail.stateBucket.write(RestoredCounterKey, 11)
        val settingsDetail = originalSettingsState.push(
            PixelRouteRequest(originalSettings, "settings-detail"),
        )
        settingsDetail.stateBucket.write(RestoredCounterKey, 22)
        originalController.selectStack("settings")
        originalTester.pumpAndSettle()

        val captured = originalController.persistentSnapshot(originalRegistries)
            as PixelMultiStackSnapshotEncodeResult.Encoded
        // Only encoded child bytes and stable IDs cross the recreated-process boundary.
        val savedSnapshot = captured.snapshot
        val originalHomeIds = originalHomeState.entries.map(PixelRouteEntry<*, *>::id)
        val originalSettingsIds = originalSettingsState.entries.map(PixelRouteEntry<*, *>::id)
        originalTester.dispose()

        // Recreated process uses new destination and adapter object identities with stable IDs.
        val recreatedHome = destination("home-page")
        val recreatedSettings = destination("settings-page")
        val recreatedRegistries = registries(recreatedHome, recreatedSettings)
        val recreatedController = PixelMultiStackNavigatorController(initialStackId = "home")
        val recreatedTester = PixelTester()
        recreatedTester.pumpWidget(
            multiStackWidget(
                controller = recreatedController,
                home = recreatedHome,
                settings = recreatedSettings,
                tester = recreatedTester,
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )
        assertEquals("home", recreatedController.activeStackId)

        val restored = recreatedController.restorePersistentSnapshot(
            savedSnapshot,
            recreatedRegistries,
        )

        assertTrue(restored is PixelMultiStackRestoreResult.Restored)
        assertEquals("settings", recreatedController.activeStackId)
        val restoredHomeState = checkNotNull(recreatedController.navigatorState("home"))
        val restoredSettingsState = checkNotNull(recreatedController.navigatorState("settings"))
        assertEquals(originalHomeIds, restoredHomeState.entries.map(PixelRouteEntry<*, *>::id))
        assertEquals(originalSettingsIds, restoredSettingsState.entries.map(PixelRouteEntry<*, *>::id))
        val restoredHomeDetail = typedEntry(restoredHomeState.currentEntry)
        val restoredSettingsDetail = typedEntry(restoredSettingsState.currentEntry)
        assertEquals("home-detail", restoredHomeDetail.arguments)
        assertEquals("settings-detail", restoredSettingsDetail.arguments)
        assertEquals(11, restoredHomeDetail.stateBucket.read(RestoredCounterKey))
        assertEquals(22, restoredSettingsDetail.stateBucket.read(RestoredCounterKey))
        assertSame(recreatedHome, restoredHomeDetail.destination)
        assertSame(recreatedSettings, restoredSettingsDetail.destination)
        recreatedTester.dispose()
    }

    /** A corrupt child or unsupported outer schema rejects before any mounted stack changes. */
    @Test
    fun corruptOrUnsupportedBundleLeavesAllMountedStacksUnchanged() {
        // Mounted fallback host supplies the identities that must survive both rejections.
        val home = destination("home-page")
        val settings = destination("settings-page")
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        val tester = PixelTester()
        tester.pumpWidget(
            multiStackWidget(controller, home, settings, tester),
            logicalWidth = 40,
            logicalHeight = 12,
        )
        val homeRoot = checkNotNull(controller.navigatorState("home")).currentEntry
        val settingsRoot = checkNotNull(controller.navigatorState("settings")).currentEntry
        val encoded = controller.persistentSnapshot(registries(home, settings))
            as PixelMultiStackSnapshotEncodeResult.Encoded

        // Mutating one defensive child copy creates a valid outer object with corrupt child bytes.
        val corruptChildren = encoded.snapshot.stackIds.associateWithTo(linkedMapOf()) { stackId ->
            checkNotNull(encoded.snapshot.snapshotBytes(stackId))
        }
        checkNotNull(corruptChildren["settings"]).also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }
        val corruptSnapshot = PixelMultiStackSnapshot(
            schemaVersion = PixelMultiStackSnapshotSchemaVersion,
            activeStackId = "settings",
            snapshots = corruptChildren,
        )
        val corruptResult = controller.restorePersistentSnapshot(
            corruptSnapshot,
            registries(home, settings),
        )
        assertEquals(
            PixelMultiStackSnapshotFailureReason.ChildSnapshotRejected,
            (corruptResult as PixelMultiStackRestoreResult.Rejected).failure.reason,
        )
        assertSame(homeRoot, controller.navigatorState("home")?.currentEntry)
        assertSame(settingsRoot, controller.navigatorState("settings")?.currentEntry)

        // Unsupported outer schema is rejected before child lookup or codec invocation.
        val unsupportedSnapshot = PixelMultiStackSnapshot(
            schemaVersion = 99,
            activeStackId = encoded.snapshot.activeStackId,
            snapshots = encoded.snapshot.stackIds.associateWith { stackId ->
                checkNotNull(encoded.snapshot.snapshotBytes(stackId))
            },
        )
        val unsupported = controller.restorePersistentSnapshot(
            unsupportedSnapshot,
            registries(home, settings),
        ) as PixelMultiStackRestoreResult.Rejected
        assertEquals(
            PixelMultiStackSnapshotFailureReason.UnsupportedSchema,
            unsupported.failure.reason,
        )
        assertSame(homeRoot, controller.navigatorState("home")?.currentEntry)
        assertSame(settingsRoot, controller.navigatorState("settings")?.currentEntry)
        tester.dispose()
    }

    /** Creates one typed destination whose label is its restored argument. */
    private fun destination(id: String): PixelRouteDestination<String, Unit> {
        return pixelRouteDestination(id = id, transition = PixelRouteTransition.None) { _, scope ->
            Text(scope.arguments)
        }
    }

    /** Creates the two explicit destination allowlists used by one multi-stack host. */
    private fun registries(
        home: PixelRouteDestination<String, Unit>,
        settings: PixelRouteDestination<String, Unit>,
    ): Map<String, PixelRouteSnapshotRegistry> {
        return mapOf(
            "home" to PixelRouteSnapshotRegistry(listOf(StringRouteAdapter(home))),
            "settings" to PixelRouteSnapshotRegistry(listOf(StringRouteAdapter(settings))),
        )
    }

    /** Creates a host whose child roots use [PixelTypedNavigatorStack] rather than legacy routes. */
    private fun multiStackWidget(
        controller: PixelMultiStackNavigatorController,
        home: PixelRouteDestination<String, Unit>,
        settings: PixelRouteDestination<String, Unit>,
        tester: PixelTester,
    ): PixelMultiStackNavigator {
        return PixelMultiStackNavigator(
            stacks = listOf(
                PixelTypedNavigatorStack("home", PixelRouteRequest(home, "home-root")),
                PixelTypedNavigatorStack(
                    "settings",
                    PixelRouteRequest(settings, "settings-root"),
                ),
            ),
            controller = controller,
            vsync = tester.vsync,
            defaultTransition = PixelRouteTransition.None,
        )
    }

    /** Narrows a known fixture entry to its typed String destination. */
    @Suppress("UNCHECKED_CAST")
    private fun typedEntry(entry: PixelRouteEntry<*, *>): PixelRouteEntry<String, Unit> {
        return entry as PixelRouteEntry<String, Unit>
    }

    /** Destination adapter that persists String arguments and one allowlisted counter. */
    private class StringRouteAdapter(
        /** Exact destination instance paired with the adapter in the current process. */
        destination: PixelRouteDestination<String, Unit>,
    ) : PixelRouteSnapshotAdapter<String, Unit>(destination, StringPayloadCodec) {
        /** Captures only the explicitly approved integer counter. */
        override fun encodeRouteState(
            entry: PixelRouteEntry<String, Unit>,
        ): Map<String, ByteArray> {
            val counter = entry.stateBucket.read(RestoredCounterKey) ?: return emptyMap()
            return mapOf(CounterPayloadKey to counter.toString().toByteArray(StandardCharsets.UTF_8))
        }

        /** Validates the optional counter before writing it to a detached state bucket. */
        override fun decodeRouteState(
            schemaVersion: Int,
            payloads: Map<String, ByteArray>,
        ): PixelRouteStateDecodeResult {
            if (schemaVersion != stateSchemaVersion) {
                return PixelRouteStateDecodeResult.Rejected("Unsupported state schema")
            }
            if (payloads.isEmpty()) {
                return PixelRouteStateDecodeResult.Decoded(PixelRouteStateRestorer { })
            }
            if (payloads.keys != setOf(CounterPayloadKey)) {
                return PixelRouteStateDecodeResult.Rejected("Unknown state key")
            }
            val counter = String(
                checkNotNull(payloads[CounterPayloadKey]),
                StandardCharsets.UTF_8,
            ).toIntOrNull() ?: return PixelRouteStateDecodeResult.Rejected("Counter is invalid")
            return PixelRouteStateDecodeResult.Decoded(
                PixelRouteStateRestorer { bucket -> bucket.write(RestoredCounterKey, counter) },
            )
        }
    }

    /** UTF-8 codec for the test destination's non-null String argument. */
    private object StringPayloadCodec : PixelRoutePayloadCodec<String> {
        /** Current String payload schema. */
        override val schemaVersion: Int = 1

        /** Encodes a non-blank label. */
        override fun encode(value: String): ByteArray {
            require(value.isNotBlank()) { "String route label must not be blank" }
            return value.toByteArray(StandardCharsets.UTF_8)
        }

        /** Decodes only schema 1 non-blank labels. */
        override fun decode(
            schemaVersion: Int,
            payload: ByteArray,
        ): PixelRoutePayloadDecodeResult<String> {
            val value = String(payload, StandardCharsets.UTF_8)
            return if (schemaVersion == 1 && value.isNotBlank()) {
                PixelRoutePayloadDecodeResult.Decoded(value)
            } else {
                PixelRoutePayloadDecodeResult.Rejected("String route label is invalid")
            }
        }
    }

    private companion object {
        /** In-memory route-local key restored only by [StringRouteAdapter]. */
        val RestoredCounterKey: PixelRouteStateKey<Int> = PixelRouteStateKey("counter")

        /** Stable wire key owned by [StringRouteAdapter]. */
        const val CounterPayloadKey: String = "counter"
    }
}
