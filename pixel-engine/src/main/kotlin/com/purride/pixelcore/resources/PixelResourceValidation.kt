package com.purride.pixelcore

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** 资源协议共享的大小、数量和维度安全上限。 */
internal object PixelResourceSafetyLimits {
    /** manifest、catalog 或 sprite JSON 的最大 UTF-8/UTF-16 近似字节数。 */
    const val MaxJsonChars: Int = 1_048_576
    /** 单个 bitmap 编码文件允许的最大字节数。 */
    const val MaxBitmapEncodedBytes: Int = 64 * 1024 * 1024
    /** 单个 glyph 二进制允许的最大字节数。 */
    const val MaxGlyphBinaryBytes: Int = 32 * 1024 * 1024
    /** 任一 catalog 或 atlas 允许的最大资源/帧数量。 */
    const val MaxEntries: Int = 16_384
    /** bitmap、glyph 或 sprite 单轴允许的最大像素尺寸。 */
    const val MaxDimension: Int = 16_384
    /** 单张解码 bitmap 允许的最大像素数量，约等于 256 MiB ARGB。 */
    const val MaxBitmapPixels: Long = 67_108_864L
    /** glyph pack 允许的最大字形数量。 */
    const val MaxGlyphCount: Int = 131_072
    /** metadata 允许的最大键值数量。 */
    const val MaxMetadataEntries: Int = 256
}

/** 在超过 [maxBytes] 前终止读取，避免先分配攻击者声明的大数组。 */
internal fun InputStream.readBoundedBytes(maxBytes: Int, label: String): ByteArray {
    require(maxBytes > 0) { "maxBytes must be > 0" }
    /** 每次从流读取的固定小缓冲。 */
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    /** 最终结果；初始容量受上限约束而不盲信 available()。 */
    val output = ByteArrayOutputStream(minOf(maxBytes, DEFAULT_BUFFER_SIZE))
    /** 当前累计读取字节数。 */
    var total = 0
    while (true) {
        /** 当前批次读取字节数。 */
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) {
            /** 对违反常见批量读取进度约定的流，退化为单字节读取并保证循环前进。 */
            val singleByte = read()
            if (singleByte < 0) break
            total += 1
            require(total <= maxBytes) { "$label exceeds byte limit $maxBytes" }
            output.write(singleByte)
            continue
        }
        total += count
        require(total <= maxBytes) { "$label exceeds byte limit $maxBytes" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** 计算小写十六进制 SHA-256。 */
internal fun ByteArray.sha256Hex(): String {
    /** 当前字节内容的 SHA-256 摘要。 */
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}

/** 校验可选 SHA-256 文本格式和实际内容。 */
internal fun ByteArray.requireSha256(expected: String?, label: String) {
    if (expected == null) return
    /** 统一成小写的期望摘要。 */
    val normalized = expected.lowercase()
    require(normalized.matches(Regex("[0-9a-f]{64}"))) {
        "$label SHA-256 must contain 64 hexadecimal characters"
    }
    /** 当前内容的实际摘要。 */
    val actual = sha256Hex()
    require(actual == normalized) { "$label SHA-256 mismatch: expected=$normalized actual=$actual" }
}

/** 以 Long 校验二维面积，避免 `width * height` 在 Int 中溢出。 */
internal fun checkedPixelArea(
    width: Int,
    height: Int,
    maxPixels: Long,
    label: String,
): Int {
    require(width in 1..PixelResourceSafetyLimits.MaxDimension) {
        "$label width $width is outside 1..${PixelResourceSafetyLimits.MaxDimension}"
    }
    require(height in 1..PixelResourceSafetyLimits.MaxDimension) {
        "$label height $height is outside 1..${PixelResourceSafetyLimits.MaxDimension}"
    }
    /** 使用 Long 计算的像素面积。 */
    val area = width.toLong() * height.toLong()
    require(area <= maxPixels) { "$label pixel count $area exceeds $maxPixels" }
    require(area <= Int.MAX_VALUE.toLong()) { "$label pixel count overflows Int" }
    return area.toInt()
}

/** 校验字符串是可选的规范 SHA-256。 */
internal fun requireOptionalSha256(value: String?, label: String): String? {
    if (value == null) return null
    /** 统一成小写的调用方摘要。 */
    val normalized = value.lowercase()
    require(normalized.matches(Regex("[0-9a-f]{64}"))) {
        "$label must contain 64 hexadecimal characters"
    }
    return normalized
}
