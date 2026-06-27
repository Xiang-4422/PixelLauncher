package com.purride.pixelui.foundation

import com.purride.pixelui.Builder
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelToastQueueController
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.ToastQueue
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelOverlayTest {
    @Test
    fun modalBarrierInvokesDismissWhenDismissible() {
        val tester = PixelTester()
        var dismissed = 0

        tester.pumpWidget(
            widget = Stack(
                children = listOf(
                    Text("HOME"),
                    ModalBarrier(
                        dismissible = true,
                        onDismiss = { dismissed += 1 },
                        key = "barrier",
                    ),
                ),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        tester.tap(find.byKey("barrier"))
        assertEquals(1, dismissed)
        tester.dispose()
    }

    @Test
    fun toastQueueShowsCurrentMessageAndAdvances() {
        val controller = PixelToastQueueController()
        val tester = PixelTester()

        tester.pumpWidget(
            widget = ToastQueue(controller = controller),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertFalse(tester.exists(find.byText("ONE")))
        val first = controller.enqueue("ONE")
        controller.enqueue("TWO")
        tester.pumpFrame(16)

        assertEquals(2, controller.size)
        assertEquals(first, controller.current)
        assertTrue(tester.exists(find.byText("ONE")))
        assertFalse(tester.exists(find.byText("TWO")))

        assertTrue(controller.dismissCurrent())
        tester.pumpFrame(16)

        assertEquals(1, controller.size)
        assertTrue(tester.exists(find.byText("TWO")))
        tester.dispose()
    }

    @Test
    fun controllerShowsAndDismissesToast() {
        val controller = PixelOverlayController()
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Text("HOME"),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertTrue(tester.exists(find.byText("HOME")))
        assertFalse(tester.exists(find.byText("SAVED")))

        val handle = controller.showToast("SAVED")
        tester.pumpFrame(16)

        assertEquals(1, controller.size)
        assertTrue(tester.exists(find.byText("SAVED")))

        assertTrue(handle.dismiss())
        tester.pumpFrame(16)

        assertEquals(0, controller.size)
        assertFalse(tester.exists(find.byText("SAVED")))
        tester.dispose()
    }

    @Test
    fun dialogAndSnackbarUseExistingWidgets() {
        val controller = PixelOverlayController()
        val tester = PixelTester()
        var confirmed = 0
        var undone = 0

        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Text("HOME"),
            ),
            logicalWidth = 80,
            logicalHeight = 32,
        )

        controller.showDialog(
            title = Text("TITLE"),
            content = Text("BODY"),
            actions = listOf(OutlinedButton("OK", onPressed = { confirmed++ })),
        )
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("TITLE")))
        assertTrue(tester.exists(find.byText("BODY")))
        tester.tap(find.byText("OK"))
        assertEquals(1, confirmed)

        controller.clear()
        controller.showSnackbar(
            message = "QUEUED",
            action = OutlinedButton("UNDO", onPressed = { undone++ }),
        )
        tester.pumpFrame(16)

        assertTrue(tester.exists(find.byText("QUEUED")))
        tester.tap(find.byText("UNDO"))
        assertEquals(1, undone)
        tester.dispose()
    }

    @Test
    fun controllerCanBeReadFromBuildContext() {
        val controller = PixelOverlayController()
        val tester = PixelTester()

        tester.pumpWidget(
            widget = PixelOverlayHost(
                controller = controller,
                child = Builder { context ->
                    OutlinedButton(
                        text = "SHOW",
                        onPressed = {
                            PixelOverlayController.of(context).showToast("FROM CONTEXT")
                        },
                    )
                },
            ),
            logicalWidth = 96,
            logicalHeight = 24,
        )

        tester.tap(find.byText("SHOW"))

        assertTrue(tester.exists(find.byText("FROM CONTEXT")))
        tester.dispose()
    }
}
