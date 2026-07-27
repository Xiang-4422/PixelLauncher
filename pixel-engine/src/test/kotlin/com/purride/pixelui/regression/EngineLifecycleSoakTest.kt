package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.AsyncBuilder
import com.purride.pixelui.PixelAsyncSource
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.Text
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.animated.AnimatedSprite
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.valueOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineLifecycleSoakTest {
    @Test
    fun animatedSpriteLongRunDisposesTicker() {
        val tester = PixelTester()
        tester.pumpWidget(AnimatedSprite(sheet = spriteSheet(), fps = 12, vsync = tester.vsync), 2, 2)
        assertEquals(1, tester.vsync.activeTickerCount)

        repeat(180) {
            tester.pumpFrame(16)
        }
        assertTrue(tester.renderResult!!.buffer.pixels.any { it != PixelColor.Transparent.argb })

        tester.dispose()
        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
    }

    @Test
    fun navigatorRepeatedPushPopSettlesAndDisposesLeavingRoutes() {
        val tester = PixelTester()
        val disposed = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = testRouteRequest(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        repeat(12) { index ->
            val route = testRouteRequest(
                name = "route-$index",
                builder = { Text("R$index") },
                onDispose = { disposed += "route-$index" },
            )
            navigator!!.push(route)
            tester.pumpAndSettle()
            assertEquals(route.destination.id, navigator!!.currentEntry.destination.id)
            assertTrue(navigator!!.pop())
            tester.pumpAndSettle()
            assertEquals("root", navigator!!.currentEntry.destination.id)
        }

        assertEquals((0 until 12).map { "route-$it" }, disposed)
        assertEquals(0, tester.vsync.activeTickerCount)
        tester.dispose()
    }

    @Test
    fun listFlingLongRunSettlesAtBoundary() {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = 10f)
        controller.sync(state, viewportHeightPx = 20, contentHeightPx = 200)
        controller.endDrag(
            state = state,
            velocityPxPerSecond = -1800f,
            viewportHeightPx = 20,
            contentHeightPx = 200,
        )

        repeat(120) {
            controller.step(state, deltaMs = 16, viewportHeightPx = 20, contentHeightPx = 200)
        }

        assertFalse(controller.isActive(state))
        assertEquals(state.maxScrollOffsetPx, state.scrollOffsetPx, 0.001f)
        assertEquals(0f, state.scrollVelocityPxPerSecond, 0.001f)
    }

    @Test
    fun pagerFlingLongRunSettlesAtTargetAndStops() {
        val controller = PixelPagerController()
        val state = controller.create(pageCount = 5, currentPage = 2, axis = PixelAxis.HORIZONTAL)
        controller.startDrag(state, viewportSizePx = 100)
        controller.dragBy(state, deltaPx = -48f, viewportSizePx = 100)
        controller.endDrag(state, viewportSizePx = 100, velocityPxPerSecond = -900f)

        repeat(120) {
            controller.step(state, deltaMs = 16)
        }

        assertFalse(controller.isActive(state))
        assertEquals(3, state.currentPage)
        assertEquals(3, state.settleTargetPage)
        assertFalse(state.isDragging)
        assertFalse(state.isSettling)
    }

    @Test
    fun navigatorRepeatedReplaceAndPopToRootSettlesCallbacksAndDisposesRoutes() {
        val tester = PixelTester()
        val disposed = mutableListOf<String>()
        val results = mutableListOf<String>()
        var navigator: PixelNavigatorState? = null
        val root = testRouteRequest(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        repeat(10) { index ->
            val first = testRouteRequest(
                name = "first-$index",
                builder = { Text("FIRST $index") },
                onDispose = { disposed += "first-$index" },
            )
            val second = testRouteRequest(
                name = "second-$index",
                builder = { Text("SECOND $index") },
                onDispose = { disposed += "second-$index" },
            )
            val third = testRouteRequest(
                name = "third-$index",
                builder = { Text("THIRD $index") },
                onDispose = { disposed += "third-$index" },
            )

            navigator!!.push(first) { outcome -> results += "first-$index=${outcome.valueOrNull()}" }
            tester.pumpAndSettle()
            navigator!!.replace(second)
            tester.pumpAndSettle()
            assertTrue("replace should dispose the outgoing route", "first-$index" in disposed)
            assertEquals(0, tester.vsync.activeTickerCount)

            navigator!!.push(third) { outcome -> results += "third-$index=${outcome.valueOrNull()}" }
            tester.pumpAndSettle()
            navigator!!.popToRoot(animated = true)
            tester.pumpAndSettle()

            assertEquals(listOf("root"), navigator!!.entries.map { entry -> entry.destination.id })
            assertTrue("popToRoot should dispose the replaced route", "second-$index" in disposed)
            assertTrue("popToRoot should dispose the pushed route", "third-$index" in disposed)
            assertTrue("replace should settle the outgoing route callback", "first-$index=null" in results)
            assertTrue("popToRoot should complete top route callback", "third-$index=null" in results)
            assertEquals(0, tester.vsync.activeTickerCount)
        }

        assertEquals(30, disposed.size)
        assertEquals(20, results.size)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    @Test
    fun multipleTesterHostsKeepTickerStateIsolated() {
        val first = PixelTester()
        val second = PixelTester()
        try {
            first.pumpWidget(AnimatedSprite(sheet = spriteSheet(), fps = 8, vsync = first.vsync), 2, 2)
            second.pumpWidget(AnimatedSprite(sheet = spriteSheet(), fps = 8, vsync = second.vsync), 2, 2)

            assertEquals(1, first.vsync.activeTickerCount)
            assertEquals(1, second.vsync.activeTickerCount)

            repeat(90) {
                first.pumpFrame(16)
                second.pumpFrame(16)
            }

            first.dispose()
            assertEquals(0, first.vsync.activeTickerCount)
            assertEquals(1, second.vsync.activeTickerCount)

            repeat(30) {
                second.pumpFrame(16)
            }
        } finally {
            first.dispose()
            second.dispose()
        }

        assertEquals(0, first.vsync.activeTickerCount)
        assertEquals(0, second.vsync.activeTickerCount)
        assertEquals(0, first.scheduler.pendingCount)
        assertEquals(0, second.scheduler.pendingCount)
    }

    @Test
    fun tickerBurstDisposeDrainsScheduler() {
        val tester = PixelTester()
        val ticks = IntArray(40)
        val tickers = List(ticks.size) { index ->
            tester.vsync.createTicker { ticks[index] += 1 }
        }

        tickers.forEach { ticker -> ticker.start() }
        assertEquals(ticks.size, tester.vsync.activeTickerCount)
        assertEquals(1, tester.scheduler.pendingCount)

        repeat(8) {
            tester.pumpFrame(16)
        }
        assertTrue(ticks.all { it > 0 })

        tickers.forEach { ticker -> ticker.dispose() }
        tester.pumpFrame(16)

        assertEquals(0, tester.vsync.activeTickerCount)
        assertEquals(0, tester.scheduler.pendingCount)
        tester.dispose()
    }

    @Test
    fun repeatedAsyncBuilderMountDisposeUnsubscribesEveryListener() {
        var subscriptions = 0
        var unsubscriptions = 0
        val source = PixelAsyncSource<Int> { listener ->
            subscriptions += 1
            listener(PixelAsyncSnapshot.Success(subscriptions))
            return@PixelAsyncSource { unsubscriptions += 1 }
        }

        repeat(30) {
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    AsyncBuilder(source = source) { _, snapshot ->
                        Text((snapshot as? PixelAsyncSnapshot.Success)?.value?.toString().orEmpty())
                    },
                    logicalWidth = 24,
                    logicalHeight = 8,
                )
            } finally {
                tester.dispose()
            }
        }

        assertEquals(30, subscriptions)
        assertEquals(30, unsubscriptions)
    }

    private fun spriteSheet(): PixelSpriteSheet {
        val color = PixelColor.fromRgb(200, 100, 0).argb
        val pixels = intArrayOf(
            PixelColor.White.argb,
            PixelColor.White.argb,
            color,
            color,
        )
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 4, height = 1, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(left = 0, top = 0, width = 2, height = 1),
                PixelBitmapRegion(left = 2, top = 0, width = 2, height = 1),
            ),
        )
    }
}
