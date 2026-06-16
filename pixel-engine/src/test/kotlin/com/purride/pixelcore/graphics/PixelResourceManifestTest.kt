package com.purride.pixelcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelResourceManifestTest {
    @Test
    fun parseManifestReadsBitmapsSpriteSheetsAndMetadata() {
        val manifest = PixelResourceManifestJsonLoader.parse(
            """
            {
              "version": 1,
              "metadata": { "pack": "demo", "revision": "1" },
              "bitmaps": [
                { "id": "runner", "path": "sprites/runner.png" },
                { "id": "icons", "path": "icons/system.png" }
              ],
              "spriteSheets": [
                { "id": "runnerRun", "path": "sprites/runner.sheet.json", "bitmap": "runner" }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, manifest.version)
        assertEquals("demo", manifest.metadata["pack"])
        assertEquals(2, manifest.bitmaps.size)
        assertEquals(PixelBitmapResourceDefinition("runner", "sprites/runner.png"), manifest.bitmaps.first())
        assertEquals(1, manifest.spriteSheets.size)
        assertEquals(
            PixelSpriteSheetResourceDefinition(
                id = "runnerRun",
                path = "sprites/runner.sheet.json",
                bitmap = "runner",
            ),
            manifest.spriteSheets.first(),
        )
    }

    @Test
    fun parseRejectsDuplicateBitmapIds() {
        val error = expectManifestError(
            """
            {
              "bitmaps": [
                { "id": "icon", "path": "a.png" },
                { "id": "icon", "path": "b.png" }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("duplicate bitmap id 'icon'"))
    }

    @Test
    fun parseRejectsSpriteSheetWithMissingBitmapReference() {
        val error = expectManifestError(
            """
            {
              "bitmaps": [
                { "id": "runner", "path": "runner.png" }
              ],
              "spriteSheets": [
                { "id": "idle", "path": "idle.json", "bitmap": "missing" }
              ]
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("references missing bitmap 'missing'"))
    }

    @Test
    fun parseRejectsUnsupportedVersion() {
        val error = expectManifestError("""{"version": 2}""")

        assertTrue(error.message.orEmpty().contains("version 2"))
    }

    @Test
    fun parseReportsMissingRequiredFields() {
        val error = expectManifestError("""{"bitmaps":[{"id":"icon"}]}""")

        assertTrue(error.message.orEmpty().contains("Missing string field 'path'"))
    }

    private fun expectManifestError(json: String): PixelResourceManifestLoadException {
        return try {
            PixelResourceManifestJsonLoader.parse(json)
            error("manifest should fail")
        } catch (error: PixelResourceManifestLoadException) {
            error
        }
    }
}
