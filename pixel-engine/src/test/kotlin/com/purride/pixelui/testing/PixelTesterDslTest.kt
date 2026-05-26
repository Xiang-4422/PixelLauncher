package com.purride.pixelui.testing

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Axis
import com.purride.pixelui.Column
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelTesterDslTest {
    @Test
    fun tapByTextInvokesButtonCallback() {
        val tester = PixelTester()
        var tapped = 0

        tester.pumpWidget(
            widget = OutlinedButton(text = "OK", onPressed = { tapped++ }),
            logicalWidth = 24,
            logicalHeight = 10,
        )
        tester.tap(find.byText("OK"))

        assertEquals(1, tapped)
        tester.dispose()
    }

    @Test
    fun tapNthByTextInvokesMatchingButtonCallback() {
        val tester = PixelTester()
        var first = 0
        var second = 0

        tester.pumpWidget(
            widget = Column(
                children = listOf(
                    OutlinedButton(text = "OK", onPressed = { first++ }),
                    OutlinedButton(text = "OK", onPressed = { second++ }),
                ),
                spacing = 1,
            ),
            logicalWidth = 24,
            logicalHeight = 24,
        )
        tester.tap(find.byText("OK").nth(1))

        assertEquals(0, first)
        assertEquals(1, second)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesListThroughRenderTarget() {
        val tester = PixelTester()
        val controller = PixelListController()
        val state = controller.create()

        tester.pumpWidget(
            widget = ListViewBuilder(
                itemCount = 20,
                itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                itemExtent = 6,
                state = state,
                controller = controller,
                key = "list",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )
        tester.drag(find.byKey("list"), dx = 0, dy = -12)

        assertTrue(state.scrollOffsetPx > 0f)
        tester.dispose()
    }

    @Test
    fun dragByKeyMovesPagerThroughRenderTarget() {
        val tester = PixelTester()
        val controller = PixelPagerController(distanceThresholdFraction = 0.2f)
        val state = controller.create(pageCount = 2)

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.HORIZONTAL,
                controller = controller,
                state = state,
                pages = listOf(Text("ONE"), Text("TWO")),
                key = "pager",
            ),
            logicalWidth = 30,
            logicalHeight = 10,
        )
        tester.drag(find.byKey("pager"), dx = -20, dy = 0)
        tester.pumpAndSettle()

        assertEquals(1, state.currentPage)
        tester.dispose()
    }

    @Test
    fun dragListAtEdgeHandsOffToPagerTarget() {
        val tester = PixelTester()
        val pagerController = PixelPagerController(distanceThresholdFraction = 0.2f)
        val pagerState = pagerController.create(pageCount = 2, currentPage = 1, axis = Axis.VERTICAL)
        val listController = PixelListController()
        val listState = listController.create()

        tester.pumpWidget(
            widget = PageView(
                axis = Axis.VERTICAL,
                controller = pagerController,
                state = pagerState,
                pages = listOf(
                    Text("FIRST"),
                    ListViewBuilder(
                        itemCount = 12,
                        itemBuilder = { index -> SizedBox(height = 6, child = Text("ROW $index")) },
                        itemExtent = 6,
                        state = listState,
                        controller = listController,
                        key = "list",
                    ),
                ),
                key = "pager",
            ),
            logicalWidth = 40,
            logicalHeight = 18,
        )

        tester.drag(find.byKey("list"), dx = 0, dy = 14)
        tester.pumpAndSettle()

        assertEquals(0, pagerState.currentPage)
        assertEquals(0f, listState.scrollOffsetPx, 0.001f)
        tester.dispose()
    }

    @Test
    fun enterTextComposeAndSubmitUseTextInputTarget() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create()
        var changed = ""
        var submitted = ""

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                placeholder = "NAME",
                onChanged = { changed = it },
                onSubmitted = { submitted = it },
                key = "field",
            ),
            logicalWidth = 40,
            logicalHeight = 12,
        )

        tester.enterText(find.byKey("field"), "AB")
        tester.composeText(find.byKey("field"), "C")
        tester.submitTextInput()

        assertEquals("ABC", state.text)
        assertEquals(2, state.compositionStart)
        assertEquals(3, state.compositionEnd)
        assertEquals("ABC", changed)
        assertEquals("ABC", submitted)
        tester.dispose()
    }

    @Test
    fun tapAndDragTextFieldFocusAndUpdateSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCD")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )

        tester.tap(find.byKey("field"))
        assertTrue(state.isFocused)

        tester.drag(find.byKey("field"), dx = -20, dy = 0)
        assertTrue(state.selectionStart < state.text.length)
        tester.dispose()
    }

    @Test
    fun doubleTapTextFieldSelectsWord() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "HELLO")

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        tester.doubleTap(find.byKey("field"))

        assertTrue(state.isFocused)
        assertEquals("HELLO", state.text.substring(state.selectionStart, state.selectionEnd))
        tester.dispose()
    }

    @Test
    fun dragSelectionHandlesUpdatesTextFieldSelection() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create(initialText = "ABCDE", selectionStart = 1, selectionEnd = 3)
        controller.focus(state)

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                key = "field",
            ),
            logicalWidth = 80,
            logicalHeight = 12,
        )

        tester.dragSelectionEndHandle(find.byKey("field"), dx = 28, dy = 0)

        assertEquals(1, state.selectionStart)
        assertTrue("end handle should extend the selection", state.selectionEnd > 3)
        tester.dispose()
    }

    @Test
    fun enterTextRejectsReadOnlyTextField() {
        val tester = PixelTester()
        val controller = PixelTextFieldController()
        val state = controller.create()

        tester.pumpWidget(
            widget = TextField(
                state = state,
                controller = controller,
                readOnly = true,
                key = "field",
            ),
            logicalWidth = 48,
            logicalHeight = 12,
        )

        try {
            tester.enterText(find.byKey("field"), "NOPE")
            error("enterText should reject readOnly fields")
        } catch (error: IllegalStateException) {
            assertTrue(error.message.orEmpty().contains("readOnly"))
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun missingFinderFailureIncludesCandidatesAndWidgetTree() {
        val tester = PixelTester()
        tester.pumpWidget(
            widget = OutlinedButton(text = "OK", onPressed = {}),
            logicalWidth = 24,
            logicalHeight = 10,
        )

        try {
            tester.tap(find.byText("MISSING"))
            error("tap should fail for missing finder")
        } catch (error: IllegalStateException) {
            val message = error.message.orEmpty()
            assertTrue(message.contains("Finder diagnostics"))
            assertTrue(message.contains("matches=0"))
            assertTrue(message.contains("Widget tree"))
            assertTrue(message.contains("OutlinedButtonWidget"))
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun pumpWidgetRendersBuffer() {
        val tester = PixelTester()
        tester.pumpWidget(Text("A", color = PixelColor.White), 8, 8)
        val lit = tester.renderResult!!.buffer.pixels.count { it != PixelColor.Transparent.argb }

        assert(lit > 0)
        tester.dispose()
    }
}
