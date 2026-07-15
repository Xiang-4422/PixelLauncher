package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M2-1 behavior contract for entry-based navigation, typed results, lifecycle, and inspection.
 *
 * These tests intentionally exercise the public entry model through [PixelNavigatorState] while
 * using package-visible transition settlement only to make asynchronous disposal deterministic.
 */
class PixelRouteEntryNavigationTest {
    /** Verifies `maintainState=true` keeps the exact inactive StatefulWidget state instance alive. */
    @Test
    fun maintainedEntriesKeepStateInstanceAndValueAcrossDuplicateDestinationPushAndPop() {
        // Real widget hosting is required to prove element/state retention rather than bucket-only behavior.
        val tester = PixelTester()
        // Navigator reference is captured from the mounted compatibility root.
        var navigator: PixelNavigatorState? = null
        // Every probe initialization is recorded by its typed route argument.
        val probeStates = mutableMapOf<String, MutableList<RouteStateProbeState>>()
        // Root provides the BuildContext used to access the mounted navigator state.
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        // One reusable destination is pushed twice with independent entries and retained subtrees.
        val destination = pixelRouteDestination<String, Unit>(
            id = "retained-probe",
            maintainState = true,
            transition = PixelRouteTransition.Fade,
        ) { _, scope ->
            RouteStateProbe(
                label = scope.arguments,
                onReady = { state ->
                    probeStates.getOrPut(scope.arguments) { mutableListOf() } += state
                },
            )
        }

        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)
        // Mounted navigator is available after the root's first build.
        val state = checkNotNull(navigator)
        // Lower duplicate entry receives the first probe state instance.
        val lowerEntry = state.push(PixelRouteRequest(destination, "lower"))
        tester.pumpAndSettle()
        // The first and only lower probe is the state identity that must survive inactivity.
        val lowerProbe = checkNotNull(probeStates["lower"]?.singleOrNull())
        lowerProbe.increment()
        tester.pumpFrame(1)
        assertTrue(tester.exists(find.byText("lower:1")))

        // Pushing the same destination creates a new entry without unmounting the lower probe.
        val topEntry = state.push(PixelRouteRequest(destination, "top"))
        tester.pumpAndSettle()
        // The top probe is independent from the lower retained state.
        val topProbe = checkNotNull(probeStates["top"]?.singleOrNull())

        assertNotEquals(lowerEntry.id, topEntry.id)
        assertNotSame(lowerProbe, topProbe)
        assertFalse(lowerProbe.isDisposed)
        assertEquals(1, lowerProbe.counter)
        assertFalse(tester.exists(find.byText("lower:1")))
        assertTrue(tester.exists(find.byText("top:0")))

        assertTrue(state.complete(topEntry, Unit))
        tester.pumpAndSettle()

