package com.purride.pixelcompat.routeentry

import com.purride.pixelui.PixelDeepLinkArgumentDecoder
import com.purride.pixelui.PixelDeepLinkDecoded
import com.purride.pixelui.PixelMultiStackNavigator
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNestedNavigator
import com.purride.pixelui.PixelNestedNavigatorController
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorPersistentSnapshotSchemaVersion
import com.purride.pixelui.PixelNavigatorSnapshotDecoded
import com.purride.pixelui.PixelNavigatorSnapshotEncoded
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.PixelRoutePayloadCodec
import com.purride.pixelui.PixelRoutePayloadDecodeResult
import com.purride.pixelui.PixelRoutePayloadDecoded
import com.purride.pixelui.PixelRoutePayloadRejected
import com.purride.pixelui.PixelRouteEntry
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteSnapshotAdapter
import com.purride.pixelui.PixelRouteSnapshotRegistry
import com.purride.pixelui.PixelRouteStateDecodeResult
import com.purride.pixelui.PixelRouteStateDecoded
import com.purride.pixelui.PixelRouteStateRestorer
import com.purride.pixelui.PixelRouteStateRejected
import com.purride.pixelui.PixelRouteStateKey
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.PixelTypedDeepLinkMatcher
import com.purride.pixelui.PixelTypedDeepLinkNavigated
import com.purride.pixelui.PixelTypedDeepLinkResolver
import com.purride.pixelui.PixelTypedDeepLinkRoute
import com.purride.pixelui.PixelTypedNavigatorStack
import com.purride.pixelui.Text
import com.purride.pixelui.handleTypedDeepLink
import com.purride.pixelui.pixelRouteDestination
import com.purride.pixelui.testing.PixelTester
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** External root-package contract for typed restoration, multi-stack, and deep-link APIs. */
public class NavigationRestorationConsumerTest {
    /** A published AAR consumer can recreate a typed stack through only stable root-package API. */
    @Test
    public fun typedSnapshotRoundTripPreservesArgumentsAndEntryIdentity() {
        // Original process mounts a typed root and one foreground entry.
        val originalTester = PixelTester()
        var originalState: PixelNavigatorState? = null
        val originalDestination = destination("external-page") { state -> originalState = state }
        val originalRegistry = PixelRouteSnapshotRegistry(
            listOf(StringSnapshotAdapter(originalDestination)),
        )
        originalTester.pumpWidget(
            PixelNavigator(
                initialRequest = PixelRouteRequest(originalDestination, "root"),
                vsync = originalTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        val originalNavigator = checkNotNull(originalState)
        val detail = originalNavigator.push(PixelRouteRequest(originalDestination, "detail"))
        val encoded = originalNavigator.persistentSnapshot(originalRegistry)
            as PixelNavigatorSnapshotEncoded
        assertEquals(PixelNavigatorPersistentSnapshotSchemaVersion, encoded.schemaVersion)
        originalTester.dispose()

        // Recreated process deliberately uses a new destination/adapter instance with the same ID.
        val recreatedTester = PixelTester()
        var recreatedState: PixelNavigatorState? = null
        val recreatedDestination = destination("external-page") { state -> recreatedState = state }
        val recreatedRegistry = PixelRouteSnapshotRegistry(
            listOf(StringSnapshotAdapter(recreatedDestination)),
        )
        recreatedTester.pumpWidget(
            PixelNavigator(
                initialRequest = PixelRouteRequest(recreatedDestination, "fallback"),
                vsync = recreatedTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        val recreatedNavigator = checkNotNull(recreatedState)
        val decoded = recreatedNavigator.restorePersistentSnapshot(encoded.bytes, recreatedRegistry)

        assertTrue(decoded is PixelNavigatorSnapshotDecoded)
        assertEquals(listOf("root", "detail"), recreatedNavigator.entries.map { it.arguments })
        assertEquals(detail.id, recreatedNavigator.currentEntry.id)
        recreatedTester.dispose()
    }

    /** Root-package typed deep links can target one retained stack without touching another. */
    @Test
    public fun typedDeepLinkTargetsRequestedMultiStack() {
        // Controller starts on home while the valid link targets settings.
        val tester = PixelTester()
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        val home = destination("home-page") { }
        val settings = destination("settings-page") { }
        tester.pumpWidget(
            PixelMultiStackNavigator(
                stacks = listOf(
                    PixelTypedNavigatorStack("home", PixelRouteRequest(home, "home")),
                    PixelTypedNavigatorStack(
                        "settings",
                        PixelRouteRequest(settings, "settings"),
                    ),
                ),
                controller = controller,
                vsync = tester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        val resolver = PixelTypedDeepLinkResolver(
            listOf(
                PixelTypedDeepLinkRoute(
                    destination = settings,
                    matcher = PixelTypedDeepLinkMatcher { link ->
                        link.host == "external" && link.pathSegments == listOf("settings")
                    },
                    argumentDecoder = PixelDeepLinkArgumentDecoder { link ->
                        PixelDeepLinkDecoded(link.queryParameter("page") ?: "default")
                    },
                ),
            ),
        )

        val result = controller.handleTypedDeepLink(
            stackId = "settings",
            uri = "pixel://external/settings?page=privacy",
            resolver = resolver,
        )

        assertTrue(result is PixelTypedDeepLinkNavigated)
        assertEquals("settings", controller.activeStackId)
        assertEquals("privacy", controller.activeNavigatorState?.currentEntry?.arguments)
        assertEquals(1, controller.navigatorState("home")?.entries?.size)
        tester.dispose()
    }

    /** A root-package consumer can recreate a typed nested stack through a single-host controller. */
    @Test
    public fun typedNestedSnapshotRoundTripUsesOnlyRootPackageApi() {
        // Original tester and controller model the nested host before process recreation.
        val originalTester = PixelTester()
        val originalController = PixelNestedNavigatorController()
        // Original destination/registry pair owns encoding for this process only.
        val originalDestination = destination("external-nested-page") { }
        val originalRegistry = PixelRouteSnapshotRegistry(
            listOf(StringSnapshotAdapter(originalDestination)),
        )
        originalTester.pumpWidget(
            PixelNestedNavigator(
                initialRequest = PixelRouteRequest(originalDestination, "nested-root"),
                controller = originalController,
                vsync = originalTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        // Mounted root and detail carry independent IDs, arguments, and approved local state.
        val originalNavigator = checkNotNull(originalController.navigatorState)
        originalNavigator.currentEntry.stateBucket.write(ExternalCounterKey, 4)
        val originalDetail = originalNavigator.push(
            PixelRouteRequest(originalDestination, "nested-detail"),
        )
        originalDetail.stateBucket.write(ExternalCounterKey, 9)
        // Versioned bytes are captured only through the root-package controller typealias.
        val originalIds = originalNavigator.entries.map { entry -> entry.id }
        val encoded = originalController.persistentSnapshot(originalRegistry)
            as PixelNavigatorSnapshotEncoded
        originalTester.dispose()

        assertFalse(originalController.isAttached)
        assertNull(originalController.navigatorState)

        // Recreated process intentionally uses new controller, destination, and adapter instances.
        val recreatedTester = PixelTester()
        val recreatedController = PixelNestedNavigatorController()
        val recreatedDestination = destination("external-nested-page") { }
        val recreatedRegistry = PixelRouteSnapshotRegistry(
            listOf(StringSnapshotAdapter(recreatedDestination)),
        )
        recreatedTester.pumpWidget(
            PixelNestedNavigator(
                initialRequest = PixelRouteRequest(recreatedDestination, "fallback"),
                controller = recreatedController,
                vsync = recreatedTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        // Restore delegates to the mounted nested state and atomically replaces its fallback root.
        val fallbackNavigator = checkNotNull(recreatedController.navigatorState)
        val decoded = recreatedController.restorePersistentSnapshot(
            bytes = encoded.bytes,
            registry = recreatedRegistry,
        )
        val recreatedNavigator = checkNotNull(recreatedController.navigatorState)

        assertTrue(decoded is PixelNavigatorSnapshotDecoded)
        assertNotSame(originalNavigator, recreatedNavigator)
        assertSame(fallbackNavigator, recreatedNavigator)
        assertEquals(originalIds, recreatedNavigator.entries.map { entry -> entry.id })
        assertEquals(
            listOf("nested-root", "nested-detail"),
            recreatedNavigator.entries.map { entry -> entry.arguments },
        )
        assertEquals(4, recreatedNavigator.entries[0].stateBucket.read(ExternalCounterKey))
        assertEquals(9, recreatedNavigator.entries[1].stateBucket.read(ExternalCounterKey))
        recreatedTester.dispose()

        assertFalse(recreatedController.isAttached)
        assertNull(recreatedController.navigatorState)
    }

    /** Creates one typed String destination and optionally exposes its mounted controller. */
    private fun destination(
        id: String,
        onMounted: (PixelNavigatorState) -> Unit,
    ) = pixelRouteDestination<String, Unit>(
        id = id,
        transition = PixelRouteTransition.None,
    ) { context, scope ->
        onMounted(PixelNavigator.of(context))
        Text(scope.arguments)
    }

    /** Minimal external adapter proving public subclassing and versioned argument decode. */
    private class StringSnapshotAdapter(
        /** Exact current-process destination paired with this adapter. */
        destination: com.purride.pixelui.PixelRouteDestination<String, Unit>,
    ) : PixelRouteSnapshotAdapter<String, Unit>(destination, StringCodec) {
        /** Captures only the fixture's explicitly approved integer counter. */
        override fun encodeRouteState(
            entry: PixelRouteEntry<String, Unit>,
        ): Map<String, ByteArray> {
            // Missing counter remains a valid empty state payload for unrelated fixture tests.
            val counter = entry.stateBucket.read(ExternalCounterKey) ?: return emptyMap()
            return mapOf(
                ExternalCounterPayloadKey to counter.toString().toByteArray(StandardCharsets.UTF_8),
            )
        }

        /** Validates the empty state or one allowlisted integer counter payload. */
        override fun decodeRouteState(
            schemaVersion: Int,
            payloads: Map<String, ByteArray>,
        ): PixelRouteStateDecodeResult {
            if (schemaVersion != stateSchemaVersion) {
                return PixelRouteStateRejected("Unexpected external route-state schema")
            }
            if (payloads.isEmpty()) return PixelRouteStateDecoded(PixelRouteStateRestorer { })
            if (payloads.keys != setOf(ExternalCounterPayloadKey)) {
                return PixelRouteStateRejected("Unexpected external route-state key")
            }
            // Parsed counter is validated before the detached restorer writes it into a bucket.
            val counter = String(
                payloads.getValue(ExternalCounterPayloadKey),
                StandardCharsets.UTF_8,
            ).toIntOrNull()?.takeIf { value -> value >= 0 }
                ?: return PixelRouteStateRejected("Invalid external route-state counter")
            return PixelRouteStateDecoded(
                PixelRouteStateRestorer { bucket -> bucket.write(ExternalCounterKey, counter) },
            )
        }

        /** Stable wire key for the one approved external route-local value. */
        private companion object {
            /** Counter field name encoded inside the destination-owned state map. */
            const val ExternalCounterPayloadKey: String = "counter"
        }
    }

    /** Stable UTF-8 argument codec implemented outside the SDK source tree. */
    private object StringCodec : PixelRoutePayloadCodec<String> {
        /** Current external fixture schema. */
        override val schemaVersion: Int = 1

        /** Encodes the non-null String argument. */
        override fun encode(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

        /** Decodes only the declared schema and non-blank values. */
        override fun decode(
            schemaVersion: Int,
            payload: ByteArray,
        ): PixelRoutePayloadDecodeResult<String> {
            val value = String(payload, StandardCharsets.UTF_8)
            return if (schemaVersion == 1 && value.isNotBlank()) {
                PixelRoutePayloadDecoded(value)
            } else {
                PixelRoutePayloadRejected("Invalid external String payload")
            }
        }
    }

    /** Stable in-memory key used to verify destination-approved local-state restoration. */
    private companion object {
        /** Counter key shared by this fixture's current-process adapters and assertions. */
        val ExternalCounterKey = PixelRouteStateKey<Int>("external.counter")
    }
}
