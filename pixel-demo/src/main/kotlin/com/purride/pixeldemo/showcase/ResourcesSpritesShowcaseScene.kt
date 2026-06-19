package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelResourceCache
import com.purride.pixelcore.PixelResourceManifestJsonLoader
import com.purride.pixelcore.PixelSpriteAtlas
import com.purride.pixelcore.PixelSpriteAtlasDefinition
import com.purride.pixelcore.PixelSpriteFrameDefinition
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelcore.PixelSpriteSheetDefinition
import com.purride.pixelcore.PixelSpriteSheetJsonLoader
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Image
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Row
import com.purride.pixelui.Sprite
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.widgets.animated.AnimatedSprite
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object ResourcesSpritesShowcaseScene : DemoScene {
    override val id = "deep_resources_sprites"
    override val title = "资源与精灵"
    override val summary = "PixelBitmap、SpriteSheet、AnimatedSprite、Manifest 与 ResourceCache"
    override val category = DemoCatalog.paint
    override val tags = setOf("resource", "sprite", "bitmap", "manifest", "cache", "atlas")
    override val apis = setOf(
        "PixelBitmap",
        "PixelBitmapRegion",
        "PixelSpriteSheet",
        "PixelSpriteSheetDefinition",
        "PixelSpriteFrameDefinition",
        "PixelSpriteAtlasDefinition",
        "PixelSpriteAtlas",
        "PixelSpriteSheetJsonLoader",
        "PixelResourceManifestJsonLoader",
        "PixelResourceCache",
        "PixelResourceCacheSnapshot",
        "PixelResourceCatalog",
        "PixelColorResourceDefinition",
        "PixelFontResourceDefinition",
        "Sprite",
        "AnimatedSprite",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body(env))

    private fun body(env: DemoEnv): Widget {
        val bitmap = demoSpriteBitmap()
        val atlas = demoAtlas(bitmap)
        val sheet = atlas.sheet
        val cache = PixelResourceCache()
        cache.getBitmap("ship-bitmap") { bitmap }
        cache.getBitmap("ship-bitmap") { bitmap }
        cache.getSpriteSheet("ship-sheet") { sheet }
        cache.getSpriteSheet("ship-sheet") { sheet }
        val snapshot = cache.snapshot()
        val catalog = PixelResourceManifestJsonLoader.parseCatalog(resourceCatalogJson)
        val definition = PixelSpriteSheetJsonLoader.parseDefinition(spriteSheetJson)
        val sheetDefinition = PixelSpriteSheetDefinition(
            bitmap = definition.bitmap,
            frames = definition.frames,
            metadata = mapOf("source" to "inline"),
        )
        val atlasDefinition = PixelSpriteAtlasDefinition(
            bitmap = definition.bitmap,
            frames = atlas.frames,
            metadata = mapOf("scale" to atlas.scale.toString()),
        )

        return Column(
            children = listOf(
                sectionTitle("位图 / 精灵"),
                samplePanel(
                    title = "Image / Sprite / AnimatedSprite",
                    color = Cyan,
                    child = Row(
                        children = listOf(
                            framed(Image(bitmap = bitmap), Cyan),
                            framed(Sprite(sheet = sheet, frameIndex = 0), Green),
                            framed(Sprite(sheet = sheet, frameIndex = 1), Pink),
                            framed(AnimatedSprite(sheet = sheet, fps = 4, vsync = env.vsync), Accent),
                        ),
                        spacing = 4,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                    ),
                ),
                sectionTitle("资源模型"),
                samplePanel(
                    title = "Manifest / catalog / atlas",
                    color = Purple,
                    child = Column(
                        children = listOf(
                            metric("manifest", "v${catalog.resources.version} bitmaps=${catalog.resources.bitmaps.size} sheets=${catalog.resources.spriteSheets.size}"),
                            metric("catalog", "colors=${catalog.colors.size} fonts=${catalog.fonts.size}"),
                            metric("sheet", "${sheetDefinition.bitmap} frames=${sheetDefinition.frames.size}"),
                            metric("atlas", "${atlasDefinition.bitmap} frames=${atlasDefinition.frames.size} scale=${atlasDefinition.scale}"),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                samplePanel(
                    title = "PixelResourceCacheSnapshot",
                    color = Blue,
                    child = Column(
                        children = listOf(
                            metric("bitmaps", "${snapshot.bitmapCount} hits=${snapshot.bitmapHits} misses=${snapshot.bitmapMisses}"),
                            metric("sprites", "${snapshot.spriteSheetCount} hits=${snapshot.spriteSheetHits} misses=${snapshot.spriteSheetMisses}"),
                            metric("remove/clear", "${snapshot.removeCount}/${snapshot.clearCount}"),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    private fun framed(child: Widget, color: PixelColor): Widget =
        Container(
            padding = EdgeInsets.all(2),
            borderColor = color,
            child = child,
        )

    private fun metric(label: String, value: String): Widget =
        Row(
            children = listOf(
                Container(width = 48, child = Text(label, style = TextStyle(color = Muted))),
                Text(value, style = TextStyle(color = PixelColor.White)),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
        )
}

private val spriteSheetJson = """
    {
      "version": 2,
      "bitmap": "ship",
      "scale": 1,
      "metadata": { "pack": "demo" },
      "frames": [
        { "left": 0, "top": 0, "width": 8, "height": 8, "sourceWidth": 8, "sourceHeight": 8 },
        { "left": 8, "top": 0, "width": 8, "height": 8, "sourceWidth": 8, "sourceHeight": 8 }
      ]
    }
""".trimIndent()

private val resourceCatalogJson = """
    {
      "version": 2,
      "metadata": { "pack": "demo", "revision": "1" },
      "bitmaps": [{ "id": "ship", "path": "inline/ship.png" }],
      "spriteSheets": [{ "id": "ship_sheet", "path": "inline/ship.json", "bitmap": "ship" }],
      "colors": [{ "id": "accent", "value": "#FFB040" }],
      "fonts": [{ "id": "mono", "manifest": "fonts/mono.json", "binary": "fonts/mono.bin" }]
    }
""".trimIndent()

private fun demoAtlas(bitmap: PixelBitmap): PixelSpriteAtlas {
    return PixelSpriteSheetJsonLoader.loadAtlas(spriteSheetJson, bitmap)
}

private fun demoSpriteBitmap(): PixelBitmap {
    val width = 16
    val height = 8
    val clear = PixelColor.Transparent.argb
    val pixels = IntArray(width * height) { clear }
    fun set(x: Int, y: Int, color: PixelColor) {
        pixels[y * width + x] = color.argb
    }
    val frame0 = arrayOf(
        "..XX....",
        ".XXXX...",
        "XXXXXX..",
        ".XXXX...",
        "..XX....",
        "..XX....",
        ".X..X...",
        "X....X..",
    )
    val frame1 = arrayOf(
        "...XX...",
        "..XXXX..",
        ".XXXXXX.",
        "..XXXX..",
        "...XX...",
        "..X..X..",
        ".X....X.",
        "X......X",
    )
    frame0.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == 'X') set(x, y, if (y < 3) Accent else Cyan)
        }
    }
    frame1.forEachIndexed { y, row ->
        row.forEachIndexed { x, c ->
            if (c == 'X') set(x + 8, y, if (y < 3) Green else Pink)
        }
    }
    return PixelBitmap(width = width, height = height, pixels = pixels)
}
