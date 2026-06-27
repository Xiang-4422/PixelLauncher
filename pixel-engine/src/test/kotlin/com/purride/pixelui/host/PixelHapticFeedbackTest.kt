package com.purride.pixelui

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.PixelUiRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelHapticFeedbackTest {
    @Test
    fun performUsesHostBridgeFromContext() {
        val bridge = RecordingHostBridge()
        val runtime = PixelUiRuntime()

        runtime.render(
            root = hostRoot(
                hostBridge = bridge,
                child = Builder { context ->
                    assertTrue(PixelHapticFeedback.perform(context, PixelHapticType.TAP))
                    SizedBox(width = 1, height = 1)
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(listOf(PixelHapticType.TAP), bridge.haptics)
        runtime.dispose()
    }

    @Test
    fun performReturnsFalseWithoutHostBridge() {
        val runtime = PixelUiRuntime()
        var handled = true

        runtime.render(
            root = hostRoot(
                child = Builder { context ->
                    handled = PixelHapticFeedback.perform(context, PixelHapticType.LONG_PRESS)
                    SizedBox(width = 1, height = 1)
                },
            ),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertFalse(handled)
        runtime.dispose()
    }

    private fun hostRoot(
        hostBridge: PixelHostBridge? = null,
        child: Widget,
    ): Widget {
        return HostRootWidget(
            screenProfile = ScreenProfile(logicalWidth = 4, logicalHeight = 4, dotSizePx = 8),
            textDirection = TextDirection.LTR,
            textRasterizer = PixelBitmapFont.Default,
            windowInsets = PixelWindowInsets.Zero,
            viewInsets = PixelWindowInsets.Zero,
            hostBridge = hostBridge,
            child = child,
        )
    }

    private class RecordingHostBridge : PixelHostBridge {
        val haptics = mutableListOf<PixelHapticType>()

        override fun showTextInput(request: PixelTextInputRequest): Unit = Unit

        override fun hideTextInput(): Unit = Unit

        override fun performHapticFeedback(type: PixelHapticType) {
            haptics += type
        }

        override fun requestFrame(): Unit = Unit

        override fun dispatchSystemAction(action: PixelSystemAction): Unit = Unit
    }
}
