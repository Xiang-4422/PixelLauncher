package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Text
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.animated.AnimatedSprite
import com.purride.pixelui.widgets.navigation.PixelNavigator
import com.purride.pixelui.widgets.navigation.PixelNavigatorState
import com.purride.pixelui.widgets.navigation.PixelRoute
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
        val root = PixelRoute(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )
        tester.pumpWidget(PixelNavigator(root, tester.vsync), 32, 12)

        repeat(12) { index ->
            val route = PixelRoute(
                name = "route-$index",
                builder = { Text("R$index") },
                onDispose = { disposed += "route-$index" },
            )
            navigator!!.push(route)
            tester.pumpAndSettle()
            assertEquals(route.name, navigator!!.currentRoute.name)
            assertTrue(navigator!!.pop())
            tester.pumpAndSettle()
            assertEquals("root", navigator!!.currentRoute.name)
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
