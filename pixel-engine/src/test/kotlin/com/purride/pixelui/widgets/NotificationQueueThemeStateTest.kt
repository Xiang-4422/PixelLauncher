package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelSnackbarQueueController
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelToastQueueController
import com.purride.pixelui.SnackbarQueue
import com.purride.pixelui.ToastQueue
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies notification queues preserve metadata while resolving mounted theme and state policy. */
class NotificationQueueThemeStateTest {
    /** Presentation Loading wins visual priority while item Error remains separately announced. */
    @Test
    fun toastQueueCombinesPresentationAndItemStatesAtMountTime() {
        /** Unique Loading surface color expected from fixed global state priority. */
        val loadingColor = PixelColor.fromRgb(17, 83, 149)
        /** Unique Error surface color that must lose visual priority to Loading. */
        val errorColor = PixelColor.fromRgb(151, 37, 53)
        /** Custom labels proving semantics are resolved from the mounted theme. */
        val labels = PixelLabelTokens.Default.copy(loading = "BUSY", error = "FAILED")
        /** Complete mounted graph with distinct status colors and labels. */
        val tokens = PixelThemeTokens.Default.copy(
            colors = PixelThemeTokens.Default.colors.copy(
                warning = loadingColor,
                danger = errorColor,
            ),
            labels = labels,
        )
        /** Queue whose item stores Error independently from presentation state. */
        val controller = PixelToastQueueController()
        controller.enqueue(
            message = "STATUS",
            states = PixelControlStateSet.of(PixelControlState.Error),
        )
        /** Off-screen renderer with no MotionScope, keeping timeout ownership manual. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = tokens,
                    child = ToastQueue(
                        controller = controller,
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 24,
            )

            assertTrue(tester.hasPixel(loadingColor))
            /** Toast live-region node keeps both status announcements despite visual priority. */
            val statusNode = tester.semanticsNodesByLabel("STATUS").single()
            assertEquals("BUSY", statusNode.value)
            assertEquals("FAILED", statusNode.error)
            assertEquals(1, controller.size)
        } finally {
            tester.dispose()
        }
    }

    /** Explicit queued visual values stay above Error component and foundation tokens. */
    @Test
    fun explicitToastQueueVisualsOverrideStateThemeValues() {
        /** Explicit fill that must remain visible over the theme Error role. */
        val explicitFill = PixelColor.fromRgb(91, 29, 173)
        /** Explicit text foreground that must remain visible over OnDanger. */
        val explicitText = PixelColor.fromRgb(23, 211, 193)
        /** Theme Error fill that should not replace the explicit queue value. */
        val themeError = PixelColor.fromRgb(187, 41, 61)
        /** Queue carrying explicit visual values in binary-safe presentation metadata. */
        val controller = PixelToastQueueController()
        controller.enqueue(
            message = "EXPLICIT",
            states = PixelControlStateSet.of(PixelControlState.Error),
            fillColor = explicitFill,
            textStyle = PixelTextStyle(color = explicitText),
        )
        /** Off-screen renderer for exact ARGB precedence assertions. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(
                        colors = PixelThemeTokens.Default.colors.copy(danger = themeError),
                    ),
                    child = ToastQueue(controller),
                ),
                logicalWidth = 80,
                logicalHeight = 24,
            )

            assertTrue(tester.hasPixel(explicitFill))
            assertTrue(tester.hasPixel(explicitText))
            assertFalse(tester.hasPixel(themeError))
        } finally {
            tester.dispose()
        }
    }

    /** Item-level Loading removes Snackbar action focus and blocks every consumption adapter. */
    @Test
    fun loadingSnackbarActionIsRemovedAndCannotBeConsumed() {
        /** Number of business callbacks observed by direct, semantic, and keyboard activation. */
        var actionCount = 0
        /** Queue whose first item owns one Loading action. */
        val controller = PixelSnackbarQueueController()
        /** Stable item id used for direct controller-consumption verification. */
        val item = controller.enqueue(
            message = "WAIT",
            states = PixelControlStateSet.of(PixelControlState.Loading),
            actionLabel = "RETRY",
            onAction = { actionCount += 1 },
        )
        /** Deterministic runtime exposing focus, semantics, and keyboard activation. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = SnackbarQueue(controller),
                logicalWidth = 96,
                logicalHeight = 24,
            )

            assertFalse(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("RETRY").isEmpty())
            assertFalse(tester.pressKey(PixelKey.ENTER))
            assertFalse(controller.performAction(item.id))
            assertFalse(controller.performCurrentAction())
            assertEquals(0, actionCount)
            assertEquals(1, controller.size)
            assertTrue(tester.semanticsNodesByLabel("RETRY").isEmpty())
        } finally {
            tester.dispose()
        }
    }
}
