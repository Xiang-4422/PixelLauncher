package com.purride.pixelcore

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.util.Collections

/** 字形 manifest 或二进制包校验失败。 */
public class PixelGlyphPackLoadException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * 像素字形包清单。
 *
 * 这一层只描述“某个字形包里有哪些字、每个字占多高的单元格、默认前进宽度是多少”，
 * 不关心这些资源来自哪一个应用，也不关心具体由谁加载。
 */
public data class PixelGlyphPackManifest(
    /** 字形包稳定 id。 */
    val packId: String,
    /** 面向工具和调试界面的显示名称。 */
    val displayName: String,
    /** 每个压缩字形共享的正单元高度。 */
    val cellHeight: Int,
    /** 从单元顶部计算的 baseline。 */
    val baseline: Int,
    /** 未单独声明时使用的正 advance。 */
    val defaultAdvance: Int,
    /** 供工具展示和静态审计的 Unicode 范围。 */
    val supportedRanges: List<String>,
) {
    init {
        require(packId.isNotBlank() && packId.length <= 256) { "packId must contain 1..256 chars" }
        require(displayName.isNotBlank() && displayName.length <= 1024) {
            "displayName must contain 1..1024 chars"
        }
        require(cellHeight in 1..PixelResourceSafetyLimits.MaxDimension) {
            "cellHeight must be within 1..${PixelResourceSafetyLimits.MaxDimension}"
        }
        require(baseline in 0 until cellHeight) { "baseline must be within the glyph cell" }
        require(defaultAdvance in 1..PixelResourceSafetyLimits.MaxDimension) {
            "defaultAdvance must be within 1..${PixelResourceSafetyLimits.MaxDimension}"
        }
        require(supportedRanges.size <= PixelResourceSafetyLimits.MaxEntries) {
            "supportedRanges count ${supportedRanges.size} exceeds ${PixelResourceSafetyLimits.MaxEntries}"
        }
        supportedRanges.forEachIndexed { index, range -> requireUnicodeRange(range, index) }
    }
}

/**
 * 压缩字形记录。
 *
 * `packedPixels` 采用按位压缩格式；构造和读取均执行 defensive copy，调用方无法修改缓存中的字形。
 */
public class PackedGlyphRecord(
    /** Unicode scalar 值。 */
    public val codePoint: Int,
    /** 排版时使用的正前进宽度。 */
    public val advanceWidth: Int,
    /** 压缩位图的正宽度。 */
    public val width: Int,
    packedPixels: ByteArray,
) {
    /** 只在引擎内部读取的不可变压缩字节副本。 */
    internal val packedPixelsUnsafe: ByteArray = packedPixels.copyOf()

    /** 返回压缩字节副本，避免消费者修改内部资源。 */
    public val packedPixels: ByteArray
        get() = packedPixelsUnsafe.copyOf()

    init {
        require(Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF) {
            "codePoint U+${codePoint.toString(16)} is not a Unicode scalar"
        }
        require(advanceWidth in 1..PixelResourceSafetyLimits.MaxDimension) {
            "advanceWidth must be within 1..${PixelResourceSafetyLimits.MaxDimension}"
        }
        require(width in 1..PixelResourceSafetyLimits.MaxDimension) {
            "width must be within 1..${PixelResourceSafetyLimits.MaxDimension}"
        }
        require(packedPixelsUnsafe.isNotEmpty()) { "packedPixels must not be empty" }
    }

    /** 保留原 data class 的第一个解构槽位。 */
    public operator fun component1(): Int = codePoint

    /** 保留原 data class 的第二个解构槽位。 */
    public operator fun component2(): Int = advanceWidth

    /** 保留原 data class 的第三个解构槽位。 */
    public operator fun component3(): Int = width

    /** 保留原 data class 的第四个解构槽位，并返回 defensive copy。 */
    public operator fun component4(): ByteArray = packedPixels

    /** 保留原 data class 的 copy 契约，同时复制输入字节。 */
    public fun copy(
        codePoint: Int = this.codePoint,
        advanceWidth: Int = this.advanceWidth,
        width: Int = this.width,
        packedPixels: ByteArray = this.packedPixels,
    ): PackedGlyphRecord = PackedGlyphRecord(codePoint, advanceWidth, width, packedPixels)

    /** 按字节内容而非数组引用比较记录。 */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PackedGlyphRecord) return false
        return codePoint == other.codePoint &&
            advanceWidth == other.advanceWidth &&
            width == other.width &&
            packedPixelsUnsafe.contentEquals(other.packedPixelsUnsafe)
    }

    /** 与 [equals] 一致地计算数组内容哈希。 */
    override fun hashCode(): Int {
        /** 基于标量和尺寸的累计哈希。 */
        var result = codePoint
        result = 31 * result + advanceWidth
        result = 31 * result + width
        result = 31 * result + packedPixelsUnsafe.contentHashCode()
        return result
    }

    /** 生成不展开二进制内容的稳定诊断文本。 */
    override fun toString(): String {
        return "PackedGlyphRecord(codePoint=$codePoint, advanceWidth=$advanceWidth, " +
            "width=$width, packedPixelsSize=${packedPixelsUnsafe.size})"
    }
}

