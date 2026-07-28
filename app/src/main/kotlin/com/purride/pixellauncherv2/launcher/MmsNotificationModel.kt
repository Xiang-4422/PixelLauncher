package com.purride.pixellauncherv2.launcher

/**
 * 彩信到达通知（m-notification.ind，WSP 编码）的最小解析：只为提取发件人地址。
 *
 * 本 Launcher 不做彩信下载，但作为默认短信应用会独占接收 WAP_PUSH_DELIVER——
 * 至少要让用户知道是谁发来了彩信。头字段按 WSP 通用规则顺序跳过，
 * 命中 From（0x89）时解析其中的 encoded-string 地址。
 */
object MmsNotificationModel {

    /** 从 m-notification.ind PDU 提取发件人地址；无 From 头或解析失败返回 null。 */
    fun extractSender(pdu: ByteArray): String? {
        var index = 0
        while (index < pdu.size) {
            val field = pdu[index].toInt() and 0xFF
            if (field < 0x80) {
                // 头区已结束（或不是合法头字段），后续没有 From 可言。
                return null
            }
            index++
            if (field == FIELD_FROM) {
                return parseFromValue(pdu, index)
            }
            index = skipHeaderValue(pdu, index)
            if (index < 0) {
                return null
            }
        }
        return null
    }

    private fun parseFromValue(pdu: ByteArray, start: Int): String? {
        // From 值固定为 value-length 包裹：address-present-token + encoded-string，
        // 或 insert-address-token（地址由彩信中心事后补齐，PDU 里没有）。
        val valueLength = readValueLength(pdu, start) ?: return null
        var index = start + valueLength.bytesConsumed
        val end = (index + valueLength.length).coerceAtMost(pdu.size)
        if (index >= end) {
            return null
        }
        when (pdu[index].toInt() and 0xFF) {
            TOKEN_INSERT_ADDRESS -> return null
            TOKEN_ADDRESS_PRESENT -> index++
        }
        // encoded-string 可能带字符集前缀（短整数）等非文本字节，跳到首个可见字符。
        while (index < end && (pdu[index].toInt() and 0xFF) !in PRINTABLE_ASCII) {
            index++
        }
        var textEnd = index
        while (textEnd < end && pdu[textEnd].toInt() != 0) {
            textEnd++
        }
        if (textEnd <= index) {
            return null
        }
        val raw = String(pdu, index, textEnd - index, Charsets.UTF_8)
        // 地址通常带 "/TYPE=PLMN" 之类的后缀，展示时剥掉。
        return raw.substringBefore("/TYPE=").trim().ifBlank { null }
    }

    /** 按 WSP 通用规则跳过一个头字段值；返回新下标，异常返回 -1。 */
    private fun skipHeaderValue(pdu: ByteArray, start: Int): Int {
        if (start >= pdu.size) {
            return -1
        }
        val first = pdu[start].toInt() and 0xFF
        return when {
            // short-length：值总长 = first 个字节
            first <= 30 -> start + 1 + first
            // length-quote：后跟 uintvar 表示长度
            first == LENGTH_QUOTE -> {
                val uintvar = readUintvar(pdu, start + 1) ?: return -1
                start + 1 + uintvar.bytesConsumed + uintvar.length
            }
            // text-string：读到 NUL 结束符
            first < 0x80 -> {
                var index = start
                while (index < pdu.size && pdu[index].toInt() != 0) {
                    index++
                }
                index + 1
            }
            // 单字节短整数值
            else -> start + 1
        }
    }

    private fun readValueLength(pdu: ByteArray, start: Int): Segment? {
        if (start >= pdu.size) {
            return null
        }
        val first = pdu[start].toInt() and 0xFF
        return when {
            first <= 30 -> Segment(length = first, bytesConsumed = 1)
            first == LENGTH_QUOTE -> {
                val uintvar = readUintvar(pdu, start + 1) ?: return null
                Segment(length = uintvar.length, bytesConsumed = 1 + uintvar.bytesConsumed)
            }
            else -> null
        }
    }

    private fun readUintvar(pdu: ByteArray, start: Int): Segment? {
        var value = 0
        var index = start
        // uintvar 每字节 7 位有效，最高位为续接标记，最多 5 字节。
        repeat(5) {
            if (index >= pdu.size) {
                return null
            }
            val byte = pdu[index].toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F)
            index++
            if (byte and 0x80 == 0) {
                return Segment(length = value, bytesConsumed = index - start)
            }
        }
        return null
    }

    private data class Segment(val length: Int, val bytesConsumed: Int)

    private const val FIELD_FROM = 0x89
    private const val TOKEN_ADDRESS_PRESENT = 0x80
    private const val TOKEN_INSERT_ADDRESS = 0x81
    private const val LENGTH_QUOTE = 31
    private val PRINTABLE_ASCII = 0x20..0x7E
}
