package com.purride.pixelcore

import android.content.Context

/**
 * Android assets 字形包加载器。
 *
 * `pixel-core` 已经有纯 Kotlin 的字形包解析器，这一层只负责把某个 assets 目录里的
 * `manifest.json` 和 `glyphs.bin` 读出来，再交给解析器。
 */
class PixelGlyphPackAssetLoader(
    private val context: Context,
) {

    private val cache = mutableMapOf<String, PixelGlyphPack>()

    fun load(assetDirectory: String): PixelGlyphPack {
        return cache.getOrPut(assetDirectory) {
            val manifest = context.assets.open("$assetDirectory/manifest.json")
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> PixelGlyphPackParser.parseManifest(reader.readText()) }

            context.assets.open("$assetDirectory/glyphs.bin").use { input ->
                PixelGlyphPackParser.parseBinary(manifest, input)
            }
        }
    }
}
