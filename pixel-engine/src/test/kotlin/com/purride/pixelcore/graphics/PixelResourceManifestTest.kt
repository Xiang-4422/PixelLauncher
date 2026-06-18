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
        val error = expectManifestError("""{"version": 3}""")

        assertTrue(error.message.orEmpty().contains("version 3"))
    }

    @Test
    fun parseReportsMissingRequiredFields() {
        val error = expectManifestError("""{"bitmaps":[{"id":"icon"}]}""")

        assertTrue(error.message.orEmpty().contains("Missing string field 'path'"))
    }

    @Test
    fun parseCatalogReadsColorsFontsAndBaseResources() {
        val catalog = PixelResourceManifestJsonLoader.parseCatalog(
            """
            {
              "version": 2,
              "bitmaps": [
                { "id": "icons", "path": "images/icons.png" }
              ],
              "colors": [
                { "id": "accent", "value": "#22AAFF" },
                { "id": "overlay", "value": "#80224466" }
              ],
              "fonts": [
                {
                  "id": "ui8",
                  "manifest": "glyphpacks/ui8/manifest.json",
                  "binary": "glyphpacks/ui8/glyphs.bin"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(2, catalog.resources.version)
        assertEquals("images/icons.png", catalog.resources.bitmaps.single().path)
        assertEquals(PixelColor.fromRgb(0x22, 0xAA, 0xFF), catalog.colors[0].color)
        assertEquals(PixelColor.fromArgb(0x80, 0x22, 0x44, 0x66), catalog.colors[1].color)
        assertEquals("ui8", catalog.fonts.single().id)
        assertEquals("glyphpacks/ui8/manifest.json", catalog.fonts.single().manifestPath)
        assertEquals("glyphpacks/ui8/glyphs.bin", catalog.fonts.single().binaryPath)
    }

    @Test
    fun parseCatalogRequiresVersionTwoForColorsAndFonts() {
        val error = expectCatalogError(
            """{"version":1,"colors":[{"id":"accent","value":"#FFFFFF"}]}""",
        )

        assertTrue(error.message.orEmpty().contains("version 2"))
    }

    @Test
    fun parseCatalogRejectsDuplicateIdsAcrossResourceTypes() {
        val error = expectCatalogError(
            """
            {
              "version": 2,
              "bitmaps": [{ "id": "shared", "path": "image.png" }],
              "fonts": [{ "id": "shared", "manifest": "font.json", "binary": "font.bin" }]
            }
            """.trimIndent(),
        )

        assertTrue(error.message.orEmpty().contains("duplicate resource id 'shared'"))
    }

    @Test
    fun parseCatalogRejectsInvalidColorEncoding() {
        val error = expectCatalogError(
            """{"version":2,"colors":[{"id":"accent","value":"#1234"}]}""",
        )

        assertTrue(error.message.orEmpty().contains("#RRGGBB or #AARRGGBB"))
    }

    private fun expectManifestError(json: String): PixelResourceManifestLoadException {
        return try {
            PixelResourceManifestJsonLoader.parse(json)
            error("manifest should fail")
        } catch (error: PixelResourceManifestLoadException) {
            error
        }
    }

    private fun expectCatalogError(json: String): PixelResourceManifestLoadException {
        return try {
            PixelResourceManifestJsonLoader.parseCatalog(json)
            error("resource catalog should fail")
        } catch (error: PixelResourceManifestLoadException) {
            error
        }
    }
}
