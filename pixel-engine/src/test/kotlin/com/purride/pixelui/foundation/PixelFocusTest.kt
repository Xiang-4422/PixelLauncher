package com.purride.pixelui.foundation

import com.purride.pixelui.Column
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.FocusScopeNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelFocusManager
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFocusTest {
    @Test
    fun autofocusAndTabTraversalMovePrimaryFocus() {
        val first = FocusNode("first")
        val second = FocusNode("second")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    node = FocusScopeNode(),
                    child = Column(
                        children = listOf(
                            Focus(node = first, autofocus = true, child = Text("FIRST")),
                            Focus(node = second, child = Text("SECOND")),
                        ),
                    ),
                ),
                logicalWidth = 60,
                logicalHeight = 24,
            )

            assertTrue(first.isFocused)
            assertFalse(second.isFocused)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertFalse(first.isFocused)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun focusedNodeHandlesEnterKeyBeforeTraversal() {
        val node = FocusNode("button")
        var handled = 0
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Focus(
                    node = node,
                    autofocus = true,
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.ENTER) {
                            handled += 1
                            true
                        } else {
                            false
                        }
                    },
                    child = OutlinedButton(text = "OK", onPressed = { }),
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals(1, handled)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun disabledFocusNodeIsSkipped() {
        val first = FocusNode("first")
        val disabled = FocusNode("disabled", canRequestFocus = false)
        val third = FocusNode("third")
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    child = Column(
                        children = listOf(
                            Focus(node = first, autofocus = true, child = Text("FIRST")),
                            Focus(node = disabled, canRequestFocus = false, child = Text("DISABLED")),
                            Focus(node = third, child = Text("THIRD")),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 36,
            )

            assertTrue(PixelFocusManager.dispatchKeyEvent(com.purride.pixelui.PixelKeyEvent(PixelKey.TAB)))
            assertTrue(third.isFocused)
            assertFalse(disabled.isFocused)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun textInputNextActionTraversesFocusScope() {
        val first = FocusNode("field")
        val second = FocusNode("next")
        val controller = PixelTextFieldController()
        val state = controller.create()
        var submitted = ""
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                FocusScope(
                    child = Column(
                        children = listOf(
                            Focus(
                                node = first,
                                autofocus = true,
                                child = TextField(
                                    state = state,
                                    controller = controller,
                                    placeholder = "NAME",
                                    textInputAction = PixelTextInputAction.NEXT,
                                    onSubmitted = { submitted = it },
                                ),
                            ),
                            Focus(node = second, child = Text("NEXT")),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 28,
            )

            tester.enterText(find.byText("NAME"), "Ada")
            tester.submitTextInput()

            assertEquals("Ada", submitted)
            assertTrue(second.isFocused)
        } finally {
            tester.dispose()
        }
    }
}
