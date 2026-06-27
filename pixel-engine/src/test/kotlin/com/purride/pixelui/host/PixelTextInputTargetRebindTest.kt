package com.purride.pixelui

import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.host.findTextInputTargetForState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PixelTextInputTargetRebindTest {

    @Test
    fun focusedStateResolvesToTargetFromLatestRenderFrame() {
        val controller = PixelTextFieldController()
        val focusedState = controller.create()
        val previous = target(controller, focusedState, left = 0)
        val current = target(controller, focusedState, left = 8)

        assertSame(current, findTextInputTargetForState(listOf(previous, current), focusedState))
    }

    @Test
    fun missingFocusedStateDoesNotBindToAnotherTextField() {
        val controller = PixelTextFieldController()
        val focusedState = controller.create()
        val otherState = controller.create()

        assertNull(findTextInputTargetForState(listOf(target(controller, otherState, left = 0)), focusedState))
    }

    private fun target(
        controller: PixelTextFieldController,
        state: PixelTextFieldState,
        left: Int,
    ): PixelTextInputTarget = PixelTextInputTarget(
        bounds = PixelRect(left = left, top = 0, width = 8, height = 8),
        state = state,
        controller = controller,
        readOnly = false,
        autofocus = false,
        minLines = 1,
        maxLines = 1,
        inputType = PixelInputType.TEXT,
        action = PixelTextInputAction.DONE,
        onChanged = null,
        onSubmitted = null,
    )
}
