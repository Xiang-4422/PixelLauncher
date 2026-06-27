package com.purride.pixelui.widgets.animated

import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class AnimatedVisibilityTest {
    @Test
    fun animatedVisibilitySwitchesBetweenChildAndReplacement() {
        val tester = PixelTester()

        tester.pumpWidget(
            widget = AnimatedVisibility(
                visible = false,
                duration = 1.milliseconds,
                vsync = tester.vsync,
                replacement = Text("OFF"),
                key = "visibility",
                child = Text("ON"),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )

        assertTrue(tester.exists(find.byText("OFF")))
        assertFalse(tester.exists(find.byText("ON")))

        tester.pumpWidget(
            widget = AnimatedVisibility(
                visible = true,
                duration = 1.milliseconds,
                vsync = tester.vsync,
                replacement = Text("OFF"),
                key = "visibility",
                child = Text("ON"),
            ),
            logicalWidth = 64,
            logicalHeight = 24,
        )
        tester.pumpFrame(1)
        tester.pumpFrame(1)

        assertTrue(tester.exists(find.byText("ON")))
        assertFalse(tester.exists(find.byText("OFF")))
        tester.dispose()
    }
}