/** 完整字形包。 */
public data class PixelGlyphPack(
    /** 与二进制头一致的 manifest。 */
    val manifest: PixelGlyphPackManifest,
    /** 以 Unicode scalar 为 key 的只读字形映射。 */
    val glyphs: Map<Int, PackedGlyphRecord>,
) {
    init {
        require(glyphs.size <= PixelResourceSafetyLimits.MaxGlyphCount) {
            "glyph count ${glyphs.size} exceeds ${PixelResourceSafetyLimits.MaxGlyphCount}"
        }
        require(glyphs.all { (codePoint, record) -> codePoint == record.codePoint }) {
            "glyph map key must equal record codePoint"
        }
    }
}

/**
 * 像素字形包解析器。
 *
 * 解析器会先有界读取完整输入并校验 SHA-256，再按照 magic/version/count/dimension/length
 * 不变量逐条构建对象；截断、重复 code point 和尾随数据都会失败。
 */
public object PixelGlyphPackParser {
    /** PGLY 文件头。 */
    private const val Magic: Int = 0x50474C59
    /** 当前支持的 glyph 二进制版本。 */
    private const val Version: Int = 1

    /** 从 manifest.json 文本解析字形包元数据。 */
    public fun parseManifest(json: String): PixelGlyphPackManifest =
        parseManifest(json, expectedSha256 = null)

    /** 校验 manifest SHA-256 后解析字形包元数据。 */
    public fun parseManifest(json: String, expectedSha256: String?): PixelGlyphPackManifest {
        return wrapGlyphError("glyph manifest") {
            /** manifest 原始 UTF-8 字节。 */
            val bytes = json.toByteArray(Charsets.UTF_8)
            bytes.requireSha256(expectedSha256, "glyph manifest")
            /** 经过结构、深度和重复 key 校验的根对象。 */
            val root = PixelBoundedJson.parseObject(
                source = json,
                limits = PixelJsonLimits(maxInputChars = PixelResourceSafetyLimits.MaxJsonChars),
            )
            PixelGlyphPackManifest(
                packId = root.requireString("packId"),
                displayName = root.requireString("displayName"),
                cellHeight = root.requireInt("cellHeight"),
                baseline = root.requireInt("baseline"),
                defaultAdvance = root.requireInt("defaultAdvance"),
                supportedRanges = root.requireArray("supportedRanges")
                    .requireStrings("supportedRanges")
                    .toList(),
            )
        }
    }

    /** 从 glyphs.bin 二进制流解析字形记录，并校验 manifest 高度。 */
    public fun parseBinary(manifest: PixelGlyphPackManifest, inputStream: InputStream): PixelGlyphPack =
        parseBinary(manifest, inputStream, expectedSha256 = null)

    /** 有界读取并校验 SHA-256 后解析 glyphs.bin。 */
    public fun parseBinary(
        manifest: PixelGlyphPackManifest,
        inputStream: InputStream,
        expectedSha256: String?,
    ): PixelGlyphPack {
        return wrapGlyphError("glyph binary") {
            /** 在固定上限内读取的完整二进制。 */
            val bytes = inputStream.use { input ->
                input.readBoundedBytes(
                    maxBytes = PixelResourceSafetyLimits.MaxGlyphBinaryBytes,
                    label = "glyph binary",
                )
            }
            bytes.requireSha256(expectedSha256, "glyph binary")
            parseBinaryBytes(manifest, bytes)
        }
    }

