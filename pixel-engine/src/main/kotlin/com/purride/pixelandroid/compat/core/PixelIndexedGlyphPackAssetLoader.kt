package com.purride.pixelcore

import android.content.Context
import com.purride.pixelcore.internal.PixelCoreArtifactAccess
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction

/**
 * Android assets 的 indexed glyph pack 加载器。
 *
 * `glyphs.bin` 未压缩时优先 mmap；其他打包方式自动退回有界流读取。同步入口只能由后台线程调用。
 */
public class PixelIndexedGlyphPackAssetLoader @JvmOverloads public constructor(
    /** 仅提取 application assets 的 Android Context。 */
    context: Context,
    /** 与其他资源共享或由 Launcher 专用配置的有界缓存。 */
    private val cache: PixelResourceCache = PixelResourceCache(),
) {
    /** 只持有调用方精确资源包的 AssetManager，不保留 Context。 */
    private val assets = context.assets

    /** 不启用 checksum 时加载一个 indexed pack。 */
    public fun load(assetDirectory: String): PixelIndexedGlyphPack = load(
        assetDirectory = assetDirectory,
        manifestSha256 = null,
        binarySha256 = null,
    )

    /** 校验可选摘要并优先 mmap 加载 indexed pack。 */
    public fun load(
        assetDirectory: String,
        manifestSha256: String?,
        binarySha256: String?,
    ): PixelIndexedGlyphPack {
        requireSafeGlyphDirectory(assetDirectory)
        val cacheKey = "$assetDirectory#${manifestSha256 ?: "-"}#${binarySha256 ?: "-"}"
        return cache.getIndexedGlyphPack(cacheKey) { loadUncached(assetDirectory, manifestSha256, binarySha256) }
    }

    /** 通过正式资源加载器异步读取，避免 UI 线程阻塞并复用 single-flight/失败缓存。 */
    public fun loadAsync(
        resourceLoader: PixelResourceLoader,
        assetDirectory: String,
        manifestSha256: String? = null,
        binarySha256: String? = null,
    ): PixelResourceLoadHandle<PixelIndexedGlyphPack> {
        requireSafeGlyphDirectory(assetDirectory)
        val cacheKey = "$assetDirectory#${manifestSha256 ?: "-"}#${binarySha256 ?: "-"}"
        return resourceLoader.loadIndexedGlyphPackAsync(cacheKey) {
            loadUncached(assetDirectory, manifestSha256, binarySha256)
        }
    }

    /** 从共享缓存释放同目录的 indexed 和 V1 表示。 */
    public fun remove(
        assetDirectory: String,
        manifestSha256: String? = null,
        binarySha256: String? = null,
    ) {
        requireSafeGlyphDirectory(assetDirectory)
        cache.remove("$assetDirectory#${manifestSha256 ?: "-"}#${binarySha256 ?: "-"}")
    }

    /** 有界读取并严格解码 manifest。 */
    private fun loadManifest(assetDirectory: String, expectedSha256: String?): PixelGlyphPackManifest {
        val bytes = assets.open("$assetDirectory/manifest.json").use { input ->
            PixelCoreArtifactAccess.readBoundedBytes(
                input = input,
                maxBytes = PixelCoreArtifactAccess.maxJsonChars * 4,
                label = "indexed glyph manifest",
            )
        }
        val json = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
        return PixelGlyphPackParser.parseManifest(json, expectedSha256)
    }

    /** 不经过第二层缓存读取一个 pack，供 [loadAsync] 的 single-flight loader 使用。 */
    private fun loadUncached(
        assetDirectory: String,
        manifestSha256: String?,
        binarySha256: String?,
    ): PixelIndexedGlyphPack {
        val manifest = loadManifest(assetDirectory, manifestSha256)
        val binaryPath = "$assetDirectory/glyphs.bin"
        val mapped = mapAssetOrNull(binaryPath)
        return if (mapped != null) {
            PixelIndexedGlyphPackParser.parseBinary(manifest, mapped, binarySha256)
        } else {
            assets.open(binaryPath).use { input ->
                PixelIndexedGlyphPackParser.parseBinary(manifest, input, binarySha256)
            }
        }
    }

    /** 尝试 mmap 未压缩 asset；压缩 asset 或设备异常返回 null 走安全流路径。 */
    private fun mapAssetOrNull(path: String): ByteBuffer? {
        return try {
            assets.openFd(path).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.length,
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
