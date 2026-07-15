package com.purride.pixelcore

import android.content.Context
import com.purride.pixelcore.internal.PixelCoreArtifactAccess
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Android assets 字形包加载器。
 *
 * 该同步入口会使用 [PixelResourceCache] 的 glyph 字节预算和并发单飞；UI 线程需要通过
 * [PixelResourceLoader.loadGlyphPackAsync] 调用本加载器。
 */
public class PixelGlyphPackAssetLoader @JvmOverloads public constructor(
    /** 用于读取 assets 的 Android Context。 */
    context: Context,
    /** 可由宿主共享的有界资源缓存。 */
    private val cache: PixelResourceCache = PixelResourceCache(),
) {
    /** 只持有 application assets，避免意外保留 Activity。 */
    private val assets = context.applicationContext.assets

    /** 使用有界读取和默认无 checksum 模式加载字形包。 */
    public fun load(assetDirectory: String): PixelGlyphPack = load(
        assetDirectory = assetDirectory,
        manifestSha256 = null,
        binarySha256 = null,
    )

    /** 校验 manifest/binary SHA-256 后加载字形包。 */
    public fun load(
        assetDirectory: String,
        manifestSha256: String?,
        binarySha256: String?,
    ): PixelGlyphPack {
        requireSafeGlyphDirectory(assetDirectory)
        /** checksum 也进入 key，防止不同完整性要求错误共享旧结果。 */
        val cacheKey = buildString {
            append(assetDirectory)
            append('#')
            append(manifestSha256 ?: "-")
            append('#')
            append(binarySha256 ?: "-")
        }
        return cache.getGlyphPack(cacheKey) {
            /** 有界读取的 manifest 原始 UTF-8 字节。 */
            val manifestBytes = assets.open("$assetDirectory/manifest.json").use { input ->
                PixelCoreArtifactAccess.readBoundedBytes(
                    input = input,
                    maxBytes = PixelCoreArtifactAccess.maxJsonChars * 4,
                    label = "glyph manifest",
                )
            }
            /** 拒绝 malformed UTF-8，而不是静默插入 replacement 字符。 */
            val manifestJson = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(manifestBytes))
                .toString()
            /** 已校验结构与可选摘要的 manifest。 */
            val manifest = PixelGlyphPackParser.parseManifest(manifestJson, manifestSha256)
            assets.open("$assetDirectory/glyphs.bin").use { input ->
                PixelGlyphPackParser.parseBinary(manifest, input, binarySha256)
            }
        }
    }

    /** 释放本加载器共享缓存中的指定目录和 checksum 组合。 */
    public fun remove(
        assetDirectory: String,
        manifestSha256: String? = null,
        binarySha256: String? = null,
    ) {
        requireSafeGlyphDirectory(assetDirectory)
        /** 与 [load] 完全一致的缓存 key。 */
        val cacheKey = "$assetDirectory#${manifestSha256 ?: "-"}#${binarySha256 ?: "-"}"
        cache.remove(cacheKey)
    }
}

/** 校验字形包 assets 相对目录。 */
private fun requireSafeGlyphDirectory(assetDirectory: String) {
    require(assetDirectory.isNotBlank()) { "assetDirectory must not be blank" }
    require(assetDirectory.length <= 1_024) { "assetDirectory exceeds 1024 chars" }
    require(!assetDirectory.startsWith('/') && !assetDirectory.startsWith('\\')) {
        "assetDirectory must be relative"
    }
    /** 使用统一分隔符拆分后的路径段。 */
    val segments = assetDirectory.replace('\\', '/').split('/')
    require(segments.none { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
        "assetDirectory contains an unsafe path segment"
    }
}
