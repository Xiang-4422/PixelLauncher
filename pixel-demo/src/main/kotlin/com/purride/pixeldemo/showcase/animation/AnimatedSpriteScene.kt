package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
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
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object AnimatedSpriteScene : DemoScene {
    override val id = "animated_sprite"
    override val title = "SPRITE"
    override val description = "Sprite sheet region + AnimatedSprite fps"

    override fun build(env: DemoEnv): Widget {
        val vsync = PixelTickerProvider(env.hostView.frameScheduler)
        val sheet = SpriteAssets.runner
        return Column(
            mainAxisAlignment = MainAxisAlignment.CENTER,
            spacing = 4,
            children = listOf(
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
    val runner: PixelSpriteSheet = PixelSpriteSheet(
        bitmap = PixelBitmap(
            width = 12,
            height = 4,
            pixels = intArrayOf(
                0x00000000, 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0x00000000, 0x00000000, 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0x00000000, 0x00000000, 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0x00000000,
                0xFFFFC040.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC040.toInt(), 0x00000000, 0x00000000, 0xFFFFC040.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC040.toInt(),
                0x00000000, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00000000, 0x00000000, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x00000000,
                0xFFFFC040.toInt(), 0x00000000, 0x00000000, 0xFFFFC040.toInt(), 0xFFFFC040.toInt(), 0x00000000, 0x00000000, 0xFFFFC040.toInt(), 0x00000000, 0xFFFFC040.toInt(), 0x00000000, 0xFFFFC040.toInt(),
            ),
        ),
        frames = listOf(
            PixelBitmapRegion(0, 0, 4, 4),
            PixelBitmapRegion(4, 0, 4, 4),
            PixelBitmapRegion(8, 0, 4, 4),
        ),
    )
}
