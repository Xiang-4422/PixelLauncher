package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelBitmapAssetLoader
import com.purride.pixelcore.PixelBitmapResourceLoader
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelResourceCache
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelcore.PixelSpriteSheetJsonLoader
import com.purride.pixelui.Image
import com.purride.pixelui.Column
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Sprite
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.widgets.animated.AnimatedSprite
import com.purride.pixeldemo.R
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object AnimatedSpriteScene : DemoScene {
    override val id = "animated_sprite"
    override val title = "SPRITE"
    override val description = "Sprite sheet region + AnimatedSprite fps"

    override fun build(env: DemoEnv): Widget {
        val vsync = PixelTickerProvider(env.hostView.frameScheduler)
        val bitmap = SpriteAssets.runnerBitmap(env)
        val sheet = SpriteAssets.runner(env)
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
                Text("ASSET PNG", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                Image(bitmap),
                Text("RESOURCE PNG", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                Image(SpriteAssets.runnerResourceBitmap(env)),
                SizedBox(height = 2),
                Text("STATIC FRAMES", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                Row(
                    spacing = 3,
                    children = listOf(
                        Sprite(sheet, frameIndex = 0),
                        Sprite(sheet, frameIndex = 1),
                        Sprite(sheet, frameIndex = 2),
                    ),
                ),
                SizedBox(height = 2),
                Text("ANIM 4FPS", style = TextStyle(color = PixelColor.White)),
                AnimatedSprite(sheet = sheet, fps = 4, vsync = vsync, key = "runner"),
            ),
        )
    }
}

private object SpriteAssets {
    private val cache = PixelResourceCache()

    fun runner(env: DemoEnv): PixelSpriteSheet {
        return cache.getSpriteSheet("asset-runner-sheet") {
            val assets = env.hostView.context.assets
            val json = assets.open("pixel_demo/runner.json").bufferedReader().use { it.readText() }
            val definition = PixelSpriteSheetJsonLoader.parseDefinition(json)
            PixelSpriteSheetJsonLoader.load(
                json = json,
                bitmap = cache.getBitmap("asset:${definition.bitmap}") {
                    PixelBitmapAssetLoader(assets).load(definition.bitmap)
                },
            )
        }
    }

    fun runnerBitmap(env: DemoEnv) = cache.getBitmap("asset:pixel_demo/runner.png") {
        PixelBitmapAssetLoader(env.hostView.context.assets).load("pixel_demo/runner.png")
    }

    fun runnerResourceBitmap(env: DemoEnv) = cache.getBitmap("res:runner_resource") {
        PixelBitmapResourceLoader(env.hostView.context.resources).load(R.drawable.runner_resource)
    }
}
