package com.purride.pixelui

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM behavior contract for typed, persistable nested Navigator hosts. */
class PixelTypedNestedNavigatorTest {
    /**
     * Verifies process recreation preserves nested IDs, arguments, and approved local state while
     * an inactive maintained outer route still blocks the nested back dispatcher.
     */
    @Test
    fun typedNestedNavigatorRestoresVersionedSnapshotInsideMaintainedOuterRoute() {
        // Original tester represents the first process and owns all initial widget State objects.
        val originalTester = PixelTester()
        // Platform-facing dispatcher reveals whether inactive nested back handlers stay isolated.
        val originalBackDispatcher = PixelBackDispatcher()
        // Controller exposes only the Navigator mounted by the original nested host.
        val originalController = PixelNestedNavigatorController()
        // Original destination instance is paired with the registry used during capture.
        val originalNestedDestination = nestedDestination()
        // Registry allowlists both nested entries because they share the same stable destination.
        val originalRegistry = PixelRouteSnapshotRegistry(
            listOf(NestedPageSnapshotAdapter(originalNestedDestination)),
        )
        // Outer Navigator state is captured from its maintained typed root builder.
        var originalOuterState: PixelNavigatorState? = null
        // Maintained outer destination embeds the typed nested host and supplies its parent entry.
        val originalOuterDestination = outerDestination(
            tester = originalTester,
            nestedController = originalController,
            nestedDestination = originalNestedDestination,
            onMounted = { state -> originalOuterState = state },
        )

        originalTester.pumpWidget(
            PixelBackHost(
                dispatcher = originalBackDispatcher,
                child = PixelNavigator(
                    initialRequest = PixelRouteRequest(originalOuterDestination, "outer-root"),
                    vsync = originalTester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )

        // Mounted controller state must be distinct from the surrounding outer Navigator state.
        val originalInnerState = checkNotNull(originalController.navigatorState)
        // Concrete outer state drives a covering route that temporarily inactivates the parent.
        val originalOuterNavigator = checkNotNull(originalOuterState)
        // Nested root local state is explicitly approved by the destination snapshot adapter.
        originalInnerState.currentEntry.stateBucket.write(NestedPageCounterKey, 3)
        // Foreground nested entry supplies a second identity, argument, and local-state payload.
        val originalDetailEntry = originalInnerState.push(
            PixelRouteRequest(originalNestedDestination, NestedPageArguments("detail")),
        )
        originalDetailEntry.stateBucket.write(NestedPageCounterKey, 17)
        originalTester.pumpAndSettle()

        assertTrue(originalController.isAttached)
        assertNotSame(originalOuterNavigator, originalInnerState)
        assertEquals(2, originalInnerState.entries.size)
        assertTrue(originalBackDispatcher.hasRegisteredHandlers)

        // Covering the maintained outer root must disable only the hidden nested back bridge.
        originalOuterNavigator.push(textRoute("outer-cover", "OUTER COVER"))
        originalTester.pumpAndSettle()
        assertEquals(
            PixelRouteLifecycleState.Inactive,
            originalOuterNavigator.entries.first().lifecycleState,
        )
        assertTrue(originalBackDispatcher.handleBack())
        originalTester.pumpAndSettle()
        assertEquals(1, originalOuterNavigator.entries.size)
        assertEquals(2, originalInnerState.entries.size)
        assertEquals(PixelRouteLifecycleState.Active, originalOuterNavigator.currentEntry.lifecycleState)

        // Capture records exact entry IDs before every first-process State object is disposed.
        val originalEntryIds = originalInnerState.entries.map(PixelRouteEntry<*, *>::id)
        // Capture records argument order independently of any restored Widget identity.
        val originalArguments = originalInnerState.entries.map(PixelRouteEntry<*, *>::arguments)
        // Encoded versioned bytes are the only nested navigation data crossing recreation.
        val encoded = originalController.persistentSnapshot(originalRegistry)
            as PixelNavigatorSnapshotEncodeResult.Encoded
        originalTester.dispose()

        assertFalse(originalController.isAttached)
        assertEquals(null, originalController.navigatorState)

        // Recreated tester represents a fresh process with new controller and destination objects.
        val recreatedTester = PixelTester()
        // Recreated controller must receive only the second host's newly allocated Navigator state.
        val recreatedController = PixelNestedNavigatorController()
        // Stable destination ID and codec contract replace object identity across recreation.
        val recreatedNestedDestination = nestedDestination()
        // Recreated registry validates and migrates all untrusted snapshot payloads before install.
        val recreatedRegistry = PixelRouteSnapshotRegistry(
            listOf(NestedPageSnapshotAdapter(recreatedNestedDestination)),
        )
        // Recreated outer state callback proves the nested state remains a separate Navigator.
        var recreatedOuterState: PixelNavigatorState? = null
        // New outer destination mounts a fallback nested root before restoration is requested.
        val recreatedOuterDestination = outerDestination(
            tester = recreatedTester,
            nestedController = recreatedController,
            nestedDestination = recreatedNestedDestination,
            onMounted = { state -> recreatedOuterState = state },
        )
        recreatedTester.pumpWidget(
            PixelNavigator(
                initialRequest = PixelRouteRequest(recreatedOuterDestination, "outer-fallback"),
                vsync = recreatedTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )

        // Versioned codec validates the complete snapshot before exposing a one-shot restore plan.
        val decoded = PixelNavigatorSnapshotCodec().decode(
            bytes = encoded.bytes,
            registry = recreatedRegistry,
        )
        // Controller's restore API installs the validated plan only into its mounted child state.
        val restored = decoded is PixelNavigatorSnapshotDecodeResult.Decoded &&
            recreatedController.restore(decoded.plan)
        recreatedTester.pumpFrame(1)

        // Restored state contains no object identity from the original process.
        val recreatedInnerState = checkNotNull(recreatedController.navigatorState)
        assertTrue(decoded is PixelNavigatorSnapshotDecodeResult.Decoded)
        assertTrue(restored)
        assertNotSame(originalInnerState, recreatedInnerState)
        assertNotSame(checkNotNull(recreatedOuterState), recreatedInnerState)
        assertEquals(originalEntryIds, recreatedInnerState.entries.map(PixelRouteEntry<*, *>::id))
        assertEquals(originalArguments, recreatedInnerState.entries.map(PixelRouteEntry<*, *>::arguments))
        assertEquals(3, recreatedInnerState.entries[0].stateBucket.read(NestedPageCounterKey))
        assertEquals(17, recreatedInnerState.entries[1].stateBucket.read(NestedPageCounterKey))
        assertEquals(PixelRouteLifecycleState.Inactive, recreatedInnerState.entries[0].lifecycleState)
        assertEquals(PixelRouteLifecycleState.Active, recreatedInnerState.entries[1].lifecycleState)
        assertTrue(recreatedTester.exists(find.byText("detail:17")))

        recreatedTester.dispose()
        assertFalse(recreatedController.isAttached)
        assertEquals(null, recreatedController.navigatorState)
    }

    /** Verifies one controller cannot ambiguously attach to two simultaneously mounted hosts. */
    @Test
    fun typedNestedControllerRejectsSecondHostAndReleasesTheFirstOnDispose() {
        // Shared controller is intentionally offered to two independent mounted widget trees.
        val controller = PixelNestedNavigatorController()
        // Stable destination may create separate entries but never makes controller ownership safe.
        val destination = nestedDestination()
        // First tester owns the valid host attachment.
        val firstTester = PixelTester()
        // Second tester attempts the forbidden simultaneous attachment.
        val secondTester = PixelTester()
        firstTester.pumpWidget(
            PixelNestedNavigator(
                initialRequest = PixelRouteRequest(destination, NestedPageArguments("first")),
                controller = controller,
                vsync = firstTester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 32,
            logicalHeight = 12,
        )
        // First mounted state remains the authoritative target after the rejected second mount.
        val firstNavigatorState = checkNotNull(controller.navigatorState)

        // Second mount must fail at controller binding before it can attach a competing state.
        val ownershipFailure = assertThrows(IllegalStateException::class.java) {
            secondTester.pumpWidget(
                PixelNestedNavigator(
                    initialRequest = PixelRouteRequest(
                        destination,
                        NestedPageArguments("second"),
                    ),
                    controller = controller,
                    vsync = secondTester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )
        }

        assertTrue(ownershipFailure.message.orEmpty().contains("another host"))
        assertTrue(controller.isAttached)
        assertSame(firstNavigatorState, controller.navigatorState)

        firstTester.dispose()
        assertFalse(controller.isAttached)
        assertEquals(null, controller.navigatorState)
    }

    /** Creates the typed nested destination shared by capture and recreation processes. */
    private fun nestedDestination(): PixelRouteDestination<NestedPageArguments, Unit> {
        return pixelRouteDestination(
            id = "typed-nested-page",
            transition = PixelRouteTransition.None,
        ) { _, scope ->
            // Approved counter value is read from the concrete restored entry's local bucket.
            val counter = scope.stateBucket.read(NestedPageCounterKey) ?: 0
            Text("${scope.arguments.label}:$counter")
        }
    }

    /** Creates a maintained typed outer route that gates nested back with its concrete entry. */
    private fun outerDestination(
        tester: PixelTester,
        nestedController: PixelNestedNavigatorController,
        nestedDestination: PixelRouteDestination<NestedPageArguments, Unit>,
        onMounted: (PixelNavigatorState) -> Unit,
    ): PixelRouteDestination<String, Unit> {
        return pixelRouteDestination(
            id = "typed-nested-outer",
            maintainState = true,
            transition = PixelRouteTransition.None,
        ) { context, scope ->
            onMounted(PixelNavigator.of(context))
            PixelNestedNavigator(
                initialRequest = PixelRouteRequest(
                    nestedDestination,
                    NestedPageArguments("root"),
                ),
                controller = nestedController,
                vsync = tester.vsync,
                defaultTransition = PixelRouteTransition.None,
                parentEntry = scope.entry,
            )
        }
    }

    /** 为外层 Navigator 创建一个确定性的遮盖路由。 */
    private fun textRoute(name: String, text: String): PixelRouteRequest<Unit, Any?> {
        return testRouteRequest(
            name = name,
            transition = PixelRouteTransition.None,
            builder = { Text(text) },
        )
    }
}

/** Arguments persisted for one typed nested page entry. */
private data class NestedPageArguments(
    /** Human-readable label encoded by the destination-owned payload codec. */
    val label: String,
)

/** Stable route-local key explicitly allowlisted by [NestedPageSnapshotAdapter]. */
private val NestedPageCounterKey = PixelRouteStateKey<Int>("typedNested.counter")

/** UTF-8 codec with one explicit schema for typed nested page arguments. */
private object NestedPageArgumentsCodec : PixelRoutePayloadCodec<NestedPageArguments> {
    /** Current argument payload schema emitted by [encode]. */
    override val schemaVersion: Int = 1

    /** Encodes only the stable non-blank page label. */
    override fun encode(value: NestedPageArguments): ByteArray {
        require(value.label.isNotBlank()) { "Nested page label must not be blank" }
        return value.label.toByteArray(StandardCharsets.UTF_8)
    }

    /** Decodes the supported schema and rejects blank or foreign payloads. */
    override fun decode(
        schemaVersion: Int,
        payload: ByteArray,
    ): PixelRoutePayloadDecodeResult<NestedPageArguments> {
        // Decoded label remains destination-owned data until validation succeeds.
        val label = String(payload, StandardCharsets.UTF_8)
        return if (schemaVersion == this.schemaVersion && label.isNotBlank()) {
            PixelRoutePayloadDecodeResult.Decoded(NestedPageArguments(label))
        } else {
            PixelRoutePayloadDecodeResult.Rejected("Invalid typed nested page arguments")
        }
    }
}

/** Snapshot adapter that persists only one validated integer from each nested entry. */
private class NestedPageSnapshotAdapter(
    /** Exact current-process destination accepted during capture or reconstruction. */
    destination: PixelRouteDestination<NestedPageArguments, Unit>,
) : PixelRouteSnapshotAdapter<NestedPageArguments, Unit>(
    destination = destination,
    argumentCodec = NestedPageArgumentsCodec,
) {
    /** Current schema for the allowlisted nested counter payload. */
    override val stateSchemaVersion: Int = 1

    /** Captures only the approved counter and never serializes the complete state bucket. */
    override fun encodeRouteState(
        entry: PixelRouteEntry<NestedPageArguments, Unit>,
    ): Map<String, ByteArray> {
        // Missing counter is represented by the adapter's valid empty state payload.
        val counter = entry.stateBucket.read(NestedPageCounterKey) ?: return emptyMap()
        return mapOf(
            NestedCounterPayloadKey to counter.toString().toByteArray(StandardCharsets.UTF_8),
        )
    }

    /** Validates state keys, schema, and integer bytes before creating a detached restorer. */
    override fun decodeRouteState(
        schemaVersion: Int,
        payloads: Map<String, ByteArray>,
    ): PixelRouteStateDecodeResult {
        if (schemaVersion != stateSchemaVersion) {
            return PixelRouteStateDecodeResult.Rejected("Unsupported typed nested state schema")
        }
        if (payloads.isEmpty()) {
            return PixelRouteStateDecodeResult.Decoded(PixelRouteStateRestorer { })
        }
        if (payloads.keys != setOf(NestedCounterPayloadKey)) {
            return PixelRouteStateDecodeResult.Rejected("Unknown typed nested state key")
        }
        // Parsed counter is bounded before it can enter a restored state bucket.
        val counter = String(
            payloads.getValue(NestedCounterPayloadKey),
            StandardCharsets.UTF_8,
        ).toIntOrNull()?.takeIf { value -> value >= 0 }
            ?: return PixelRouteStateDecodeResult.Rejected("Invalid typed nested counter")
        return PixelRouteStateDecodeResult.Decoded(
            PixelRouteStateRestorer { bucket -> bucket.write(NestedPageCounterKey, counter) },
        )
    }

    /** Stable wire key for the one allowed route-local state value. */
    private companion object {
        /** Counter payload key scoped to this destination adapter. */
        const val NestedCounterPayloadKey: String = "counter"
    }
}
