package com.purride.pixelui

import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-2 behavior contract for nested stacks, multi-stack retention, and typed deep links. */
class PixelNestedAndTypedDeepLinkTest {
    /** Verifies a registered link creates only the destination paired with its decoded type. */
    @Test
    fun validTypedDeepLinkPushesAndReplacesWithDecodedArguments() {
        // Root remains the immutable bottom entry during both typed navigation modes.
        val navigator = PixelNavigatorState(textRoute("root", "ROOT"))
        // Observer events must identify the external intent as DeepLink, not an incidental stack primitive.
        val navigationEvents = mutableListOf<PixelNavigationEvent>()
        navigator.addObserver(PixelNavigationObserver { event -> navigationEvents += event })
        // Reusable destination records a strongly typed profile request.
        val destination = profileDestination()
        // Resolver is restricted to the one legal profile destination.
        val resolver = profileResolver(destination)

        val pushed = navigator.handleTypedDeepLink(
            uri = "pixel://app/profile?id=42&source=notification",
            resolver = resolver,
        )

        assertTrue(pushed is PixelTypedDeepLinkNavigated)
        pushed as PixelTypedDeepLinkNavigated
        assertEquals(PixelTypedDeepLinkNavigationMode.Push, pushed.mode)
        assertEquals(ProfileArguments(42, "notification"), pushed.entry.arguments)
        assertEquals("profile", pushed.entry.destination.id)
        assertEquals(2, navigator.entries.size)

        val replaced = navigator.handleTypedDeepLink(
            uri = "pixel://app/profile?id=7",
            resolver = resolver,
            mode = PixelTypedDeepLinkNavigationMode.Replace,
        )

        assertTrue(replaced is PixelTypedDeepLinkNavigated)
        replaced as PixelTypedDeepLinkNavigated
        assertEquals(PixelTypedDeepLinkNavigationMode.Replace, replaced.mode)
        assertEquals(ProfileArguments(7, null), replaced.entry.arguments)
        assertEquals(2, navigator.entries.size)
        assertSame(replaced.entry, navigator.currentEntry)
        assertEquals(4, navigationEvents.size)
        assertTrue(navigationEvents.all { event -> event.action == PixelNavigationAction.DeepLink })
        assertEquals(
            listOf(
                PixelNavigationEventType.Started,
                PixelNavigationEventType.Completed,
                PixelNavigationEventType.Started,
                PixelNavigationEventType.Completed,
            ),
            navigationEvents.map(PixelNavigationEvent::type),
        )
    }

