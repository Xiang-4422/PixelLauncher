package com.purride.pixelshowcase

import java.io.ByteArrayOutputStream

/**
 * GIF89a 编码器：点阵画面的天生归宿——调色板本来就只有几十种颜色。
 * 纯 Kotlin 实现全局调色板收集、必要时的位深量化与 LZW 压缩。
 */
object GifEncoder {

    /**
     * 把 ARGB 帧序列编码为无限循环的 GIF。
     *
     * @param frames 每帧一个 ARGB IntArray（长度 = width*height），至少一帧。
     * @param delayCentis 帧间隔，单位 1/100 秒。
     */
    fun encode(width: Int, height: Int, frames: List<IntArray>, delayCentis: Int): ByteArray {
        require(frames.isNotEmpty()) { "至少需要一帧" }
        require(frames.all { it.size == width * height }) { "帧尺寸与画布不符" }

        val (palette, indexedFrames) = buildPalette(frames)
        val tableBits = colorTableBits(palette.size)
        val out = ByteArrayOutputStream()

        out.writeAscii("GIF89a")
        // Logical Screen Descriptor：全局调色板，大小 2^tableBits。
        out.writeShortLe(width)
        out.writeShortLe(height)
        out.write(0x80 or ((tableBits - 1) shl 4) or (tableBits - 1))
        out.write(0)
        out.write(0)
        // Global Color Table：不足处补零。
        val tableSize = 1 shl tableBits
        for (index in 0 until tableSize) {
            val rgb = palette.getOrElse(index) { 0 }
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }
        // NETSCAPE2.0 扩展：无限循环。
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.writeAscii("NETSCAPE2.0")
        out.write(3)
        out.write(1)
        out.writeShortLe(0)
        out.write(0)

        indexedFrames.forEach { indexed ->
            // Graphics Control Extension：帧延时，不透明。
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(0x04)
            out.writeShortLe(delayCentis)
            out.write(0)
            out.write(0)
            // Image Descriptor：整幅、无局部调色板。
            out.write(0x2C)
            out.writeShortLe(0)
            out.writeShortLe(0)
            out.writeShortLe(width)
            out.writeShortLe(height)
            out.write(0)
            writeLzw(out, indexed, tableBits)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    /**
     * 收集全局调色板并把所有帧转成索引。颜色数超过 256 时按位深
     * 逐级量化（每通道 6bit → 5bit → 4bit）直到装得下。
     */
    private fun buildPalette(frames: List<IntArray>): Pair<List<Int>, List<ByteArray>> {
        var quantizeBits = 8
        while (true) {
            val paletteIndex = LinkedHashMap<Int, Int>()
            val indexedFrames = ArrayList<ByteArray>(frames.size)
            var overflow = false
            for (frame in frames) {
                val indexed = ByteArray(frame.size)
                for (i in frame.indices) {
                    val rgb = quantize(frame[i] and 0xFFFFFF, quantizeBits)
                    val existing = paletteIndex[rgb]
                    val index = if (existing != null) {
                        existing
                    } else {
                        if (paletteIndex.size >= 256) {
                            overflow = true
                            break
                        }
                        val next = paletteIndex.size
                        paletteIndex[rgb] = next
                        next
                    }
                    indexed[i] = index.toByte()
                }
                if (overflow) break
                indexedFrames += indexed
            }
            if (!overflow) return paletteIndex.keys.toList() to indexedFrames
            quantizeBits -= 1
            require(quantizeBits >= 3) { "调色板无法量化到 256 色以内" }
        }
    }

    /** 每通道保留高 bits 位（并把最高段拉满，避免整体变暗）。 */
    private fun quantize(rgb: Int, bits: Int): Int {
        if (bits >= 8) return rgb
        val mask = (0xFF shl (8 - bits)) and 0xFF
        val r = rgb shr 16 and mask
        val g = rgb shr 8 and mask
        val b = rgb and mask
        return (r shl 16) or (g shl 8) or b
    }

    private fun colorTableBits(colorCount: Int): Int {
        var bits = 1
        while ((1 shl bits) < colorCount) bits++
        return bits.coerceIn(1, 8)
    }

    /** GIF 变长码 LZW：字典满 4096 发 clear 重置，输出打包成 ≤255 字节子块。 */
    private fun writeLzw(out: ByteArrayOutputStream, pixels: ByteArray, tableBits: Int) {
        val minCodeSize = tableBits.coerceAtLeast(2)
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val packer = BitPacker(out)

        var dictionary = HashMap<Int, Int>()
        var nextCode = endCode + 1
        var codeSize = minCodeSize + 1

        packer.write(clearCode, codeSize)
        var prefix = pixels[0].toInt() and 0xFF
        for (i in 1 until pixels.size) {
            val pixel = pixels[i].toInt() and 0xFF
            val key = (prefix shl 8) or pixel
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            packer.write(prefix, codeSize)
            if (nextCode < MAX_DICTIONARY) {
                dictionary[key] = nextCode
                if (nextCode == (1 shl codeSize)) codeSize++
                nextCode++
            } else {
                packer.write(clearCode, codeSize)
                dictionary = HashMap()
                nextCode = endCode + 1
                codeSize = minCodeSize + 1
            }
            prefix = pixel
        }
        packer.write(prefix, codeSize)
        packer.write(endCode, codeSize)
        packer.flush()
        out.write(0)
    }

    /** LSB-first 位打包器，满 255 字节吐一个 GIF 子块。 */
    private class BitPacker(private val out: ByteArrayOutputStream) {
        private var bitBuffer = 0
        private var bitCount = 0
        private val block = ByteArray(255)
        private var blockLength = 0

        fun write(code: Int, codeSize: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                appendByte(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        fun flush() {
            if (bitCount > 0) appendByte(bitBuffer and 0xFF)
            bitBuffer = 0
            bitCount = 0
            if (blockLength > 0) {
                out.write(blockLength)
                out.write(block, 0, blockLength)
                blockLength = 0
            }
        }

        private fun appendByte(value: Int) {
            block[blockLength++] = value.toByte()
            if (blockLength == 255) {
                out.write(255)
                out.write(block, 0, 255)
                blockLength = 0
            }
        }
    }

    private const val MAX_DICTIONARY = 4096

    private fun ByteArrayOutputStream.writeAscii(text: String) =
        text.forEach { write(it.code) }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
