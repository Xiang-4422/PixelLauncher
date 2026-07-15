package com.purride.pixelcompat.legacy

import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.navigation.PixelDeepLinkResolver
import com.purride.pixelui.widgets.navigation.PixelNavigator
import com.purride.pixelui.widgets.navigation.PixelNavigatorState
import com.purride.pixelui.widgets.navigation.PixelRoute

/**
 * Legacy navigation entry point compiled only against the reviewed frozen engine AAR.
 *
 * The current runner invokes this object reflectively, ensuring every navigation call site below
 * remains old consumer bytecode while the runtime supplies only the current engine implementation.
 */
public object LegacyNavigatorBinaryProbe {
    /** Exercises the frozen Navigator API and returns a primitive-only deterministic summary. */
    @JvmStatic
    public fun run(): String {
        /** Public engine harness used to mount the legacy two-argument Navigator constructor. */
        val tester = PixelTester()
        /** Navigator state captured through the old route builder and inherited lookup API. */
        var navigator: PixelNavigatorState? = null
        /** Root route compiled against the frozen PixelRoute constructor. */
        val rootRoute = PixelRoute(
            name = "legacy-root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("LEGACY ROOT")
            },
        )
        tester.pumpWidget(
            widget = PixelNavigator(rootRoute, tester.vsync),
            logicalWidth = 24,
            logicalHeight = 8,
        )

        /** Non-null mounted state proving the old constructor and route builder linked at runtime. */
        val state = checkNotNull(navigator) { "Legacy PixelNavigator route did not mount" }
        /** One route object deliberately reused for two independent legacy stack slots. */
        val sharedRoute = PixelRoute(
            name = "legacy-shared",
            builder = { Text("LEGACY SHARED") },
        )
        /** Result deliveries used to prove duplicate stack slots retain independent callbacks. */
        val duplicateResults = mutableListOf<String>()
        state.push(sharedRoute) { result -> duplicateResults += "first=$result" }
        tester.pumpAndSettle()
        state.push(sharedRoute) { result -> duplicateResults += "second=$result" }
        tester.pumpAndSettle()

        /** Route-name snapshot captured while both occurrences of the same route are present. */
        val duplicateSnapshot = state.snapshot().routeNames.joinToString(separator = ">")
        /** Whether the top duplicate accepted its old untyped result completion. */
        val topPopped = state.pop("top")
        tester.pumpAndSettle()
        /** Whether the lower duplicate accepted its independent old untyped result completion. */
        val lowerPopped = state.pop("bottom")
        tester.pumpAndSettle()

        /** Original route whose callback must transfer to its replacement stack slot. */
        val replaceSource = PixelRoute(
            name = "replace-source",
            builder = { Text("REPLACE SOURCE") },
        )
        /** Replacement route created through the frozen PixelRoute constructor. */
        val replaceTarget = PixelRoute(
            name = "replace-target",
            builder = { Text("REPLACE TARGET") },
        )
        /** Deliveries proving replacement preserves the old stack-slot callback contract. */
        val replacementResults = mutableListOf<String>()
        state.push(replaceSource) { result -> replacementResults += "replace=$result" }
        tester.pumpAndSettle()
        state.replace(replaceTarget, animated = false)
        /** Route exposed by the old currentRoute getter immediately after replacement. */
        val replacementRouteName = state.currentRoute.name
        /** Whether popping the replacement completed the callback registered on its predecessor. */
        val replacementPopped = state.pop("transferred")
        tester.pumpAndSettle()

        /** Root selected by the legacy deep-link resolver. */
        val deepRoot = PixelRoute(
            name = "deep-root",
            builder = { Text("DEEP ROOT") },
        )
        /** Foreground route selected by the legacy deep-link resolver. */
        val deepLeaf = PixelRoute(
            name = "deep-leaf",
            builder = { Text("DEEP LEAF") },
        )
        /** Whether the resolver received the parsed legacy URI fields it expects. */
        var resolverMatched = false
        /** Whether the old string deep-link overload committed the resolver's stack. */
        val deepLinkHandled = state.handleDeepLink(
            uri = "pixel://legacy/leaf?mode=old",
            resolver = PixelDeepLinkResolver { link ->
                resolverMatched =
                    link.host == "legacy" &&
                        link.pathSegments == listOf("leaf") &&
                        link.queryParameter("mode") == "old"
                if (resolverMatched) listOf(deepRoot, deepLeaf) else null
            },
            animated = false,
        )
        /** Final old stack projection after deep-link replacement. */
        val deepLinkStack = state.stack.joinToString(separator = ">") { route -> route.name }
        tester.dispose()

        return "mounted=true;" +
            "duplicate=$duplicateSnapshot;" +
            "pops=$topPopped,$lowerPopped;" +
            "results=${duplicateResults.joinToString(separator = ",")};" +
            "replace=$replacementRouteName,$replacementPopped," +
            replacementResults.joinToString(separator = ",") + ";" +
            "deep=$resolverMatched,$deepLinkHandled,$deepLinkStack"
    }
}