    /** Verifies missing, invalid, unknown, and malformed links are non-throwing and non-mutating. */
    @Test
    fun rejectedTypedDeepLinksPreserveTheExistingStack() {
        // Navigator identity and entry count must remain unchanged for every rejection category.
        val navigator = PixelNavigatorState(textRoute("root", "ROOT"))
        // Destination/route pairing supplies explicit missing and invalid decode outcomes.
        val resolver = profileResolver(profileDestination())
        // Existing root identity is a stronger non-mutation assertion than route-name equality.
        val rootEntry = navigator.currentEntry

        val missing = navigator.handleTypedDeepLink("pixel://app/profile", resolver)
        val invalid = navigator.handleTypedDeepLink("pixel://app/profile?id=not-a-number", resolver)
        val unmatched = navigator.handleTypedDeepLink("pixel://app/settings?id=1", resolver)
        val malformed = navigator.handleTypedDeepLink("https://bad host/profile?id=1", resolver)

        assertEquals(
            PixelTypedDeepLinkRejectionReason.MissingArgument,
            (missing as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals("id", missing.argumentFailure?.parameterName)
        assertEquals(
            PixelTypedDeepLinkRejectionReason.InvalidArgument,
            (invalid as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals("not-a-number", invalid.argumentFailure?.rawValue)
        assertTrue(unmatched is PixelTypedDeepLinkNotMatched)
        assertEquals(
            PixelTypedDeepLinkRejectionReason.MalformedUri,
            (malformed as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals(listOf(rootEntry), navigator.entries)
    }

    /** Verifies consumer matcher/decoder exceptions cannot partially mutate navigation state. */
    @Test
    fun typedDeepLinkResolverExceptionsBecomeStructuredRejections() {
        // Root entry must survive both matcher and decoder callback failures.
        val navigator = PixelNavigatorState(textRoute("root", "ROOT"))
        // Destination type remains legal even though consumer callbacks fail unexpectedly.
        val destination = profileDestination()
        // Throwing matcher verifies resolver-level exception containment.
        val matcherFailureResolver = PixelTypedDeepLinkResolver(
            listOf(
                PixelTypedDeepLinkRoute(
                    destination = destination,
                    matcher = PixelTypedDeepLinkMatcher { error("matcher failed") },
                    argumentDecoder = PixelDeepLinkArgumentDecoder { PixelDeepLinkDecoded(ProfileArguments(1, null)) },
                ),
            ),
        )
        // Throwing decoder verifies a matched route still cannot mutate before validation finishes.
        val decoderFailureResolver = PixelTypedDeepLinkResolver(
            listOf(
                PixelTypedDeepLinkRoute(
                    destination = destination,
                    matcher = PixelTypedDeepLinkMatcher { true },
                    argumentDecoder = PixelDeepLinkArgumentDecoder { error("decoder failed") },
                ),
            ),
        )

        val matcherFailure = navigator.handleTypedDeepLink("pixel://app/profile?id=1", matcherFailureResolver)
        val decoderFailure = navigator.handleTypedDeepLink("pixel://app/profile?id=1", decoderFailureResolver)

        assertEquals(
            PixelTypedDeepLinkRejectionReason.ResolverFailure,
            (matcherFailure as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals(
            PixelTypedDeepLinkRejectionReason.ResolverFailure,
            (decoderFailure as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals(1, navigator.entries.size)
        assertEquals("root", navigator.currentEntry.destination.id)
    }

    /** Verifies bottom-navigation stacks retain state while isolating presentation and back. */
    @Test
    fun multiStackNavigatorRetainsEachStackAndDispatchesBackOnlyToTheActiveStack() {
        // Tester provides deterministic frames for all child Navigator transitions.
        val tester = PixelTester()
        // Root dispatcher observes only the multi-stack bridge, never inactive child handlers.
        val rootBackDispatcher = PixelBackDispatcher()
        // Controller starts on the common bottom-navigation home tab.
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        // Probe states reveal unmounting and preserve tap counters across switches.
        val probes = linkedMapOf<String, StackProbeState>()
        // Navigator states allow independent stack-depth assertions.
        val navigatorStates = linkedMapOf<String, PixelNavigatorState>()
        // Home and settings roots use distinct retained StatefulWidget instances.
        val stacks = listOf(
            stackDefinition("home", "HOME", probes, navigatorStates),
            stackDefinition("settings", "SETTINGS", probes, navigatorStates),
        )

        tester.pumpWidget(
            PixelBackHost(
                dispatcher = rootBackDispatcher,
                child = PixelMultiStackNavigator(
                    stacks = stacks,
                    controller = controller,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )

        val homeProbe = checkNotNull(probes["home"])
        val settingsProbe = checkNotNull(probes["settings"])
        val homeNavigator = checkNotNull(navigatorStates["home"])
        val settingsNavigator = checkNotNull(navigatorStates["settings"])
        assertTrue(tester.exists(find.byText("HOME:0")))
        assertFalse(tester.exists(find.byText("SETTINGS:0")))
        assertTrue(tester.dumpSemanticsTree().contains("HOME:0"))
        assertFalse(tester.dumpSemanticsTree().contains("SETTINGS:0"))
        assertEquals(1, tester.renderResult?.clickTargets?.size)
        assertFalse(rootBackDispatcher.hasRegisteredHandlers)

        assertEquals(PixelStackSelectionResult.Activated, controller.selectStack("settings"))
        tester.pumpFrame(1)

        assertFalse(homeProbe.isDisposed)
        assertFalse(settingsProbe.isDisposed)
        assertFalse(tester.exists(find.byText("HOME:0")))
        assertTrue(tester.exists(find.byText("SETTINGS:0")))
        assertFalse(tester.dumpSemanticsTree().contains("HOME:0"))
        assertTrue(tester.dumpSemanticsTree().contains("SETTINGS:0"))
        assertEquals(1, tester.renderResult?.clickTargets?.size)
        assertTrue(rootBackDispatcher.hasRegisteredHandlers)
        tester.tap(find.byKey("stack-probe:SETTINGS"))
        assertEquals(1, settingsProbe.counter)
        assertEquals(0, homeProbe.counter)

        // Both tabs now receive independent detail entries.
        homeNavigator.push(textRoute("home-detail", "HOME DETAIL"))
        controller.selectStack("home")
        tester.pumpAndSettle()
        settingsNavigator.push(textRoute("settings-detail", "SETTINGS DETAIL"))
        controller.selectStack("settings")
        tester.pumpAndSettle()
        assertEquals(2, homeNavigator.entries.size)
        assertEquals(2, settingsNavigator.entries.size)

        // Back pops only settings; home remains on its own detail route.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpAndSettle()
        assertEquals(2, homeNavigator.entries.size)
        assertEquals(1, settingsNavigator.entries.size)
        assertEquals("settings", controller.activeStackId)

        // Back at a secondary root returns to the initial tab without clearing its retained stack.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpFrame(1)
        assertEquals("home", controller.activeStackId)
        assertEquals(2, homeNavigator.entries.size)

        // Active home back now pops its own detail; no hidden settings handler participates.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpAndSettle()
        assertEquals(1, homeNavigator.entries.size)
        assertEquals(1, settingsNavigator.entries.size)
        assertFalse(rootBackDispatcher.handleBack())
        assertFalse(rootBackDispatcher.hasRegisteredHandlers)

        // Reselect behavior is explicit and affects only the active stack.
        homeNavigator.push(textRoute("home-second-detail", "HOME SECOND DETAIL"))
        tester.pumpAndSettle()
        assertEquals(
            PixelStackSelectionResult.PoppedToRoot,
            controller.selectStack("home", popToRootOnReselect = true, animated = false),
        )
        assertEquals(1, homeNavigator.entries.size)
        assertEquals(PixelStackSelectionResult.UnknownStack, controller.selectStack("missing"))

        tester.dispose()
        assertTrue(homeProbe.isDisposed)
        assertTrue(settingsProbe.isDisposed)
        assertFalse(controller.isAttached)
        assertNull(controller.activeNavigatorState)
    }

    /** Verifies predictive progress is locked to the active stack and cancelled on a tab switch. */
    @Test
    fun multiStackPredictiveBackNeverReachesHiddenStacks() {
        // Tester mounts one predictive callback inside each isolated stack dispatcher.
        val tester = PixelTester()
        // Host dispatcher is the platform-facing entry point for the full gesture session.
        val rootBackDispatcher = PixelBackDispatcher()
        // Controller forwards sessions only to its selected stack dispatcher.
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        // Per-stack callbacks retain exact lifecycle logs for isolation assertions.
        val callbacks = linkedMapOf(
            "home" to RecordingPredictiveBackCallback("home"),
            "settings" to RecordingPredictiveBackCallback("settings"),
        )
        // Each stack root contributes one predictive handler beneath its private PixelBackHost.
        val stacks = callbacks.map { (id, callback) ->
            PixelTypedNavigatorStack(
                id = id,
                initialRequest = testRouteRequest(
                    name = id,
                    transition = PixelRouteTransition.None,
                    builder = {
                        PixelPredictiveBackHandler(
                            callback = callback,
                            child = Text(id.uppercase()),
                        )
                    },
                ),
            )
        }
        tester.pumpWidget(
            PixelBackHost(
                dispatcher = rootBackDispatcher,
                child = PixelMultiStackNavigator(
                    stacks = stacks,
                    controller = controller,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )
        // Start and progress values model a cancellable left-edge platform gesture.
        val started = predictiveEvent(progress = 0f)
        val progressed = predictiveEvent(progress = 0.45f)

        assertTrue(rootBackDispatcher.startPredictiveBack(started))
        rootBackDispatcher.updatePredictiveBack(progressed)
        rootBackDispatcher.cancelPredictiveBack()

        assertEquals(listOf("home:start:0.0", "home:progress:0.45", "home:cancel"), callbacks.getValue("home").events)
        assertTrue(callbacks.getValue("settings").events.isEmpty())

        controller.selectStack("settings")
        tester.pumpFrame(1)
        assertTrue(rootBackDispatcher.startPredictiveBack(started))
        rootBackDispatcher.updatePredictiveBack(progressed)
        assertTrue(rootBackDispatcher.commitPredictiveBack())

        assertEquals(
            listOf("settings:start:0.0", "settings:progress:0.45", "settings:commit"),
            callbacks.getValue("settings").events,
        )
        assertEquals(3, callbacks.getValue("home").events.size)

        // Changing tabs during a locked session cancels only the old active child.
        assertTrue(rootBackDispatcher.startPredictiveBack(started))
        controller.selectStack("home")
        tester.pumpFrame(1)
        assertFalse(rootBackDispatcher.commitPredictiveBack())
        assertEquals("settings:cancel", callbacks.getValue("settings").events.last())
        assertEquals(3, callbacks.getValue("home").events.size)
        tester.dispose()
    }

    /** Verifies nearest-Navigator lookup and inactive-parent nested back isolation. */
    @Test
    fun nestedNavigatorOwnsIndependentStateAndCannotHandleBackBehindAnInactiveParentEntry() {
        // Tester and root dispatcher model the host-level system back path.
        val tester = PixelTester()
        val rootBackDispatcher = PixelBackDispatcher()
        // Captured states prove BuildContext resolves the nearest nested Navigator.
        var outerNavigator: PixelNavigatorState? = null
        var innerNavigator: PixelNavigatorState? = null
        // Stable root entry identity controls nested back eligibility after the route is covered.
        var outerRootEntry: PixelRouteEntry<*, *>? = null
        // Inner root captures the nested state from its own Navigator scope.
        val innerRoot = testRouteRequest(
            name = "inner-root",
            transition = PixelRouteTransition.None,
            builder = { context ->
                innerNavigator = PixelNavigator.of(context)
                Text("INNER ROOT")
            },
        )
        // Outer root inserts the back-isolated nested wrapper under its concrete parent entry.
        val outerRoot = testRouteRequest(
            name = "outer-root",
            transition = PixelRouteTransition.None,
            builder = { context ->
                val outer = PixelNavigator.of(context)
                outerNavigator = outer
                if (outerRootEntry == null) outerRootEntry = outer.currentEntry
                PixelNestedNavigator(
                    initialRequest = innerRoot,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                    parentEntry = outerRootEntry,
                )
            },
        )

        tester.pumpWidget(
            PixelBackHost(
                dispatcher = rootBackDispatcher,
                child = PixelNavigator(
                    initialRequest = outerRoot,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )

        val outer = checkNotNull(outerNavigator)
        val inner = checkNotNull(innerNavigator)
        assertNotSame(outer, inner)
        assertFalse(rootBackDispatcher.hasRegisteredHandlers)
        inner.push(textRoute("inner-detail", "INNER DETAIL"))
        tester.pumpAndSettle()
        assertTrue(rootBackDispatcher.hasRegisteredHandlers)
        assertEquals(1, outer.entries.size)
        assertEquals(2, inner.entries.size)

        // Active nested Navigator receives back before its parent.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpAndSettle()
        assertEquals(1, outer.entries.size)
        assertEquals(1, inner.entries.size)
        assertFalse(rootBackDispatcher.hasRegisteredHandlers)

        inner.push(textRoute("inner-retained-detail", "INNER RETAINED DETAIL"))
        tester.pumpAndSettle()
        outer.push(textRoute("outer-cover", "OUTER COVER"))
        tester.pumpAndSettle()
        assertEquals(PixelRouteLifecycleState.Inactive, outerRootEntry?.lifecycleState)

        // The inactive nested dispatcher is skipped, so parent back removes its covering route.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpAndSettle()
        assertEquals(1, outer.entries.size)
        assertEquals(2, inner.entries.size)
        assertSame(inner, innerNavigator)

        // After the parent entry is active again, nested back resumes and removes its own detail.
        assertTrue(rootBackDispatcher.handleBack())
        tester.pumpAndSettle()
        assertEquals(1, inner.entries.size)
        assertFalse(rootBackDispatcher.handleBack())
        tester.dispose()
    }

    /** Verifies nested predictive progress stops at the inactive parent-entry boundary. */
    @Test
    fun nestedPredictiveBackIsUnregisteredWhileItsParentEntryIsInactive() {
        // Tester and host dispatcher represent a platform predictive-back session.
        val tester = PixelTester()
        val rootBackDispatcher = PixelBackDispatcher()
        // Callback records only events that cross the nested wrapper boundary.
        val nestedCallback = RecordingPredictiveBackCallback("nested")
        // Parent state and root identity are captured before another outer route covers them.
        var outerNavigator: PixelNavigatorState? = null
        var outerRootEntry: PixelRouteEntry<*, *>? = null
        // Inner root accepts predictive sessions when its parent route is active.
        val innerRoot = testRouteRequest(
            name = "predictive-inner-root",
            transition = PixelRouteTransition.None,
            builder = {
                PixelPredictiveBackHandler(
                    callback = nestedCallback,
                    child = Text("PREDICTIVE INNER"),
                )
            },
        )
        // Outer root supplies its concrete entry to the nested wrapper's back gate.
        val outerRoot = testRouteRequest(
            name = "predictive-outer-root",
            transition = PixelRouteTransition.None,
            builder = { context ->
                val outer = PixelNavigator.of(context)
                outerNavigator = outer
                if (outerRootEntry == null) outerRootEntry = outer.currentEntry
                PixelNestedNavigator(
                    initialRequest = innerRoot,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                    parentEntry = outerRootEntry,
                )
            },
        )
        tester.pumpWidget(
            PixelBackHost(
                dispatcher = rootBackDispatcher,
                child = PixelNavigator(
                    initialRequest = outerRoot,
                    vsync = tester.vsync,
                    defaultTransition = PixelRouteTransition.None,
                ),
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )
        val started = predictiveEvent(progress = 0f)
        val progressed = predictiveEvent(progress = 0.6f)

        assertTrue(rootBackDispatcher.startPredictiveBack(started))
        rootBackDispatcher.updatePredictiveBack(progressed)
        rootBackDispatcher.cancelPredictiveBack()
        assertEquals(
            listOf("nested:start:0.0", "nested:progress:0.6", "nested:cancel"),
            nestedCallback.events,
        )

        checkNotNull(outerNavigator).push(textRoute("predictive-cover", "PREDICTIVE COVER"))
        tester.pumpAndSettle()
        assertEquals(PixelRouteLifecycleState.Inactive, outerRootEntry?.lifecycleState)
        assertTrue(rootBackDispatcher.startPredictiveBack(started))
        rootBackDispatcher.updatePredictiveBack(progressed)
        rootBackDispatcher.cancelPredictiveBack()

        // Outer Navigator may own the gesture, but the hidden nested callback sees no new event.
        assertEquals(3, nestedCallback.events.size)
        tester.dispose()
    }

    /** Verifies a multi-stack deep link selects its target only after argument validation succeeds. */
    @Test
    fun multiStackTypedDeepLinkTargetsOnlyTheRequestedStackAndActivatesOnSuccess() {
        // Tester mounts both stack states before controller-targeted deep-link requests run.
        val tester = PixelTester()
        // Controller starts at home while the link targets settings.
        val controller = PixelMultiStackNavigatorController(initialStackId = "home")
        // Captured states reveal which stack changed.
        val navigatorStates = linkedMapOf<String, PixelNavigatorState>()
        // Stateless maps satisfy the shared stack factory while this test inspects entries directly.
        val probes = linkedMapOf<String, StackProbeState>()
        tester.pumpWidget(
            PixelMultiStackNavigator(
                stacks = listOf(
                    stackDefinition("home", "HOME", probes, navigatorStates),
                    stackDefinition("settings", "SETTINGS", probes, navigatorStates),
                ),
                controller = controller,
                vsync = tester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 48,
            logicalHeight = 16,
        )
        // Resolver decodes only valid profile links.
        val resolver = profileResolver(profileDestination())

        val rejected = controller.handleTypedDeepLink(
            stackId = "settings",
            uri = "pixel://app/profile",
            resolver = resolver,
        )

        assertTrue(rejected is PixelTypedDeepLinkRejected)
        assertEquals("home", controller.activeStackId)
        assertEquals(1, navigatorStates.getValue("settings").entries.size)

        val navigated = controller.handleTypedDeepLink(
            stackId = "settings",
            uri = "pixel://app/profile?id=9&source=shortcut",
            resolver = resolver,
        )
        tester.pumpAndSettle()

        assertTrue(navigated is PixelTypedDeepLinkNavigated)
        assertEquals("settings", controller.activeStackId)
        assertEquals(1, navigatorStates.getValue("home").entries.size)
        assertEquals(2, navigatorStates.getValue("settings").entries.size)
        assertEquals(
            ProfileArguments(9, "shortcut"),
            navigatorStates.getValue("settings").currentEntry.arguments,
        )
        assertTrue(tester.exists(find.byText("PROFILE 9")))

        val unavailable = controller.handleTypedDeepLink(
            stackId = "unknown",
            uri = "pixel://app/profile?id=2",
            resolver = resolver,
        )
        assertEquals(
            PixelTypedDeepLinkRejectionReason.StackUnavailable,
            (unavailable as PixelTypedDeepLinkRejected).reason,
        )
        assertEquals("settings", controller.activeStackId)
        tester.dispose()
    }

    /** Creates a typed destination used by all deep-link validation scenarios. */
    private fun profileDestination(): PixelRouteDestination<ProfileArguments, Unit> {
        return pixelRouteDestination(id = "profile", transition = PixelRouteTransition.None) { _, scope ->
            Text("PROFILE ${scope.arguments.userId}")
        }
    }

    /** Creates the deterministic profile URI matcher and argument decoder. */
    private fun profileResolver(
        destination: PixelRouteDestination<ProfileArguments, Unit>,
    ): PixelTypedDeepLinkResolver {
        val route = PixelTypedDeepLinkRoute(
            destination = destination,
            matcher = PixelTypedDeepLinkMatcher { link ->
                link.scheme == "pixel" &&
                    link.host == "app" &&
                    link.pathSegments == listOf("profile")
            },
            argumentDecoder = PixelDeepLinkArgumentDecoder { link ->
                val rawId = link.queryParameter("id")
                    ?: return@PixelDeepLinkArgumentDecoder PixelDeepLinkDecodeRejected(
                        PixelDeepLinkArgumentFailure(
                            reason = PixelDeepLinkArgumentFailureReason.Missing,
                            parameterName = "id",
                            message = "Profile deep link requires query parameter 'id'",
                        ),
                    )
                val userId = rawId.toIntOrNull()?.takeIf { value -> value > 0 }
                    ?: return@PixelDeepLinkArgumentDecoder PixelDeepLinkDecodeRejected(
                        PixelDeepLinkArgumentFailure(
                            reason = PixelDeepLinkArgumentFailureReason.Invalid,
                            parameterName = "id",
                            rawValue = rawId,
                            message = "Profile id must be a positive integer",
                        ),
                    )
                PixelDeepLinkDecoded(
                    ProfileArguments(
                        userId = userId,
                        source = link.queryParameter("source"),
                    ),
                )
            },
        )
        return PixelTypedDeepLinkResolver(listOf(route))
    }

    /** 创建一个可独立捕获、带保留状态探针的标签页根栈。 */
    private fun stackDefinition(
        id: String,
        label: String,
        probes: MutableMap<String, StackProbeState>,
        navigatorStates: MutableMap<String, PixelNavigatorState>,
    ): PixelTypedNavigatorStack<Unit, Any?> {
        return PixelTypedNavigatorStack(
            id = id,
            initialRequest = testRouteRequest(
                name = id,
                transition = PixelRouteTransition.None,
                builder = { context ->
                    navigatorStates[id] = PixelNavigator.of(context)
                    StackProbe(label = label, onReady = { state -> probes[id] = state })
                },
            ),
        )
    }

    /** 创建一个无动画、文本确定且不可交互的简单路由。 */
    private fun textRoute(name: String, text: String): PixelRouteRequest<Unit, Any?> {
        return testRouteRequest(
            name = name,
            transition = PixelRouteTransition.None,
            builder = { Text(text) },
        )
    }

    /** Creates one deterministic predictive-back frame for dispatcher forwarding tests. */
    private fun predictiveEvent(progress: Float): PixelPredictiveBackEvent {
        return PixelPredictiveBackEvent(
            progress = progress,
            touchX = 2f,
            touchY = 4f,
            swipeEdge = PixelPredictiveBackSwipeEdge.Left,
        )
    }
}

/** Strongly typed arguments accepted by the profile test destination. */
private data class ProfileArguments(
    /** Positive profile identifier decoded from the required query parameter. */
    val userId: Int,
    /** Optional launch source decoded independently from the required identifier. */
    val source: String?,
)

/** Stateful tab root used to prove inactive multi-stack children remain mounted. */
private class StackProbe(
    /** Text rendered by this stack. */
    val label: String,
    /** Callback exposing the exact retained State identity. */
    val onReady: (StackProbeState) -> Unit,
    /** Optional retained-tree key. */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** Creates one counter state for this stack root. */
    override fun createState(): State<out StatefulWidget> = StackProbeState()
}

/** Retained state whose counter, identity, and disposal are asserted across stack switches. */
private class StackProbeState : State<StackProbe>() {
    /** Tap count retained while this stack is inactive. */
    var counter: Int = 0
        private set

    /** Whether terminal subtree disposal has occurred. */
    var isDisposed: Boolean = false
        private set

    /** Exposes this exact state instance after first mount. */
    override fun initState() {
        widget.onReady(this)
    }

    /** Builds one click target and text label for interaction/semantics isolation assertions. */
    override fun build(context: BuildContext): Widget {
        return GestureDetector(
            onTap = {
                setState { counter += 1 }
            },
            child = Text("${widget.label}:$counter"),
            key = "stack-probe:${widget.label}",
        )
    }

    /** Records terminal unmount after the entire multi-stack host is disposed. */
    override fun dispose() {
        isDisposed = true
    }
}

/** Predictive callback that records every lifecycle event without mutating a route stack. */
private class RecordingPredictiveBackCallback(
    /** Stable stack label prepended to each recorded event. */
    private val stackId: String,
) : PixelPredictiveBackCallback {
    /** Ordered lifecycle events received by this one stack. */
    val events: MutableList<String> = mutableListOf()

    /** Accepts the gesture and records its initial progress. */
    override fun onBackStarted(event: PixelPredictiveBackEvent): Boolean {
        events += "$stackId:start:${event.progress}"
        return true
    }

    /** Records progress forwarded after this callback accepted start. */
    override fun onBackProgressed(event: PixelPredictiveBackEvent) {
        events += "$stackId:progress:${event.progress}"
    }

    /** Records cancellation without any terminal stack mutation. */
    override fun onBackCancelled() {
        events += "$stackId:cancel"
    }

    /** Records commit and reports that this stack consumed the gesture. */
    override fun onBackCommitted(): Boolean {
        events += "$stackId:commit"
        return true
    }
}
