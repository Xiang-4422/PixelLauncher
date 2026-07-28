package com.purride.pixelui.foundation

import com.purride.pixelui.PixelBackDispatcher
import com.purride.pixelui.PixelBackHost
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayDismissReason
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.Text
import com.purride.pixelui.internal.host.handlePixelHostBack
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testRouteRequest
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelBackTest {
    @Test
    fun dispatcherUsesLastRegisteredHandlerFirst() {
        val dispatcher = PixelBackDispatcher()
        var first = 0
        var second = 0

        dispatcher.register {
            first++
            true
        }
        dispatcher.register {
            second++
            true
        }

        assertTrue(dispatcher.handleBack())
        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun registrationDisposeRemovesHandler() {
        val dispatcher = PixelBackDispatcher()
        var calls = 0
        val registration = dispatcher.register {
            calls++
            true
        }

        registration.dispose()

        assertFalse(dispatcher.handleBack())
        assertEquals(0, calls)
    }

    @Test
    fun hostBackClearsTextInputBeforeWidgetBackAndFallback() {
        val dispatcher = PixelBackDispatcher()
        var cleared = 0
        var widgetBack = 0
        var fallback = 0
        var handled = 0
        dispatcher.register {
            widgetBack++
            true
        }

        assertTrue(
            handlePixelHostBack(
                hasFocusedTextInput = true,
                clearFocusedTextInput = { cleared++ },
                backDispatcher = dispatcher,
                onUnhandledBack = {
                    fallback++
                    true
                },
                onHandled = { handled++ },
            ),
        )

        assertEquals(1, cleared)
        assertEquals(0, widgetBack)
        assertEquals(0, fallback)
        assertEquals(1, handled)
    }

    @Test
    fun hostBackFallsBackAfterWidgetStackDeclines() {
        val dispatcher = PixelBackDispatcher()
        var fallback = 0
        dispatcher.register { false }

        assertTrue(
            handlePixelHostBack(
                hasFocusedTextInput = false,
                clearFocusedTextInput = {},
                backDispatcher = dispatcher,
                onUnhandledBack = {
                    fallback++
                    true
                },
                onHandled = {},
            ),
        )

        assertEquals(1, fallback)
    }

    @Test
    fun passiveNotificationDoesNotInterceptNavigatorBack() {
        val dispatcher = PixelBackDispatcher()
        val overlay = PixelOverlayController()
        val tester = PixelTester()
        var navigator: com.purride.pixelui.PixelNavigatorState? = null
        val root = testRouteRequest(
            name = "root",
            builder = { context ->
                navigator = PixelNavigator.of(context)
                Text("ROOT")
            },
        )

        tester.pumpWidget(
            widget = PixelBackHost(
                dispatcher = dispatcher,
                child = PixelOverlayHost(
                    controller = overlay,
                    child = PixelNavigator(
                        initialRequest = root,
                        vsync = tester.vsync,
                        defaultTransition = PixelRouteTransition.None,
                    ),
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        navigator!!.push(
            testRouteRequest(
                name = "detail",
                builder = { Text("DETAIL") },
            ),
        )
        tester.pumpFrame(16)
        assertTrue(tester.exists(find.byText("DETAIL")))

        val toast = overlay.showToast("TOAST")
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("DETAIL")))
        assertTrue(tester.exists(find.byText("TOAST")))

        assertTrue(dispatcher.handleBack())
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("ROOT")))
        assertFalse(tester.exists(find.byText("DETAIL")))
        assertTrue(tester.exists(find.byText("TOAST")))

        assertTrue(toast.dismiss(PixelOverlayDismissReason.Handle))
        tester.pumpFrame(16)
        assertFalse(tester.exists(find.byText("TOAST")))
        tester.dispose()
    }
}
