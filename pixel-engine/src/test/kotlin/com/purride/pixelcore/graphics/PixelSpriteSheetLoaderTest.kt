package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelSpriteSheetLoaderTest {
    @Test
    fun parseDefinitionReadsBitmapAndFrames() {
        val definition = PixelSpriteSheetJsonLoader.parseDefinition(
            """
            {
              "bitmap": "sprites/runner.png",
              "frames": [
                {"left": 0, "top": 0, "width": 4, "height": 4},
                {"left": 4, "top": 0, "width": 4, "height": 4}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("sprites/runner.png", definition.bitmap)
        assertEquals(2, definition.frames.size)
        assertEquals(PixelBitmapRegion(4, 0, 4, 4), definition.frames[1])
    }

    @Test
    fun loadRejectsFrameOutsideBitmap() {
        val bitmap = PixelBitmap(width = 4, height = 4, pixels = IntArray(16))

        try {
            PixelSpriteSheetJsonLoader.load(
                json = """{"bitmap":"tiny.png","frames":[{"left":2,"top":0,"width":4,"height":4}]}""",
                bitmap = bitmap,
            )
            error("loader should reject invalid region")
        } catch (error: PixelSpriteSheetLoadException) {
            assertTrue(error.message.orEmpty().contains("tiny.png"))
            assertTrue(error.message.orEmpty().contains("bitmap width"))
        }
    }

    @Test
    fun parseRejectsEmptyFrames() {
        try {
            PixelSpriteSheetJsonLoader.parseDefinition("""{"bitmap":"x.png","frames":[]}""")
            error("empty frames should fail")
        } catch (error: PixelSpriteSheetLoadException) {
            assertTrue(error.message.orEmpty().contains("frames"))
        }
    }
}
