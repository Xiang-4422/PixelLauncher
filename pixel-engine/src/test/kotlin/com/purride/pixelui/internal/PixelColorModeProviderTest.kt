package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import org.junit.Assert.assertEquals
import org.junit.Test

class PixelColorModeProviderTest {

    @Test
    fun fallbackToMonoWhenProviderAbsent() {
        var capturedMode: PixelColorMode? = null
        val root = object : StatelessWidget() {
            override val key: Any? = null
            override fun build(context: BuildContext): Widget {
                capturedMode = context.colorMode()
                return Container(width = 1, height = 1)
            }
        }
        PixelUiRuntime().render(root = root, logicalWidth = 1, logicalHeight = 1)
        assertEquals(PixelColorMode.Mono, capturedMode)
    }

    @Test
    fun readsMonoModeFromProvider() {
        var capturedMode: PixelColorMode? = null
        val inner = object : StatelessWidget() {
            override val key: Any? = null
            override fun build(context: BuildContext): Widget {
                capturedMode = context.colorMode()
                return Container(width = 1, height = 1)
            }
        }
        val root = PixelColorModeProvider(
            colorMode = PixelColorMode.Mono,
            child = inner,
        )
        PixelUiRuntime().render(root = root, logicalWidth = 1, logicalHeight = 1)
        assertEquals(PixelColorMode.Mono, capturedMode)
    }

    @Test
    fun readsColorModeFromProvider() {
        var capturedMode: PixelColorMode? = null
        val inner = object : StatelessWidget() {
            override val key: Any? = null
            override fun build(context: BuildContext): Widget {
                capturedMode = context.colorMode()
                return Container(width = 1, height = 1)
            }
        }
        val root = PixelColorModeProvider(
            colorMode = PixelColorMode.Color,
            child = inner,
        )
        PixelUiRuntime().render(root = root, logicalWidth = 1, logicalHeight = 1)
        assertEquals(PixelColorMode.Color, capturedMode)
    }

    @Test
    fun maybeOfReturnsNullWhenAbsent() {
        var capturedMode: PixelColorMode? = PixelColorMode.Color
        val root = object : StatelessWidget() {
            override val key: Any? = null
            override fun build(context: BuildContext): Widget {
                capturedMode = PixelColorModeProvider.maybeOf(context)
                return Container(width = 1, height = 1)
            }
        }
        PixelUiRuntime().render(root = root, logicalWidth = 1, logicalHeight = 1)
        assertEquals(null, capturedMode)
    }
}