        assertSame(lowerProbe, checkNotNull(probeStates["lower"]?.singleOrNull()))
        assertFalse(lowerProbe.isDisposed)
        assertEquals(1, lowerProbe.counter)
        assertTrue(topProbe.isDisposed)
        assertTrue(tester.exists(find.byText("lower:1")))
        tester.dispose()
    }

    /** Verifies `maintainState=false` releases its subtree and clears its local state bucket. */
    @Test
    fun nonMaintainedEntryRebuildsStateAndDropsBucketWhenItBecomesInactive() {
        // Real widget hosting observes StatefulWidget disposal and later recreation.
        val tester = PixelTester()
        // Navigator reference is captured by the mounted root route.
        var navigator: PixelNavigatorState? = null
        // Probe instances are appended each time the non-maintained entry is rebuilt.
        val probeStates = mutableMapOf<String, MutableList<RouteStateProbeState>>()
        // Root retains access to the Navigator while typed entries are covered and revealed.
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        // This destination explicitly opts out of inactive subtree and bucket retention.
        val destination = pixelRouteDestination<String, Unit>(
            id = "ephemeral-probe",
            maintainState = false,
            transition = PixelRouteTransition.Fade,
        ) { _, scope ->
            RouteStateProbe(
                label = scope.arguments,
                onReady = { state ->
                    probeStates.getOrPut(scope.arguments) { mutableListOf() } += state
                },
            )
        }
        // Typed bucket key makes the retention policy observable independently of widget state.
        val bucketKey = PixelRouteStateKey<String>("ephemeral")

        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)
        // Mounted navigator is required before typed requests can be pushed.
        val state = checkNotNull(navigator)
        // Lower entry initially owns one state instance and one local bucket value.
        val lowerEntry = state.push(PixelRouteRequest(destination, "lower"))
        tester.pumpAndSettle()
        // Initial lower probe will be disposed when the entry becomes inactive.
        val firstLowerProbe = checkNotNull(probeStates["lower"]?.singleOrNull())
        firstLowerProbe.increment()
        lowerEntry.stateBucket.write(bucketKey, "discard-me")

        // Covering the lower entry releases both its subtree and route-local retained values.
        val topEntry = state.push(PixelRouteRequest(destination, "top"))
        tester.pumpAndSettle()

        assertTrue(firstLowerProbe.isDisposed)
        assertNull(lowerEntry.stateBucket.read(bucketKey))

        assertTrue(state.complete(topEntry, Unit))
        tester.pumpAndSettle()
        // Returning to the lower entry creates a new state object with its initial counter.
        val rebuiltLowerProbe = checkNotNull(probeStates["lower"]?.lastOrNull())

        assertEquals(2, probeStates["lower"]?.size)
        assertNotSame(firstLowerProbe, rebuiltLowerProbe)
        assertEquals(0, rebuiltLowerProbe.counter)
        assertFalse(rebuiltLowerProbe.isDisposed)
        assertTrue(tester.exists(find.byText("lower:0")))
        tester.dispose()
    }

    /** Verifies keyed sibling reconciliation preserves states after middle removal and replace. */
    @Test
    fun keyedEntryReconciliationPreservesUnaffectedAndReplacementStatesAcrossIndexChanges() {
        // Hosted rendering is required to observe sibling element reuse after stack index changes.
        val tester = PixelTester()
        // Navigator reference is captured by the root route's mounted context.
        var navigator: PixelNavigatorState? = null
        // Probe histories reveal any unintended state recreation caused by sibling reordering.
        val probeStates = mutableMapOf<String, MutableList<RouteStateProbeState>>()
        // Root remains below all typed entries during the reconciliation scenarios.
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        // Reusable maintained destination uses Fade so outgoing and incoming entries coexist.
        val destination = pixelRouteDestination<String, Unit>(
            id = "keyed-probe",
            maintainState = true,
            transition = PixelRouteTransition.Fade,
        ) { _, scope ->
            RouteStateProbe(
                label = scope.arguments,
                onReady = { state ->
                    probeStates.getOrPut(scope.arguments) { mutableListOf() } += state
                },
            )
        }

        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)
        // Mounted state coordinates middle removal and animated replacement.
        val state = checkNotNull(navigator)
        // Lower entry will be removed while a later sibling remains current.
        val lowerEntry = state.push(PixelRouteRequest(destination, "lower"))
        tester.pumpAndSettle()
        // Current entry shifts left when lowerEntry is removed.
        val currentEntry = state.push(PixelRouteRequest(destination, "current"))
        tester.pumpAndSettle()
        // Current probe identity must survive the sibling index change.
        val currentProbe = checkNotNull(probeStates["current"]?.singleOrNull())

        assertTrue(state.remove(lowerEntry, animated = false))
        tester.pumpFrame(1)

        assertSame(currentEntry, state.currentEntry)
        assertSame(currentProbe, checkNotNull(probeStates["current"]?.singleOrNull()))
        assertFalse(currentProbe.isDisposed)

        // Replacement mounts beside the outgoing entry, then shifts into its final stack index.
        val replacementEntry = checkNotNull(
            state.replace(PixelRouteRequest(destination, "replacement"), animated = true),
        )
        tester.pumpFrame(100)
        // Mid-transition replacement probe is the identity that must survive settlement.
        val replacementProbe = checkNotNull(probeStates["replacement"]?.singleOrNull())
        tester.pumpAndSettle()

        assertSame(replacementEntry, state.currentEntry)
        assertSame(replacementProbe, checkNotNull(probeStates["replacement"]?.singleOrNull()))
        assertFalse(replacementProbe.isDisposed)
        assertTrue(currentProbe.isDisposed)
        tester.dispose()
    }

    /** Verifies repeated legacy pushes isolate identity, state, result, and lower-entry ownership. */
    @Test
    fun repeatedLegacyRoutePushesOwnIndependentEntriesBucketsAndChannels() {
        // Result delivery order proves that each repeated stack slot owns its callback.
        val results = mutableListOf<String>()
        // Root route keeps the stack non-empty throughout both pop operations.
        val root = legacyRoute("root")
        // One reusable route object deliberately appears in two stack positions.
        val shared = legacyRoute("shared")
        // Direct state access makes entry identity and channel state observable.
        val navigator = PixelNavigatorState(root)
        // One identity key is reused across both buckets to prove entry-level isolation.
        val stateKey = PixelRouteStateKey<String>("draft")

        navigator.push(shared) { value -> results += "lower=$value" }
        // Lower repeated entry must remain independently addressable after another push.
        val lowerEntry = navigator.currentEntry
        lowerEntry.stateBucket.write(stateKey, "lower-state")
        settleTransition(navigator)

        navigator.push(shared) { value -> results += "top=$value" }
        // Top repeated entry is a new concrete entry despite sharing the route definition.
        val topEntry = navigator.currentEntry
        topEntry.stateBucket.write(stateKey, "top-state")
        settleTransition(navigator)

        assertNotEquals(lowerEntry.id, topEntry.id)
        assertNotSame(lowerEntry.stateBucket, topEntry.stateBucket)
        assertNotSame(lowerEntry.resultChannel, topEntry.resultChannel)
        assertEquals("lower-state", lowerEntry.stateBucket.read(stateKey))
        assertEquals("top-state", topEntry.stateBucket.read(stateKey))

        assertTrue(navigator.pop("top-value"))
        assertEquals(PixelRouteResultState.Pending, topEntry.resultChannel.state)
        settleTransition(navigator)

        assertEquals(listOf("top=top-value"), results)
        assertEquals(PixelRouteOutcome.Success("top-value"), topEntry.resultChannel.outcome)
        assertNull(topEntry.stateBucket.read(stateKey))
        assertEquals("lower-state", lowerEntry.stateBucket.read(stateKey))
        assertSame(lowerEntry, navigator.currentEntry)
        assertEquals(PixelRouteResultState.Pending, lowerEntry.resultChannel.state)

        assertTrue(navigator.pop("lower-value"))
        settleTransition(navigator)

        assertEquals(listOf("top=top-value", "lower=lower-value"), results)
        assertEquals(PixelRouteOutcome.Success("lower-value"), lowerEntry.resultChannel.outcome)
        navigator.disposeNavigator()
    }

    /** Verifies typed duplicate entries distinguish nullable success from explicit cancellation. */
    @Test
    fun repeatedTypedDestinationPushesIsolateArgumentsAndNullableOutcomesExactlyOnce() {
        // A single destination definition is reused for two independently typed requests.
        val destination = pixelRouteDestination<String, String?>(id = "typed-nullable") { _, scope ->
            Text(scope.arguments)
        }
        // Lower callback outcomes are tracked separately from top callback outcomes.
        val lowerOutcomes = mutableListOf<PixelRouteOutcome<String?>>()
        // Top callback outcomes prove that a successful null is still a success.
        val topOutcomes = mutableListOf<PixelRouteOutcome<String?>>()
        // Navigator state starts with a compatibility root entry.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // Shared key object proves identical keys do not bridge entry buckets.
        val stateKey = PixelRouteStateKey<Int>("counter")

        // First request captures its own immutable argument and callback.
        val lowerEntry = navigator.push(
            PixelRouteRequest(destination, "lower-argument"),
        ) { outcome -> lowerOutcomes += outcome }
        lowerEntry.stateBucket.write(stateKey, 1)
        settleTransition(navigator)

        // Second request uses the same destination but different arguments and storage.
        val topEntry = navigator.push(
            PixelRouteRequest(destination, "top-argument"),
        ) { outcome -> topOutcomes += outcome }
        topEntry.stateBucket.write(stateKey, 2)
        settleTransition(navigator)

        assertNotEquals(lowerEntry.id, topEntry.id)
        assertEquals("lower-argument", lowerEntry.arguments)
        assertEquals("top-argument", topEntry.arguments)
        assertNotSame(lowerEntry.stateBucket, topEntry.stateBucket)
        assertNotSame(lowerEntry.resultChannel, topEntry.resultChannel)
        assertEquals(1, lowerEntry.stateBucket.read(stateKey))
        assertEquals(2, topEntry.stateBucket.read(stateKey))

        assertTrue(navigator.complete(topEntry, null))
        assertTrue(topOutcomes.isEmpty())
        settleTransition(navigator)

        assertEquals(listOf(PixelRouteOutcome.Success(null)), topOutcomes)
        assertEquals(PixelRouteResultState.Succeeded, topEntry.resultChannel.state)
        assertEquals(PixelRouteOutcome.Success(null), topEntry.resultChannel.outcome)
        assertEquals(PixelRouteResultState.Pending, lowerEntry.resultChannel.state)

        assertTrue(navigator.cancel(lowerEntry, animated = false))
        assertEquals(
            listOf(PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Explicit)),
            lowerOutcomes,
        )
        assertEquals(PixelRouteResultState.Cancelled, lowerEntry.resultChannel.state)
        assertFalse(navigator.cancel(lowerEntry, animated = false))
        assertEquals(1, lowerOutcomes.size)
        navigator.disposeNavigator()
    }

    /** Verifies lifecycle ordering and exactly-once disposal when a transition is interrupted. */
    @Test
    fun interruptedTransitionsPreserveLifecycleOrderAndDisposeEachEntryExactlyOnce() {
        // Ordered lifecycle log exposes every activation, removal, and terminal disposal.
        val events = mutableListOf<String>()
        // Root callbacks prove reactivation happens before outgoing entry disposal.
        val root = PixelRoute(
            name = "root",
            builder = { Text("ROOT") },
            onEnter = { events += "root-enter" },
            onExit = { events += "root-exit" },
        )
        // Typed destination labels lifecycle events with each request argument.
        val destination = pixelRouteDestination<String, Unit>(
            id = "interruptible",
            onEnter = { entry -> events += "${entry.arguments}-enter" },
            onExit = { entry -> events += "${entry.arguments}-exit" },
            onDispose = { entry -> events += "${entry.arguments}-dispose" },
        ) { _, scope -> Text(scope.arguments) }
        // State under test owns all transition IDs and finalization queues.
        val navigator = PixelNavigatorState(root)

        // First typed entry becomes active normally.
        val firstEntry = navigator.push(PixelRouteRequest(destination, "first"))
        settleTransition(navigator)

        assertTrue(navigator.pop())
        // This transition will be superseded by the next push.
        val interruptedTransitionId = checkNotNull(navigator.activeTransition).id

        // Starting another mutation settles and disposes the prior outgoing entry first.
        val secondEntry = navigator.push(PixelRouteRequest(destination, "second"))
        // A stale completion callback must not finalize anything a second time.
        navigator.completeTransition(interruptedTransitionId)
        settleTransition(navigator)

        assertTrue(navigator.remove(secondEntry, animated = true))
        // Current removal transition is completed twice to prove idempotence.
        val removalTransitionId = checkNotNull(navigator.activeTransition).id
        navigator.completeTransition(removalTransitionId)
        navigator.completeTransition(removalTransitionId)

        assertEquals(
            listOf(
                "root-enter",
                "root-exit",
                "first-enter",
                "first-exit",
                "root-enter",
                "first-dispose",
                "root-exit",
                "second-enter",
                "second-exit",
                "root-enter",
                "second-dispose",
            ),
            events,
        )
        assertEquals(1, events.count { event -> event == "first-dispose" })
        assertEquals(1, events.count { event -> event == "second-dispose" })
        assertEquals(PixelRouteLifecycleState.Disposed, firstEntry.lifecycleState)
        assertEquals(PixelRouteLifecycleState.Disposed, secondEntry.lifecycleState)
        navigator.disposeNavigator()
    }

    /** Verifies legacy replace transfers only its callback while allocating fresh entry state. */
    @Test
    fun legacyReplaceTransfersCallbackToFreshEntryWithoutSharingState() {
        // Callback values prove compatibility ownership follows the replaced stack slot.
        val callbackValues = mutableListOf<Any?>()
        // Navigator starts from a stable compatibility root.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // Shared key makes old-bucket clearing and new-bucket independence visible.
        val stateKey = PixelRouteStateKey<String>("editor")

        navigator.push(legacyRoute("old")) { value -> callbackValues += value }
        // Old entry owns the callback before replacement.
        val oldEntry = navigator.currentEntry
        oldEntry.stateBucket.write(stateKey, "old-state")
        settleTransition(navigator)

        navigator.replace(legacyRoute("replacement"), animated = false)
        // Replacement must never reuse the old entry object, ID, or state bucket.
        val replacementEntry = navigator.currentEntry
        replacementEntry.stateBucket.write(stateKey, "replacement-state")

        assertNotEquals(oldEntry.id, replacementEntry.id)
        assertNotSame(oldEntry.stateBucket, replacementEntry.stateBucket)
        assertNull(oldEntry.stateBucket.read(stateKey))
        assertEquals("replacement-state", replacementEntry.stateBucket.read(stateKey))
        assertEquals(PixelRouteLifecycleState.Disposed, oldEntry.lifecycleState)
        assertEquals(
            PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Replaced),
            oldEntry.resultChannel.outcome,
        )
        assertTrue(callbackValues.isEmpty())

        assertTrue(navigator.pop("replacement-result"))
        settleTransition(navigator)

        assertEquals(listOf("replacement-result"), callbackValues)
        assertEquals(
            PixelRouteOutcome.Success("replacement-result"),
            replacementEntry.resultChannel.outcome,
        )
        navigator.disposeNavigator()
    }

    /** Verifies typed replace cancels the old channel and creates an independent new channel. */
    @Test
    fun typedReplaceCancelsOldChannelAsReplacedAndSettlesNewChannelIndependently() {
        // Reusable destination supports an integer result for both old and new requests.
        val destination = pixelRouteDestination<String, Int>(id = "typed-replace") { _, scope ->
            Text(scope.arguments)
        }
        // Old outcomes must receive exactly one explicit replacement cancellation.
        val oldOutcomes = mutableListOf<PixelRouteOutcome<Int>>()
        // New outcomes must remain pending until the replacement entry completes.
        val newOutcomes = mutableListOf<PixelRouteOutcome<Int>>()
        // Navigator under test uses a legacy root only as its retained base entry.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // Bucket key proves typed replacement does not inherit prior state.
        val stateKey = PixelRouteStateKey<Int>("revision")

        // Original typed entry owns its first result channel.
        val oldEntry = navigator.push(PixelRouteRequest(destination, "old")) { outcome ->
            oldOutcomes += outcome
        }
        oldEntry.stateBucket.write(stateKey, 1)
        settleTransition(navigator)

        // Replacement receives a completely independent callback and channel.
        val nullableReplacement = navigator.replace(
            request = PixelRouteRequest(destination, "new"),
            onOutcome = { outcome -> newOutcomes += outcome },
            animated = false,
        )
        assertNotNull(nullableReplacement)
        // Non-null assertion is safe after the explicit contract assertion above.
        val replacementEntry = checkNotNull(nullableReplacement)

        assertNotEquals(oldEntry.id, replacementEntry.id)
        assertNotSame(oldEntry.stateBucket, replacementEntry.stateBucket)
        assertNotSame(oldEntry.resultChannel, replacementEntry.resultChannel)
        assertNull(oldEntry.stateBucket.read(stateKey))
        assertNull(replacementEntry.stateBucket.read(stateKey))
        assertEquals(
            listOf(PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Replaced)),
            oldOutcomes,
        )
        assertEquals(PixelRouteResultState.Pending, replacementEntry.resultChannel.state)
        assertTrue(newOutcomes.isEmpty())

        assertFalse(navigator.complete(oldEntry, 99))
        assertEquals(1, oldOutcomes.size)
        assertSame(replacementEntry, navigator.currentEntry)
        assertTrue(navigator.complete(replacementEntry, 7))
        settleTransition(navigator)

        assertEquals(listOf(PixelRouteOutcome.Success(7)), newOutcomes)
        assertEquals(PixelRouteOutcome.Success(7), replacementEntry.resultChannel.outcome)
        navigator.disposeNavigator()
    }

    /** Verifies remove behavior for inactive, current, stale, and final-root entries. */
    @Test
    fun removeHandlesInactiveAndCurrentEntriesWhileFailuresPreserveStackTopology() {
        // Reusable destination lets the test remove two independent typed entries.
        val destination = pixelRouteDestination<String, Unit>(id = "removable") { _, scope ->
            Text(scope.arguments)
        }
        // Inactive outcome confirms non-current removal settles immediately.
        val inactiveOutcomes = mutableListOf<PixelRouteOutcome<Unit>>()
        // Current outcome confirms animated removal waits for transition settlement.
        val currentOutcomes = mutableListOf<PixelRouteOutcome<Unit>>()
        // Navigator begins with one non-removable final root.
        val navigator = PixelNavigatorState(legacyRoute("root"))

        // First typed entry becomes inactive after the second push.
        val inactiveEntry = navigator.push(PixelRouteRequest(destination, "inactive")) { outcome ->
            inactiveOutcomes += outcome
        }
        settleTransition(navigator)
        // Second typed entry remains the current foreground entry.
        val currentEntry = navigator.push(PixelRouteRequest(destination, "current")) { outcome ->
            currentOutcomes += outcome
        }
        settleTransition(navigator)

        assertTrue(navigator.remove(inactiveEntry, animated = true))
        assertNull(navigator.activeTransition)
        assertSame(currentEntry, navigator.currentEntry)
        assertEquals(
            listOf(PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Removed)),
            inactiveOutcomes,
        )
        assertEquals(PixelRouteLifecycleState.Disposed, inactiveEntry.lifecycleState)

        assertTrue(navigator.remove(currentEntry.id, animated = true))
        assertNotNull(navigator.activeTransition)
        assertTrue(currentOutcomes.isEmpty())
        settleTransition(navigator)
        assertEquals(
            listOf(PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Removed)),
            currentOutcomes,
        )

        // Topology before stale removal excludes mutable diagnostic failure state.
        val topologyBeforeMissingRemoval = stackTopology(navigator)
        assertFalse(navigator.remove(inactiveEntry.id, animated = false))
        assertEquals(topologyBeforeMissingRemoval, stackTopology(navigator))
        assertEquals(PixelNavigationFailureReason.EntryNotFound, navigator.lastFailure?.reason)

        // The remaining root cannot be removed and its stack identity must remain unchanged.
        val rootEntry = navigator.currentEntry
        // Topology snapshot verifies root-removal rejection is side-effect free.
        val topologyBeforeRootRemoval = stackTopology(navigator)
        assertFalse(navigator.remove(rootEntry, animated = false))
        assertEquals(topologyBeforeRootRemoval, stackTopology(navigator))
        assertEquals(PixelNavigationFailureReason.CannotPopRoot, navigator.lastFailure?.reason)
        assertEquals(PixelRouteLifecycleState.Active, rootEntry.lifecycleState)
        navigator.disposeNavigator()
    }

    /** Verifies clear preserves established bottom-to-top disposal and result delivery ordering. */
    @Test
    fun clearDisposesThenCancelsEntriesInBottomToTopOrder() {
        // Unified log captures the two ordered phases: all disposals, then all callbacks.
        val events = mutableListOf<String>()
        // Navigator retains this root after clear.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // First route is the bottom-most removable stack entry.
        val firstRoute = PixelRoute(
            name = "first",
            builder = { Text("FIRST") },
            onDispose = { events += "first-dispose" },
        )
        // Second route occupies the middle removable stack position.
        val secondRoute = PixelRoute(
            name = "second",
            builder = { Text("SECOND") },
            onDispose = { events += "second-dispose" },
        )
        // Third route is the foreground removable stack entry.
        val thirdRoute = PixelRoute(
            name = "third",
            builder = { Text("THIRD") },
            onDispose = { events += "third-dispose" },
        )

        navigator.push(firstRoute) { value -> events += "first-result=$value" }
        // Captured entry exposes terminal channel state after clear.
        val firstEntry = navigator.currentEntry
        settleTransition(navigator)
        navigator.push(secondRoute) { value -> events += "second-result=$value" }
        // Captured middle entry verifies bottom-to-top finalization.
        val secondEntry = navigator.currentEntry
        settleTransition(navigator)
        navigator.push(thirdRoute) { value -> events += "third-result=$value" }
        // Captured top entry verifies it is finalized last.
        val thirdEntry = navigator.currentEntry
        settleTransition(navigator)

        assertTrue(navigator.clear(animated = false))

        assertEquals(
            listOf(
                "first-dispose",
                "second-dispose",
                "third-dispose",
                "first-result=null",
                "second-result=null",
                "third-result=null",
            ),
            events,
        )
        listOf(firstEntry, secondEntry, thirdEntry).forEach { entry ->
            assertEquals(PixelRouteLifecycleState.Disposed, entry.lifecycleState)
            assertEquals(
                PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Cleared),
                entry.resultChannel.outcome,
            )
        }
        assertEquals(listOf("root"), navigator.snapshot().routeNames)
        assertFalse(navigator.clear(animated = false))
        assertEquals(6, events.size)
        navigator.disposeNavigator()
    }

    /** Verifies host disposal terminates every entry and pending typed channel exactly once. */
    @Test
    fun navigatorHostDisposeDisposesAllEntriesAndCancelsPendingTypedResultsExactlyOnce() {
        // PixelTester exercises the real StatefulWidget host disposal path.
        val tester = PixelTester()
        // Ordered log captures reverse-stack disposal followed by reverse-stack result delivery.
        val events = mutableListOf<String>()
        // Navigator reference is initialized by the mounted root builder.
        var navigator: PixelNavigatorState? = null
        // Root route proves the compatibility entry is also disposed by host teardown.
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
            onDispose = { events += "root-dispose" },
        )
        // Typed destination logs per-entry disposal through its request arguments.
        val destination = pixelRouteDestination<String, Int>(
            id = "host-dispose",
            onDispose = { entry -> events += "${entry.arguments}-dispose" },
        ) { _, scope -> Text(scope.arguments) }

        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)
        // Mounted state is required before pushing typed entries.
        val state = checkNotNull(navigator)
        // Root entry is retained so its final lifecycle can be inspected after teardown.
        val rootEntry = state.currentEntry
        // First typed entry will be inactive when the host is disposed.
        val firstEntry = state.push(PixelRouteRequest(destination, "first")) { outcome ->
            events += "first-result=${cancellationReason(outcome)}"
        }
        tester.pumpAndSettle()
        // Second typed entry is active when the host is disposed.
        val secondEntry = state.push(PixelRouteRequest(destination, "second")) { outcome ->
            events += "second-result=${cancellationReason(outcome)}"
        }
        tester.pumpAndSettle()

        tester.dispose()

        assertEquals(
            listOf(
                "second-dispose",
                "first-dispose",
                "root-dispose",
                "second-result=NavigatorDisposed",
                "first-result=NavigatorDisposed",
            ),
            events,
        )
        listOf(rootEntry, firstEntry, secondEntry).forEach { entry ->
            assertEquals(PixelRouteLifecycleState.Disposed, entry.lifecycleState)
        }
        assertEquals(
            PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.NavigatorDisposed),
            firstEntry.resultChannel.outcome,
        )
        assertEquals(
            PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.NavigatorDisposed),
            secondEntry.resultChannel.outcome,
        )
        assertTrue(state.inspectionSnapshot().isDisposed)
        assertTrue(state.inspectionSnapshot().entries.isEmpty())

        // Repeated terminal disposal must not invoke lifecycle or result callbacks again.
        state.disposeNavigator()
        assertEquals(5, events.size)
    }

    /** Verifies ordered observer phases, inspection data, and observer exception isolation. */
    @Test
    fun observersReceiveOrderedPhasesAndInspectionWhileThrowingObserverCannotBreakNavigation() {
        // Recording observer receives every immutable event that survives another observer's throw.
        val events = mutableListOf<PixelNavigationEvent>()
        // Navigator under test starts with one active root entry.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // This observer deliberately fails on every callback.
        val throwingObserver = PixelNavigationObserver {
            throw IllegalStateException("observer failure")
        }
        // This observer proves later observers still receive the same event.
        val recordingObserver = PixelNavigationObserver { event -> events += event }
        // State key appears in the deterministic inspection snapshot.
        val stateKey = PixelRouteStateKey<String>("inspection-key")
        // Typed destination creates an inspectable entry with a stable destination ID.
        val destination = pixelRouteDestination<String, Unit>(id = "observed") { _, scope ->
            Text(scope.arguments)
        }

        navigator.addObserver(throwingObserver)
        navigator.addObserver(recordingObserver)
        // Pushed entry must succeed even though the first observer throws twice.
        val entry = navigator.push(PixelRouteRequest(destination, "argument"))
        entry.stateBucket.write(stateKey, "visible")

        assertEquals(
            listOf(PixelNavigationEventType.Started, PixelNavigationEventType.Completed),
            events.filter { event -> event.action == PixelNavigationAction.Push }
                .map { event -> event.type },
        )
        // Inspection snapshot freezes the current stack and transition without exposing arguments.
        val inspection = navigator.inspectionSnapshot()
        assertEquals(entry.id, inspection.currentEntryId)
        assertEquals(listOf("root", "observed"), inspection.entries.map { item -> item.destinationId })
        assertEquals(
            listOf(PixelRouteLifecycleState.Inactive, PixelRouteLifecycleState.Active),
            inspection.entries.map { item -> item.lifecycleState },
        )
        assertEquals(setOf("inspection-key"), inspection.entries.last().stateKeyNames)
        assertEquals(PixelNavigatorOperation.Push, inspection.transition?.operation)
        assertEquals(PixelNavigationFailureReason.ObserverCallbackFailed, inspection.lastFailure?.reason)

        navigator.removeObserver(throwingObserver)
        settleTransition(navigator)
        assertTrue(navigator.pop())
        settleTransition(navigator)
        assertFalse(navigator.pop())

        assertEquals(
            listOf(
                PixelNavigationEventType.Started,
                PixelNavigationEventType.Completed,
                PixelNavigationEventType.Failed,
            ),
            events.filter { event -> event.action == PixelNavigationAction.Pop }
                .map { event -> event.type },
        )
        assertEquals(
            events.map { event -> event.sequence }.sorted(),
            events.map { event -> event.sequence },
        )
        assertEquals(events.size, events.map { event -> event.sequence }.distinct().size)
        assertEquals(PixelNavigationFailureReason.CannotPopRoot, navigator.lastFailure?.reason)
        navigator.disposeNavigator()
    }

    /** Verifies lifecycle and result callback failures cannot strand later entry settlements. */
    @Test
    fun throwingLifecycleAndResultCallbacksDoNotPreventOtherEntriesFromSettling() {
        // Ordered event log proves processing continues after both independent exceptions.
        val events = mutableListOf<String>()
        // First destination throws from terminal lifecycle after recording its invocation.
        val lifecycleThrowingDestination = pixelRouteDestination<String, Unit>(
            id = "lifecycle-throwing",
            onDispose = {
                events += "first-dispose-throw"
                throw IllegalStateException("dispose failure")
            },
        ) { _, scope -> Text(scope.arguments) }
        // Remaining entries use a non-throwing destination lifecycle.
        val normalDestination = pixelRouteDestination<String, Unit>(
            id = "normal",
            onDispose = { entry -> events += "${entry.arguments}-dispose" },
        ) { _, scope -> Text(scope.arguments) }
        // Navigator retains a root while all typed entries are cleared together.
        val navigator = PixelNavigatorState(legacyRoute("root"))
        // First callback verifies a lifecycle failure does not prevent result delivery.
        var firstCallbackCount = 0
        // Second callback throws after terminal channel state has already been committed.
        var secondCallbackCount = 0
        // Third callback proves result processing continues after the second callback throws.
        var thirdCallbackCount = 0

        // First entry owns the lifecycle callback that throws during disposal.
        val firstEntry = navigator.push(
            PixelRouteRequest(lifecycleThrowingDestination, "first"),
        ) { outcome ->
            firstCallbackCount += 1
            events += "first-result=${cancellationReason(outcome)}"
        }
        settleTransition(navigator)
        // Second entry owns the result callback that throws during delivery.
        val secondEntry = navigator.push(PixelRouteRequest(normalDestination, "second")) { outcome ->
            secondCallbackCount += 1
            events += "second-result=${cancellationReason(outcome)}-throw"
            throw IllegalStateException("result failure")
        }
        settleTransition(navigator)
        // Third entry must still settle normally after both prior failures.
        val thirdEntry = navigator.push(PixelRouteRequest(normalDestination, "third")) { outcome ->
            thirdCallbackCount += 1
            events += "third-result=${cancellationReason(outcome)}"
        }
        settleTransition(navigator)

        assertTrue(navigator.clear(animated = false))

        assertEquals(
            listOf(
                "first-dispose-throw",
                "second-dispose",
                "third-dispose",
                "first-result=Cleared",
                "second-result=Cleared-throw",
                "third-result=Cleared",
            ),
            events,
        )
        assertEquals(1, firstCallbackCount)
        assertEquals(1, secondCallbackCount)
        assertEquals(1, thirdCallbackCount)
        listOf(firstEntry, secondEntry, thirdEntry).forEach { entry ->
            assertEquals(PixelRouteLifecycleState.Disposed, entry.lifecycleState)
            assertEquals(
                PixelRouteOutcome.Cancelled(PixelRouteCancellationReason.Cleared),
                entry.resultChannel.outcome,
            )
        }
        assertEquals(PixelNavigationFailureReason.ResultCallbackFailed, navigator.lastFailure?.reason)
        assertEquals(secondEntry.id, navigator.lastFailure?.entryId)
        navigator.disposeNavigator()
    }

    /** Creates a minimal compatibility route for state-machine tests that do not render a host. */
    private fun legacyRoute(name: String): PixelRoute {
        return PixelRoute(name = name, builder = { Text(name.uppercase()) })
    }

    /** Completes the navigator's current transition and fails fast when no transition exists. */
    private fun settleTransition(navigator: PixelNavigatorState) {
        // Captured record prevents a later state read from observing a different transition ID.
        val transition = navigator.activeTransition
        assertNotNull("Expected an active PixelNavigator transition", transition)
        navigator.completeTransition(checkNotNull(transition).id)
    }

    /** Returns immutable stack identity and foreground identity while excluding diagnostics. */
    private fun stackTopology(
        navigator: PixelNavigatorState,
    ): Pair<List<PixelRouteEntryId>, PixelRouteEntryId?> {
        // Runtime inspection provides a deterministic copy without retaining mutable entries.
        val snapshot = navigator.inspectionSnapshot()
        return snapshot.entries.map { entry -> entry.id } to snapshot.currentEntryId
    }

    /** Extracts the explicit cancellation reason from one typed test outcome. */
    private fun <R> cancellationReason(outcome: PixelRouteOutcome<R>): PixelRouteCancellationReason {
        assertTrue("Expected a cancelled route outcome", outcome is PixelRouteOutcome.Cancelled)
        return (outcome as PixelRouteOutcome.Cancelled).reason
    }
}

/** Stateful probe used to verify exact route subtree retention and recreation. */
private class RouteStateProbe(
    /** Text label identifying the route entry in assertions. */
    val label: String,
    /** Callback receiving the newly created state instance. */
    val onReady: (RouteStateProbeState) -> Unit,
    /** Optional retained widget identity. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one independently observable probe state. */
    override fun createState(): State<out StatefulWidget> = RouteStateProbeState()
}

/** Mutable state whose identity, value, and disposal are asserted by navigation tests. */
private class RouteStateProbeState : State<RouteStateProbe>() {
    /** Counter value that must survive only when the entry maintains its subtree. */
    var counter: Int = 0
        private set

    /** Whether this exact State instance has received terminal disposal. */
    var isDisposed: Boolean = false
        private set

    /** Registers this newly created state instance with the owning test. */
    override fun initState() {
        widget.onReady(this)
    }

    /** Increments [counter] through the normal retained State invalidation path. */
    fun increment() {
        setState { counter += 1 }
    }

    /** Records terminal state disposal exactly once. */
    override fun dispose() {
        isDisposed = true
    }

    /** Renders the route label and current retained counter. */
    override fun build(context: BuildContext): Widget {
        return Text("${widget.label}:$counter")
    }
}
