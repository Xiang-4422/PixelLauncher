package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-2 contract tests for versioned, typed, atomic Navigator process restoration. */
class PixelNavigatorSnapshotCodecTest {
    /** Verifies IDs, typed arguments, current page, local state, and allocator continuity. */
    @Test
    fun processDeathRoundTripPreservesTypedStackAndAdvancesEntryIdAllocator() {
        // One stable destination instance represents application code recreated around process state.
        val destination = testDestination("detail")
        // One adapter explicitly owns argument migration and the local counter allowlist.
        val adapter = TestRouteSnapshotAdapter(destination)
        // Registry is the only set of destination IDs allowed to restore.
        val registry = PixelRouteSnapshotRegistry(listOf(adapter))
        // Original process starts with a typed root so every real entry is persistable.
        val original = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments(label = "root", count = 1)),
        )
        // Root-local state is independently captured through its entry bucket.
        val originalRoot = typedEntry(original.entries.single())
        originalRoot.stateBucket.write(CounterStateKey, 11)
        // Foreground detail reuses the same destination but owns independent arguments and state.
        val originalDetail = original.push(
            PixelRouteRequest(destination, TestArguments(label = "detail", count = 2)),
        )
        originalDetail.stateBucket.write(CounterStateKey, 22)
        // Persistent encoding is detached from Android and therefore directly JVM-testable.
        val encoded = requireEncoded(original.persistentSnapshot(registry))

        // A recreated process first mounts its safe typed root before applying saved state.
        val recreatedDestination = testDestination("detail")
        // Recreated registry deliberately uses a new destination/adapter object with the same stable ID.
        val recreatedRegistry = PixelRouteSnapshotRegistry(
            listOf(TestRouteSnapshotAdapter(recreatedDestination)),
        )
        val recreated = PixelNavigatorState(
            PixelRouteRequest(recreatedDestination, TestArguments(label = "fallback", count = 0)),
        )
        // Full decode and installation form one public operation.
        val restored = recreated.restorePersistentSnapshot(encoded.bytes, recreatedRegistry)
        assertTrue(restored is PixelNavigatorSnapshotDecodeResult.Decoded)

        // Original IDs and root-to-foreground order survive process death exactly.
        assertEquals(listOf(originalRoot.id, originalDetail.id), recreated.entries.map { it.id })
        assertEquals(originalDetail.id, recreated.currentEntry.id)
        // Typed argument values are recovered through the registered versioned codec.
        val restoredRoot = typedEntry(recreated.entries[0])
        val restoredDetail = typedEntry(recreated.entries[1])
        assertEquals(TestArguments("root", 1), restoredRoot.arguments)
        assertEquals(TestArguments("detail", 2), restoredDetail.arguments)
        // Only adapter-approved route-local counter values are restored.
        assertEquals(11, restoredRoot.stateBucket.read(CounterStateKey))
        assertEquals(22, restoredDetail.stateBucket.read(CounterStateKey))
        assertEquals(PixelRouteLifecycleState.Inactive, restoredRoot.lifecycleState)
        assertEquals(PixelRouteLifecycleState.Active, restoredDetail.lifecycleState)

        // A future push cannot collide with any preserved entry identity.
        val future = recreated.push(
            PixelRouteRequest(recreatedDestination, TestArguments(label = "future", count = 3)),
        )
        assertEquals(originalDetail.id.value + 1L, future.id.value)
    }

    /** Verifies the public typed-root factory produces a stack the persistent codec can encode. */
    @Test
    fun typedNavigatorFactoryAvoidsAnUnpersistableLegacyRoot() {
        // Test host supplies deterministic frame scheduling for the public widget factory.
        val tester = PixelTester()
        // Destination builder captures the mounted Navigator controller for assertions.
        var mountedNavigator: PixelNavigatorState? = null
        // Public destination used by the typed root widget.
        val destination = pixelRouteDestination<TestArguments, Unit>(id = "typed-root") { context, scope ->
            mountedNavigator = PixelNavigator.of(context)
            Text(scope.arguments.label)
        }
        // Explicit restoration adapter matches the exact destination instance.
        val registry = PixelRouteSnapshotRegistry(listOf(TestRouteSnapshotAdapter(destination)))
        // The old PixelNavigator constructor still exists; typed() adds a source-level typed root path.
        val widget = PixelNavigator.typed(
            initialRequest = PixelRouteRequest(destination, TestArguments("home", 7)),
            vsync = tester.vsync,
        )

        tester.pumpWidget(widget, logicalWidth = 16, logicalHeight = 8)

        // Mounted root is a real typed entry rather than a legacy PixelRoute wrapper.
        val navigator = checkNotNull(mountedNavigator)
        assertEquals(TestArguments("home", 7), typedEntry(navigator.currentEntry).arguments)
        assertTrue(navigator.persistentSnapshot(registry) is PixelNavigatorSnapshotEncodeResult.Encoded)
        tester.dispose()
    }

    /** Verifies schema 1 and destination argument schema 1 migrate without inventing local state. */
    @Test
    fun legacyOuterAndArgumentSchemasMigrateSafely() {
        // Current application destination remains stable across an SDK schema upgrade.
        val destination = testDestination("migrating")
        // Current adapter accepts its documented legacy argument schema.
        val registry = PixelRouteSnapshotRegistry(listOf(TestRouteSnapshotAdapter(destination)))
        // Schema 1 stored entry IDs, destination IDs, and arguments but no local state or current ID.
        val legacyBytes = PixelNavigatorSnapshotCodec().encodeLegacySchema1ForTest(
            listOf(
                rawEntry(
                    id = 41L,
                    destinationId = destination.id,
                    argumentSchemaVersion = 1,
                    argumentPayload = "legacy-label".toByteArray(StandardCharsets.UTF_8),
                ),
            ),
        )
        // New process begins from a safe typed fallback root.
        val navigator = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("fallback", 0)),
        )

        val decoded = navigator.restorePersistentSnapshot(legacyBytes, registry)

        assertTrue(decoded is PixelNavigatorSnapshotDecodeResult.Decoded)
        val plan: PixelNavigatorRestorePlan =
            (decoded as PixelNavigatorSnapshotDecodeResult.Decoded).plan
        // Public inspection exposes persisted metadata without leaking decoded arguments.
        val restoredInspection: PixelNavigatorRestoredEntryInspection = plan.entries.single()
        assertTrue(plan.migratedFromOlderSchema)
        assertEquals(1, plan.sourceSchemaVersion)
        assertEquals(destination.id, restoredInspection.destinationId)
        assertEquals(41L, navigator.currentEntry.id.value)
        assertEquals(TestArguments("legacy-label", 0), typedEntry(navigator.currentEntry).arguments)
        assertEquals(null, typedEntry(navigator.currentEntry).stateBucket.read(CounterStateKey))
        // Restored maximum identity moves the allocator forward rather than restarting from one.
        val next = navigator.push(
            PixelRouteRequest(destination, TestArguments("next", 1)),
        )
        assertEquals(42L, next.id.value)
    }

    /** Verifies unknown destinations and corrupt bytes never partially replace the live stack. */
    @Test
    fun unknownDestinationAndCorruptionLeaveCurrentStackUnchanged() {
        // Known source destination creates a valid checksummed snapshot.
        val destination = testDestination("known")
        // Matching source registry can encode the live typed root.
        val sourceRegistry = PixelRouteSnapshotRegistry(listOf(TestRouteSnapshotAdapter(destination)))
        // Source stack supplies bytes for adversarial restore attempts.
        val source = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("source", 1)),
        )
        val encoded = requireEncoded(source.persistentSnapshot(sourceRegistry))
        // Target stack identity must remain unchanged after each rejection.
        val target = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("safe", 2)),
        )
        val safeEntry = target.currentEntry

        // Empty registry makes every encoded destination unknown.
        val unknownResult = target.restorePersistentSnapshot(
            encoded.bytes,
            PixelRouteSnapshotRegistry(emptyList()),
        )
        assertRejected(unknownResult, PixelNavigatorSnapshotFailureReason.UnknownDestination)
        assertSame(safeEntry, target.currentEntry)

        // A checksum mutation is rejected before destination codecs run.
        val corruptBytes = encoded.bytes.also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        }
        val corruptResult = target.restorePersistentSnapshot(corruptBytes, sourceRegistry)
        assertRejected(corruptResult, PixelNavigatorSnapshotFailureReason.CorruptData)
        assertSame(safeEntry, target.currentEntry)
        assertEquals(TestArguments("safe", 2), typedEntry(target.currentEntry).arguments)
    }

    /** Verifies argument and route-state migration failures reject the complete plan. */
    @Test
    fun rejectedArgumentOrRouteStateNeverProducesARestorationPlan() {
        // Destination participates in both rejection scenarios.
        val destination = testDestination("rejecting")
        // Normal adapter is used to produce a valid current snapshot with route-local state.
        val acceptingAdapter = TestRouteSnapshotAdapter(destination)
        // Raw legacy entry contains an invalid empty argument for schema 1.
        val invalidArguments = PixelNavigatorSnapshotCodec().encodeLegacySchema1ForTest(
            listOf(
                rawEntry(
                    id = 8L,
                    destinationId = destination.id,
                    argumentSchemaVersion = 1,
                    argumentPayload = byteArrayOf(),
                ),
            ),
        )
        val argumentResult = PixelNavigatorSnapshotCodec().decode(
            invalidArguments,
            PixelRouteSnapshotRegistry(listOf(acceptingAdapter)),
        )
        assertRejected(argumentResult, PixelNavigatorSnapshotFailureReason.ArgumentPayloadRejected)

        // A valid source entry opts one counter into persistence.
        val source = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("state", 1)),
        )
        typedEntry(source.currentEntry).stateBucket.write(CounterStateKey, 9)
        val encoded = requireEncoded(
            source.persistentSnapshot(PixelRouteSnapshotRegistry(listOf(acceptingAdapter))),
        )
        // Updated application adapter explicitly rejects the old state schema.
        val rejectingAdapter = RejectingStateSnapshotAdapter(destination)
        val stateResult = PixelNavigatorSnapshotCodec().decode(
            encoded.bytes,
            PixelRouteSnapshotRegistry(listOf(rejectingAdapter)),
        )
        assertRejected(stateResult, PixelNavigatorSnapshotFailureReason.RouteStateRejected)
    }

    /** Verifies duplicate identities, unsupported schemas, truncation, and size limits are total. */
    @Test
    fun malformedAndUnsupportedEnvelopesReturnStructuredFailures() {
        // Destination registry allows parsing to reach stack-level identity validation.
        val destination = testDestination("limits")
        // Matching adapter owns the destination's migration behavior.
        val registry = PixelRouteSnapshotRegistry(listOf(TestRouteSnapshotAdapter(destination)))
        // Schema 1 duplicate IDs must not become two live entries sharing identity.
        val duplicateIds = PixelNavigatorSnapshotCodec().encodeLegacySchema1ForTest(
            listOf(
                rawEntry(5L, destination.id, 1, "first".toByteArray()),
                rawEntry(5L, destination.id, 1, "second".toByteArray()),
            ),
        )
        assertRejected(
            PixelNavigatorSnapshotCodec().decode(duplicateIds, registry),
            PixelNavigatorSnapshotFailureReason.InvalidStack,
        )

        // A valid envelope with an unknown outer schema is rejected before payload parsing.
        val futureSchema = PixelNavigatorSnapshotCodec().encodeEnvelopeForTest(99, byteArrayOf())
        assertRejected(
            PixelNavigatorSnapshotCodec().decode(futureSchema, registry),
            PixelNavigatorSnapshotFailureReason.UnsupportedSchema,
        )
        // Arbitrary truncation returns corruption rather than EOF escaping to the caller.
        assertRejected(
            PixelNavigatorSnapshotCodec().decode(byteArrayOf(1, 2, 3), registry),
            PixelNavigatorSnapshotFailureReason.CorruptData,
        )
        // Oversized input is rejected before any payload-sized allocation or adapter call.
        assertRejected(
            PixelNavigatorSnapshotCodec().decode(ByteArray(1_048_577), registry),
            PixelNavigatorSnapshotFailureReason.LimitExceeded,
        )
    }

    /** Verifies detached state buckets in a decoded plan cannot be mounted twice. */
    @Test
    fun restorationPlanIsOneShot() {
        // One typed source stack produces a valid decoded plan.
        val destination = testDestination("one-shot")
        // Registry is shared only to make the two targets decode the same destination definition.
        val registry = PixelRouteSnapshotRegistry(listOf(TestRouteSnapshotAdapter(destination)))
        // Source Navigator supplies persistent bytes.
        val source = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("source", 1)),
        )
        val encoded = requireEncoded(source.persistentSnapshot(registry))
        // Decode is deliberately separated from installation to exercise plan ownership.
        val decoded = PixelNavigatorSnapshotCodec().decode(encoded.bytes, registry)
            as PixelNavigatorSnapshotDecodeResult.Decoded
        // First target claims every detached entry and bucket.
        val firstTarget = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("first", 2)),
        )
        assertTrue(firstTarget.restore(decoded.plan))
        // Second target must keep its own safe root when the same plan is reused.
        val secondTarget = PixelNavigatorState(
            PixelRouteRequest(destination, TestArguments("second", 3)),
        )
        val secondRoot = secondTarget.currentEntry
        assertFalse(secondTarget.restore(decoded.plan))
        assertSame(secondRoot, secondTarget.currentEntry)
        assertNotSame(firstTarget.currentEntry, secondTarget.currentEntry)
    }

    /** Creates one reusable destination with deterministic test UI. */
    private fun testDestination(id: String): PixelRouteDestination<TestArguments, Unit> {
        return pixelRouteDestination(id = id) { _, scope -> Text(scope.arguments.label) }
    }

    /** Narrows one test entry to this suite's known destination types. */
    @Suppress("UNCHECKED_CAST")
    private fun typedEntry(entry: PixelRouteEntry<*, *>): PixelRouteEntry<TestArguments, Unit> {
        return entry as PixelRouteEntry<TestArguments, Unit>
    }

    /** Returns encoded bytes or fails the test with the structured rejection. */
    private fun requireEncoded(
        result: PixelNavigatorSnapshotEncodeResult,
    ): PixelNavigatorSnapshotEncodeResult.Encoded {
        if (result is PixelNavigatorSnapshotEncodeResult.Encoded) return result
        val rejected = result as PixelNavigatorSnapshotEncodeResult.Rejected
        error("Snapshot encode rejected: ${rejected.failure}")
    }

    /** Asserts a decode result has the expected stable failure category. */
    private fun assertRejected(
        result: PixelNavigatorSnapshotDecodeResult,
        reason: PixelNavigatorSnapshotFailureReason,
    ) {
        assertTrue(result is PixelNavigatorSnapshotDecodeResult.Rejected)
        val failure: PixelNavigatorSnapshotFailure =
            (result as PixelNavigatorSnapshotDecodeResult.Rejected).failure
        assertEquals(reason, failure.reason)
    }

    /** Creates one raw schema-migration fixture entry with no route-local state. */
    private fun rawEntry(
        id: Long,
        destinationId: String,
        argumentSchemaVersion: Int,
        argumentPayload: ByteArray,
    ): RawPersistentRouteEntry {
        return RawPersistentRouteEntry(
            entryId = PixelRouteEntryId(id),
            destinationId = destinationId,
            argumentSchemaVersion = argumentSchemaVersion,
            argumentPayload = argumentPayload,
            stateSchemaVersion = 1,
            statePayloads = emptyMap(),
        )
    }

    /** Typed arguments used to prove destination-specific migration. */
    private data class TestArguments(
        /** Human-readable page label. */
        val label: String,
        /** Additional typed value absent from legacy argument schema 1. */
        val count: Int,
    )

    /** Versioned argument codec whose schema 1 migration supplies the new count field. */
    private object TestArgumentsCodec : PixelRoutePayloadCodec<TestArguments> {
        /** Current test argument payload schema. */
        override val schemaVersion: Int = 2

        /** Encodes both current typed fields into a compact deterministic fixture payload. */
        override fun encode(value: TestArguments): ByteArray {
            return "${value.label}\u0000${value.count}".toByteArray(StandardCharsets.UTF_8)
        }

        /** Decodes schema 2 or migrates schema 1's label-only representation. */
        override fun decode(
            schemaVersion: Int,
            payload: ByteArray,
        ): PixelRoutePayloadDecodeResult<TestArguments> {
            val text = String(payload, StandardCharsets.UTF_8)
            return when (schemaVersion) {
                1 -> if (text.isNotBlank()) {
                    PixelRoutePayloadDecodeResult.Decoded(TestArguments(text, 0))
                } else {
                    PixelRoutePayloadDecodeResult.Rejected("Legacy label must not be blank")
                }
                2 -> {
                    val separator = text.lastIndexOf('\u0000')
                    val label = if (separator >= 0) text.substring(0, separator) else ""
                    val count = if (separator >= 0) text.substring(separator + 1).toIntOrNull() else null
                    if (label.isBlank() || count == null) {
                        PixelRoutePayloadDecodeResult.Rejected("Current arguments are malformed")
                    } else {
                        PixelRoutePayloadDecodeResult.Decoded(TestArguments(label, count))
                    }
                }
                else -> PixelRoutePayloadDecodeResult.Rejected(
                    "Unsupported argument schema $schemaVersion",
                )
            }
        }
    }

    /** Normal adapter that persists only the explicitly approved integer counter. */
    private open class TestRouteSnapshotAdapter(
        /** Exact destination instance paired with this adapter. */
        destination: PixelRouteDestination<TestArguments, Unit>,
    ) : PixelRouteSnapshotAdapter<TestArguments, Unit>(destination, TestArgumentsCodec) {
        /** Current route-local counter schema. */
        override val stateSchemaVersion: Int = 2

        /** Encodes the approved counter and ignores every other in-memory bucket value. */
        override fun encodeRouteState(
            entry: PixelRouteEntry<TestArguments, Unit>,
        ): Map<String, ByteArray> {
            val counter = entry.stateBucket.read(CounterStateKey) ?: return emptyMap()
            return mapOf(CounterPayloadKey to counter.toString().toByteArray(StandardCharsets.UTF_8))
        }

        /** Validates current counter bytes or accepts the empty local state of outer schema 1. */
        override fun decodeRouteState(
            schemaVersion: Int,
            payloads: Map<String, ByteArray>,
        ): PixelRouteStateDecodeResult {
            if (schemaVersion == 1 && payloads.isEmpty()) {
                return PixelRouteStateDecodeResult.Decoded(PixelRouteStateRestorer { })
            }
            if (schemaVersion == stateSchemaVersion && payloads.isEmpty()) {
                return PixelRouteStateDecodeResult.Decoded(PixelRouteStateRestorer { })
            }
            if (schemaVersion != stateSchemaVersion || payloads.keys != setOf(CounterPayloadKey)) {
                return PixelRouteStateDecodeResult.Rejected("Unsupported counter state schema")
            }
            val counter = String(
                checkNotNull(payloads[CounterPayloadKey]),
                StandardCharsets.UTF_8,
            ).toIntOrNull() ?: return PixelRouteStateDecodeResult.Rejected("Counter is not an integer")
            return PixelRouteStateDecodeResult.Decoded(
                PixelRouteStateRestorer { bucket -> bucket.write(CounterStateKey, counter) },
            )
        }
    }

    /** Adapter fixture that simulates an application dropping support for an old state schema. */
    private class RejectingStateSnapshotAdapter(
        /** Exact destination instance paired with this rejecting adapter. */
        destination: PixelRouteDestination<TestArguments, Unit>,
    ) : TestRouteSnapshotAdapter(destination) {
        /** Rejects every route-local state payload for atomic fallback coverage. */
        override fun decodeRouteState(
            schemaVersion: Int,
            payloads: Map<String, ByteArray>,
        ): PixelRouteStateDecodeResult {
            return PixelRouteStateDecodeResult.Rejected(
                "State schema $schemaVersion is no longer supported",
            )
        }
    }

    private companion object {
        /** Identity-based in-memory key restored only through the adapter allowlist. */
        val CounterStateKey: PixelRouteStateKey<Int> = PixelRouteStateKey("counter")

        /** Stable wire key owned by [TestRouteSnapshotAdapter]. */
        const val CounterPayloadKey: String = "counter"
    }
}
