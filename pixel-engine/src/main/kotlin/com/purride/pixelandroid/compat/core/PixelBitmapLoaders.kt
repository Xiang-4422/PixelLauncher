package com.purride.pixelcore

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.purride.pixelcore.internal.PixelCoreArtifactAccess

/** Android bitmap 资源解码失败。 */
public class PixelBitmapLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** bitmap 编码输入和解码尺寸的安全上限。 */
public data class PixelBitmapDecodeLimits(
    /** 单个编码文件允许的最大字节数。 */
    val maxEncodedBytes: Int = PixelCoreArtifactAccess.maxBitmapEncodedBytes,
    /** 解码后任一轴允许的最大像素尺寸。 */
    val maxDimension: Int = PixelCoreArtifactAccess.maxDimension,
    /** 解码后允许的最大像素数量。 */
    val maxPixels: Long = PixelCoreArtifactAccess.maxBitmapPixels,
) {
    init {
        require(maxEncodedBytes in 1..PixelCoreArtifactAccess.maxBitmapEncodedBytes) {
            "maxEncodedBytes must be within 1..${PixelCoreArtifactAccess.maxBitmapEncodedBytes}"
        }
        require(maxDimension in 1..PixelCoreArtifactAccess.maxDimension) {
            "maxDimension must be within 1..${PixelCoreArtifactAccess.maxDimension}"
        }
        require(maxPixels in 1..PixelCoreArtifactAccess.maxBitmapPixels) {
            "maxPixels must be within 1..${PixelCoreArtifactAccess.maxBitmapPixels}"
        }
    }
}

/** 从 Android assets 加载有 magic/长度/尺寸/checksum 校验的 bitmap。 */
public class PixelBitmapAssetLoader(
    /** 提供 asset 输入流的 Android 管理器。 */
    private val assets: AssetManager,
) {
    /** 使用默认上限读取指定 asset 路径。该同步方法应由后台资源加载器调用。 */
    public fun load(path: String): PixelBitmap = load(
        path = path,
        expectedSha256 = null,
        limits = PixelBitmapDecodeLimits(),
    )

    /** 校验 SHA-256 并使用指定上限读取 asset。 */
    public fun load(
        path: String,
        expectedSha256: String?,
        limits: PixelBitmapDecodeLimits = PixelBitmapDecodeLimits(),
    ): PixelBitmap {
        return wrapBitmapError("asset '$path'") {
            requireSafeAssetPath(path)
            /** 在最大编码长度内读取的原始文件。 */
            val bytes = assets.open(path).use { input ->
                PixelCoreArtifactAccess.readBoundedBytes(input, limits.maxEncodedBytes, "bitmap asset '$path'")
            }
            PixelCoreArtifactAccess.requireSha256(bytes, expectedSha256, "bitmap asset '$path'")
            decodeBitmapBytes(bytes = bytes, label = "asset '$path'", limits = limits)
        }
    }
}

/** 从 Android resource id 加载有 magic/长度/尺寸/checksum 校验的 bitmap。 */
public class PixelBitmapResourceLoader(
    /** 提供 raw resource 与密度感知解码的 Android Resources。 */
    private val resources: Resources,
) {
    /** 使用默认上限读取 drawable/mipmap resource id。该同步方法应由后台资源加载器调用。 */
    public fun load(resourceId: Int): PixelBitmap = load(
        resourceId = resourceId,
        expectedSha256 = null,
        limits = PixelBitmapDecodeLimits(),
    )

    /** 校验资源原始字节 SHA-256，并限制原始和密度换算后的尺寸。 */
    public fun load(
        resourceId: Int,
        expectedSha256: String?,
        limits: PixelBitmapDecodeLimits = PixelBitmapDecodeLimits(),
    ): PixelBitmap {
        return wrapBitmapError("resource '$resourceId'") {
            require(resourceId != 0) { "resourceId must not be 0" }
            /** 用于 checksum、magic 和原始尺寸预检的资源字节。 */
            val bytes = resources.openRawResource(resourceId).use { input ->
                PixelCoreArtifactAccess.readBoundedBytes(input, limits.maxEncodedBytes, "bitmap resource '$resourceId'")
            }
            PixelCoreArtifactAccess.requireSha256(bytes, expectedSha256, "bitmap resource '$resourceId'")
            validateBitmapBytes(bytes = bytes, label = "resource '$resourceId'", limits = limits)
            /** 保留 Android density 语义的正式解码结果。 */
            val bitmap = BitmapFactory.decodeResource(resources, resourceId)
                ?: throw PixelBitmapLoadException(
                    "Failed to decode bitmap resource '$resourceId': decoder returned null",
                )
            bitmap.toPixelBitmapAndRecycle(label = "resource '$resourceId'", limits = limits)
        }
    }
}

