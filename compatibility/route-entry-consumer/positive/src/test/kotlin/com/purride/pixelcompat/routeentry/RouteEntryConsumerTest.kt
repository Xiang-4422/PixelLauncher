package com.purride.pixelcompat.routeentry

import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteCancellationReason
import com.purride.pixelui.PixelRouteCancelled
import com.purride.pixelui.PixelRouteOutcome
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteResultState
import com.purride.pixelui.PixelRouteStateKey
import com.purride.pixelui.PixelRouteSuccess
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.Text
import com.purride.pixelui.pixelRouteDestination
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * External consumer contract for the root-package typed route-entry API.
 *
 * This test is compiled only against the AAR published into the script-owned file repository.
 */
public class RouteEntryConsumerTest {
    /**
     * Proves duplicate requests own independent arguments, IDs, buckets, and terminal outcomes.
     */
    @Test
    public fun duplicateTypedDestinationEntriesRemainIsolatedAndDistinguishNullFromCancellation() {
        // PixelTester provides a real retained widget host without an Android device.
        val tester = PixelTester()
        // The mounted compatibility root exposes the public navigator controller.
        var navigator: PixelNavigatorState? = null
        // A legacy root keeps the stack valid while both typed entries are completed.
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        // One reusable typed destination deliberately creates two independent stack entries.
        val destination = pixelRouteDestination<String, String?>(
            id = "external-nullable-editor",
            transition = PixelRouteTransition.None,
        ) { _, scope ->
            Text(scope.arguments)
        }
        // The same typed key object makes cross-entry bucket leakage directly observable.
        val draftKey = PixelRouteStateKey<String>("draft")
        // Lower outcomes must remain pending while the top entry completes successfully.
        val lowerOutcomes = mutableListOf<PixelRouteOutcome<String?>>()
        // Top outcomes must preserve successful null rather than converting it to cancellation.
        val topOutcomes = mutableListOf<PixelRouteOutcome<String?>>()

        tester.pumpWidget(
            widget = PixelNavigator(
                initialRoute = root,
                vsync = tester.vsync,
                defaultTransition = PixelRouteTransition.None,
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )
        // Initial mounting has built the root and captured its navigator state.
        val mountedNavigator = checkNotNull(navigator)
        // First request captures its own String argument and result callback.
        val lowerEntry = mountedNavigator.push(
            PixelRouteRequest(destination, "lower-argument"),
        ) { outcome -> lowerOutcomes += outcome }
        lowerEntry.stateBucket.write(draftKey, "lower-draft")
        tester.pumpAndSettle()

        // Second request reuses the destination while supplying a different String argument.
        val topEntry = mountedNavigator.push(
            PixelRouteRequest(destination, "top-argument"),
        ) { outcome -> topOutcomes += outcome }
        topEntry.stateBucket.write(draftKey, "top-draft")
        tester.pumpAndSettle()

        assertNotEquals(lowerEntry.id, topEntry.id)
        assertEquals("lower-argument", lowerEntry.arguments)
        assertEquals("top-argument", topEntry.arguments)
        assertNotSame(lowerEntry.stateBucket, topEntry.stateBucket)
        assertNotSame(lowerEntry.resultChannel, topEntry.resultChannel)
        assertEquals("lower-draft", lowerEntry.stateBucket.read(draftKey))
        assertEquals("top-draft", topEntry.stateBucket.read(draftKey))
        assertTrue(tester.exists(find.byText("top-argument")))

        // A nullable successful result must remain distinguishable from cancellation.
        assertTrue(mountedNavigator.complete(topEntry, null))
        tester.pumpAndSettle()

        assertEquals(
            PixelRouteSuccess<String?>(null),
            topOutcomes.single(),
        )
        assertEquals(PixelRouteResultState.Succeeded, topEntry.resultChannel.state)
        assertEquals(PixelRouteResultState.Pending, lowerEntry.resultChannel.state)
        assertTrue(tester.exists(find.byText("lower-argument")))

        // Explicit cancellation resolves only the still-active lower entry.
        assertTrue(
            mountedNavigator.cancel(
                entry = lowerEntry,
                reason = PixelRouteCancellationReason.Explicit,
                animated = false,
            ),
        )

        assertEquals(
            PixelRouteCancelled(PixelRouteCancellationReason.Explicit),
            lowerOutcomes.single(),
        )
        assertEquals(PixelRouteResultState.Cancelled, lowerEntry.resultChannel.state)
        assertFalse(mountedNavigator.cancel(lowerEntry, animated = false))
        assertEquals(1, lowerOutcomes.size)
        assertEquals(1, topOutcomes.size)
        tester.dispose()
    }
}
