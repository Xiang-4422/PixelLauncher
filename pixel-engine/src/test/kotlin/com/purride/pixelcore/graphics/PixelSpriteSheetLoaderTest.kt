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
              "version": 1,
              "bitmap": "sprites/runner.png",
              "metadata": {
                "name": "runner",
                "fps": "8"
              },
              "frames": [
                {"left": 0, "top": 0, "width": 4, "height": 4},
                {"left": 4, "top": 0, "width": 4, "height": 4}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("sprites/runner.png", definition.bitmap)
        assertEquals(1, definition.version)
        assertEquals("runner", definition.metadata["name"])
        assertEquals("8", definition.metadata["fps"])
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

    @Test
    fun parseRejectsUnsupportedVersion() {
        try {
            PixelSpriteSheetJsonLoader.parseDefinition(
                """{"version":3,"bitmap":"x.png","frames":[{"left":0,"top":0,"width":1,"height":1}]}""",
            )
            error("unsupported version should fail")
        } catch (error: PixelSpriteSheetLoadException) {
            assertTrue(error.message.orEmpty().contains("version 3"))
        }
    }

    @Test
    fun parseAtlasDefinitionReadsTrimPivotAndScaleMetadata() {
        val definition = PixelSpriteSheetJsonLoader.parseAtlasDefinition(
            """
            {
              "version": 1,
              "bitmap": "sprites/runner.png",
              "scale": 2,
              "frames": [
                {
                  "left": 0, "top": 0, "width": 6, "height": 8,
                  "sourceWidth": 10, "sourceHeight": 12,
                  "trimLeft": 2, "trimTop": 3,
                  "pivotX": 5, "pivotY": 12
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(PixelSpriteSheetVersion, definition.version)
        assertEquals(2, definition.scale)
        assertEquals(PixelBitmapRegion(0, 0, 6, 8), definition.frames.single().region)
        assertEquals(10, definition.frames.single().sourceWidth)
        assertEquals(12, definition.frames.single().sourceHeight)
        assertEquals(2, definition.frames.single().trimLeft)
        assertEquals(3, definition.frames.single().trimTop)
        assertEquals(5, definition.frames.single().pivotX)
        assertEquals(12, definition.frames.single().pivotY)
    }

    @Test
    fun parseAtlasDefinitionDefaultsSourcePivotScaleAndVersionForMinimalFrames() {
        val definition = PixelSpriteSheetJsonLoader.parseAtlasDefinition(
            """
            {
              "bitmap": "sprites/runner.png",
              "frames": [
                {"left": 2, "top": 3, "width": 4, "height": 5}
              ]
            }
            """.trimIndent(),
        )
        val frame = definition.frames.single()

        assertEquals(PixelSpriteSheetVersion, definition.version)
        assertEquals(1, definition.scale)
        assertEquals(4, frame.sourceWidth)
        assertEquals(5, frame.sourceHeight)
        assertEquals(0, frame.trimLeft)
        assertEquals(0, frame.trimTop)
        assertEquals(0, frame.pivotX)
        assertEquals(0, frame.pivotY)
    }

    @Test
    fun loadAtlasRetainsMetadataWithoutChangingSheetRegions() {
        val bitmap = PixelBitmap(width = 8, height = 8, pixels = IntArray(64))
        val atlas = PixelSpriteSheetJsonLoader.loadAtlas(
            json =
                """
                {
                  "version": 1,
                  "bitmap": "runner.png",
                  "scale": 2,
                  "frames": [
                    {
                      "left": 1, "top": 2, "width": 4, "height": 3,
                      "sourceWidth": 8, "sourceHeight": 6,
                      "trimLeft": 2, "trimTop": 1,
                      "pivotX": 4, "pivotY": 5
                    }
                  ]
                }
                """.trimIndent(),
            bitmap = bitmap,
        )

        assertEquals(2, atlas.scale)
        assertEquals(listOf(PixelBitmapRegion(1, 2, 4, 3)), atlas.sheet.frames)
        assertEquals(8, atlas.frames.single().sourceWidth)
        assertEquals(4, atlas.frames.single().pivotX)
    }

    @Test
    fun parseAtlasRejectsTrimOutsideSourceBounds() {
        try {
            PixelSpriteSheetJsonLoader.parseAtlasDefinition(
                """
                {
                  "version": 1,
                  "bitmap": "runner.png",
                  "frames": [
                    {
                      "left": 0, "top": 0, "width": 4, "height": 4,
                      "sourceWidth": 5, "sourceHeight": 5,
                      "trimLeft": 2, "trimTop": 0
                    }
                  ]
                }
                """.trimIndent(),
            )
            error("out-of-bounds trim should fail")
        } catch (error: PixelSpriteSheetLoadException) {
            assertTrue(error.message.orEmpty().contains("sourceWidth"))
        }
    }

    @Test
    fun parseAtlasRejectsNonPositiveScale() {
        try {
            PixelSpriteSheetJsonLoader.parseAtlasDefinition(
                """{"version":1,"bitmap":"runner.png","scale":0,"frames":[{"left":0,"top":0,"width":1,"height":1}]}""",
            )
            error("non-positive scale should fail")
        } catch (error: PixelSpriteSheetLoadException) {
            assertTrue(error.message.orEmpty().contains("scale"))
        }
    }
}