    /** 解析已经有界读取的 glyph 二进制。 */
    private fun parseBinaryBytes(
        manifest: PixelGlyphPackManifest,
        bytes: ByteArray,
    ): PixelGlyphPack {
        /** 用于检查尾随数据的内存输入流。 */
        val byteInput = ByteArrayInputStream(bytes)
        /** 使用大端协议读取固定宽度字段。 */
        val dataInput = DataInputStream(byteInput)
        /** 文件 magic。 */
        val magic = dataInput.readInt()
        require(magic == Magic) { "Unexpected glyph pack magic: $magic" }
        /** 文件协议版本。 */
        val version = dataInput.readInt()
        require(version == Version) { "Unsupported glyph pack version: $version" }
        /** 二进制声明的统一单元高度。 */
        val cellHeight = dataInput.readInt()
        require(cellHeight == manifest.cellHeight) {
            "Manifest cellHeight ${manifest.cellHeight} does not match binary $cellHeight"
        }
        /** 文件声明的字形数量。 */
        val glyphCount = dataInput.readInt()
        require(glyphCount in 0..PixelResourceSafetyLimits.MaxGlyphCount) {
            "glyph count $glyphCount exceeds ${PixelResourceSafetyLimits.MaxGlyphCount}"
        }
        /** 只按已验证数量预分配的字形映射。 */
        val glyphs = LinkedHashMap<Int, PackedGlyphRecord>(mapCapacity(glyphCount))
        repeat(glyphCount) { index ->
            /** 当前记录 Unicode scalar。 */
            val codePoint = dataInput.readInt()
            require(Character.isValidCodePoint(codePoint) && codePoint !in 0xD800..0xDFFF) {
                "glyph[$index] codePoint U+${codePoint.toString(16)} is invalid"
            }
            require(codePoint !in glyphs) { "duplicate glyph codePoint U+${codePoint.toString(16)}" }
            /** 当前记录 advance。 */
            val advanceWidth = dataInput.readInt()
            require(advanceWidth in 1..PixelResourceSafetyLimits.MaxDimension) {
                "glyph[$index] advanceWidth $advanceWidth is invalid"
            }
            /** 当前记录位图宽度。 */
            val width = dataInput.readInt()
            require(width in 1..PixelResourceSafetyLimits.MaxDimension) {
                "glyph[$index] width $width is invalid"
            }
            /** 以 Long 计算的未压缩 bit 数。 */
            val bitCount = width.toLong() * manifest.cellHeight.toLong()
            require(bitCount <= Int.MAX_VALUE.toLong() * 8L) {
                "glyph[$index] pixel count overflows packed length"
            }
            /** 协议要求的精确压缩字节数。 */
            val expectedLength = ((bitCount + 7L) / 8L).toInt()
            /** 文件声明的压缩字节数。 */
            val dataLength = dataInput.readInt()
            require(dataLength == expectedLength) {
                "glyph[$index] dataLength $dataLength does not match expected $expectedLength"
            }
            require(dataLength <= byteInput.available()) {
                "glyph[$index] is truncated: need=$dataLength remaining=${byteInput.available()}"
            }
            /** 只在长度验证后分配的压缩像素。 */
            val packedPixels = ByteArray(dataLength)
            dataInput.readFully(packedPixels)
            glyphs[codePoint] = PackedGlyphRecord(
                codePoint = codePoint,
                advanceWidth = advanceWidth,
                width = width,
                packedPixels = packedPixels,
            )
        }
        require(byteInput.available() == 0) { "glyph binary contains ${byteInput.available()} trailing bytes" }
        /** 防止调用方通过 Map 强转修改解析结果的只读映射。 */
        val readOnlyGlyphs = Collections.unmodifiableMap(LinkedHashMap(glyphs))
        return PixelGlyphPack(manifest = manifest, glyphs = readOnlyGlyphs)
    }

    /** 为 LinkedHashMap 计算不溢出的合理初始容量。 */
    private fun mapCapacity(entryCount: Int): Int {
        if (entryCount < 3) return entryCount + 1
        /** 以默认 0.75 load factor 反推的容量。 */
        val capacity = (entryCount.toLong() * 4L / 3L) + 1L
        return minOf(capacity, Int.MAX_VALUE.toLong()).toInt()
    }

    /** 把截断和其他实现异常统一包装为稳定的公开解析异常。 */
    private inline fun <T> wrapGlyphError(label: String, block: () -> T): T {
        return try {
            block()
        } catch (error: PixelGlyphPackLoadException) {
            throw error
        } catch (error: EOFException) {
            throw PixelGlyphPackLoadException("Failed to parse $label: truncated input", error)
        } catch (error: IllegalArgumentException) {
            throw PixelGlyphPackLoadException(error.message ?: "Invalid $label", error)
        } catch (error: Throwable) {
            throw PixelGlyphPackLoadException("Failed to parse $label: ${error.message}", error)
        }
    }
}

/** 校验 `XXXX-YYYY` Unicode scalar 范围。 */
private fun requireUnicodeRange(value: String, index: Int) {
    /** 允许 4 到 6 位十六进制端点的完整匹配。 */
    val match = Regex("([0-9A-Fa-f]{4,6})-([0-9A-Fa-f]{4,6})").matchEntire(value)
        ?: throw IllegalArgumentException("supportedRanges[$index] '$value' is invalid")
    /** 范围起点。 */
    val start = match.groupValues[1].toInt(16)
    /** 范围终点。 */
    val end = match.groupValues[2].toInt(16)
    require(start <= end) { "supportedRanges[$index] start exceeds end" }
    require(Character.isValidCodePoint(start) && Character.isValidCodePoint(end)) {
        "supportedRanges[$index] is outside Unicode"
    }
    require(start !in 0xD800..0xDFFF && end !in 0xD800..0xDFFF) {
        "supportedRanges[$index] contains surrogate code points"
    }
}