/** 校验编码 magic 与 bounds 后解码内存字节。 */
internal fun decodeBitmapBytes(
    bytes: ByteArray,
    label: String,
    limits: PixelBitmapDecodeLimits,
): PixelBitmap {
    validateBitmapBytes(bytes = bytes, label = label, limits = limits)
    /** 禁止 Android 根据设备 density 隐式缩放 asset。 */
    val options = BitmapFactory.Options().apply { inScaled = false }
    /** 正式像素解码结果。 */
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw PixelBitmapLoadException("Failed to decode bitmap $label: decoder returned null")
    return bitmap.toPixelBitmapAndRecycle(label = label, limits = limits)
}

/** 只解析编码头和尺寸，不分配像素 bitmap。 */
private fun validateBitmapBytes(
    bytes: ByteArray,
    label: String,
    limits: PixelBitmapDecodeLimits,
) {
    require(bytes.size <= limits.maxEncodedBytes) {
        "bitmap $label has ${bytes.size} bytes, limit=${limits.maxEncodedBytes}"
    }
    require(hasSupportedBitmapMagic(bytes)) {
        "bitmap $label has unsupported or corrupt magic"
    }
    /** 只读取 bounds 的解码选项。 */
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    validateBitmapDimensions(
        width = bounds.outWidth,
        height = bounds.outHeight,
        label = label,
        limits = limits,
    )
}

/** 把已解码 bitmap 转成不可变 PixelBitmap，并及时释放 Android native 像素。 */
private fun Bitmap.toPixelBitmapAndRecycle(
    label: String,
    limits: PixelBitmapDecodeLimits,
): PixelBitmap {
    return try {
        validateBitmapDimensions(width = width, height = height, label = label, limits = limits)
        PixelBitmap.fromAndroidBitmap(this)
    } finally {
        recycle()
    }
}

/** 使用调用方上限校验解码尺寸与乘法溢出。 */
private fun validateBitmapDimensions(
    width: Int,
    height: Int,
    label: String,
    limits: PixelBitmapDecodeLimits,
) {
    require(width in 1..limits.maxDimension) {
        "bitmap $label width $width is outside 1..${limits.maxDimension}"
    }
    require(height in 1..limits.maxDimension) {
        "bitmap $label height $height is outside 1..${limits.maxDimension}"
    }
    /** 以 Long 计算的像素面积。 */
    val pixels = width.toLong() * height.toLong()
    require(pixels <= limits.maxPixels) {
        "bitmap $label pixel count $pixels exceeds ${limits.maxPixels}"
    }
}

/** 识别 BitmapFactory 支持且 SDK 明确允许的编码头。 */
private fun hasSupportedBitmapMagic(bytes: ByteArray): Boolean {
    /** PNG 的八字节固定签名。 */
    val isPng = bytes.size >= 8 &&
        bytes[0].toInt() and 0xFF == 0x89 &&
        bytes.copyOfRange(1, 8).contentEquals(byteArrayOf(0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    /** JPEG 的 SOI 与首个 marker。 */
    val isJpeg = bytes.size >= 3 &&
        bytes[0].toInt() and 0xFF == 0xFF &&
        bytes[1].toInt() and 0xFF == 0xD8 &&
        bytes[2].toInt() and 0xFF == 0xFF
    /** GIF87a/GIF89a 签名。 */
    val isGif = bytes.size >= 6 &&
        (bytes.copyOfRange(0, 6).contentEquals("GIF87a".toByteArray(Charsets.US_ASCII)) ||
            bytes.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray(Charsets.US_ASCII)))
    /** RIFF 容器中的 WEBP 签名。 */
    val isWebp = bytes.size >= 12 &&
        bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII))
    /** BMP 的 BM 签名。 */
    val isBmp = bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()
    return isPng || isJpeg || isGif || isWebp || isBmp
}

/** 校验 asset 相对路径并拒绝父目录穿越。 */
private fun requireSafeAssetPath(path: String) {
    require(path.isNotBlank()) { "asset path must not be blank" }
    require(path.length <= 1024) { "asset path exceeds 1024 chars" }
    require(!path.startsWith('/') && !path.startsWith('\\')) { "asset path must be relative" }
    /** 使用统一分隔符拆分后的路径段。 */
    val segments = path.replace('\\', '/').split('/')
    require(segments.none { segment -> segment.isEmpty() || segment == "." || segment == ".." }) {
        "asset path contains an unsafe segment"
    }
}

/** 把底层异常统一包装为公开 bitmap 加载异常。 */
private inline fun <T> wrapBitmapError(label: String, block: () -> T): T {
    return try {
        block()
    } catch (error: PixelBitmapLoadException) {
        throw error
    } catch (error: Throwable) {
        throw PixelBitmapLoadException("Failed to decode bitmap $label: ${error.message}", error)
    }
}
