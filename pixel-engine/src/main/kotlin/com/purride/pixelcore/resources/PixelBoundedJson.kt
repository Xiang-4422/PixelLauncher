package com.purride.pixelcore

/**
 * 资源 JSON 的统一安全上限。
 *
 * 该限制在创建集合和字符串前生效，避免不可信清单通过超深嵌套、超长字符串或超多条目耗尽内存。
 */
internal data class PixelJsonLimits(
    /** 单份 JSON 允许的最大 UTF-16 字符数。 */
    val maxInputChars: Int = 1_048_576,
    /** 对象和数组允许的最大嵌套深度。 */
    val maxDepth: Int = 32,
    /** 任一对象允许的最大成员数量。 */
    val maxObjectMembers: Int = 16_384,
    /** 任一数组允许的最大元素数量。 */
    val maxArrayItems: Int = 16_384,
    /** 单个字符串解码后的最大字符数量。 */
    val maxStringChars: Int = 16_384,
) {
    init {
        require(maxInputChars > 0) { "maxInputChars must be > 0" }
        require(maxDepth > 0) { "maxDepth must be > 0" }
        require(maxObjectMembers > 0) { "maxObjectMembers must be > 0" }
        require(maxArrayItems > 0) { "maxArrayItems must be > 0" }
        require(maxStringChars > 0) { "maxStringChars must be > 0" }
    }
}

/** 严格 JSON 解析后的内部值模型，不向 SDK 消费者暴露可变集合。 */
internal sealed interface PixelJsonValue

/** JSON 对象值；成员顺序只用于生成确定性的错误信息。 */
internal data class PixelJsonObject(
    /** 已拒绝重复 key 的对象成员。 */
    val members: Map<String, PixelJsonValue>,
) : PixelJsonValue

/** JSON 数组值。 */
internal data class PixelJsonArray(
    /** 保持源顺序的数组元素。 */
    val items: List<PixelJsonValue>,
) : PixelJsonValue

/** JSON 字符串值。 */
internal data class PixelJsonString(
    /** 完成转义解码后的字符串。 */
    val value: String,
) : PixelJsonValue

/** JSON 整数值；资源协议故意不接受浮点数。 */
internal data class PixelJsonInteger(
    /** 经过 Int 溢出校验的整数。 */
    val value: Int,
) : PixelJsonValue

/** JSON 布尔值。 */
internal data class PixelJsonBoolean(
    /** 布尔字面量。 */
    val value: Boolean,
) : PixelJsonValue

/** JSON null 单例。 */
internal data object PixelJsonNull : PixelJsonValue

/**
 * 只支持资源协议所需类型的严格有界 JSON 解析器。
 *
 * 与正则提取不同，本解析器会拒绝重复 key、尾随垃圾、未闭合结构、非法转义、浮点数和整数溢出。
 */
internal object PixelBoundedJson {
    /** 解析顶层 JSON 对象，并在创建大对象前应用 [limits]。 */
    fun parseObject(
        source: String,
        limits: PixelJsonLimits = PixelJsonLimits(),
    ): PixelJsonObject {
        require(source.length <= limits.maxInputChars) {
            "JSON input has ${source.length} chars, limit=${limits.maxInputChars}"
        }
        /** 当前输入对应的一次性递归下降解析器。 */
        val parser = Parser(source = source, limits = limits)
        /** 顶层解析结果，资源协议只接受对象。 */
        val value = parser.parseRoot()
        return value as? PixelJsonObject
            ?: throw IllegalArgumentException("JSON root must be an object")
    }

    /** 保存单次解析游标和限额，实例不会跨线程共享。 */
    private class Parser(
        /** 待读取的完整 JSON 文本。 */
        private val source: String,
        /** 本次解析必须遵守的安全上限。 */
        private val limits: PixelJsonLimits,
    ) {
        /** 下一个尚未消费字符的 UTF-16 索引。 */
        private var index: Int = 0

        /** 解析唯一根值，并拒绝根值后的非空白字符。 */
        fun parseRoot(): PixelJsonValue {
            skipWhitespace()
            /** 根 JSON 值。 */
            val value = parseValue(depth = 1)
            skipWhitespace()
            require(index == source.length) { "Unexpected trailing JSON at index $index" }
            return value
        }

        /** 根据当前首字符解析一个 JSON 值。 */
        private fun parseValue(depth: Int): PixelJsonValue {
            require(depth <= limits.maxDepth) { "JSON nesting exceeds ${limits.maxDepth}" }
            skipWhitespace()
            require(index < source.length) { "Unexpected end of JSON" }
            return when (source[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> PixelJsonString(parseString())
                't' -> parseLiteral("true", PixelJsonBoolean(true))
                'f' -> parseLiteral("false", PixelJsonBoolean(false))
                'n' -> parseLiteral("null", PixelJsonNull)
                '-', in '0'..'9' -> PixelJsonInteger(parseInteger())
                else -> throw IllegalArgumentException(
                    "Unexpected JSON character '${source[index]}' at index $index",
                )
            }
        }

        /** 解析对象并在插入前拒绝重复 key 和超量成员。 */
        private fun parseObject(depth: Int): PixelJsonObject {
            expect('{')
            /** 保持输入顺序且只在本方法内可变的对象成员。 */
            val members = linkedMapOf<String, PixelJsonValue>()
            skipWhitespace()
            if (consumeIf('}')) return PixelJsonObject(members.toMap())
            while (true) {
                require(members.size < limits.maxObjectMembers) {
                    "JSON object exceeds ${limits.maxObjectMembers} members"
                }
                skipWhitespace()
                require(peek() == '"') { "JSON object key must be a string at index $index" }
                /** 当前对象成员名。 */
                val name = parseString()
                require(name !in members) { "Duplicate JSON key '$name'" }
                skipWhitespace()
                expect(':')
                members[name] = parseValue(depth + 1)
                skipWhitespace()
                if (consumeIf('}')) break
                expect(',')
            }
            return PixelJsonObject(members.toMap())
        }

        /** 解析数组并在追加前限制元素数量。 */
        private fun parseArray(depth: Int): PixelJsonArray {
            expect('[')
            /** 保持输入顺序且只在本方法内可变的数组元素。 */
            val items = mutableListOf<PixelJsonValue>()
            skipWhitespace()
            if (consumeIf(']')) return PixelJsonArray(items.toList())
            while (true) {
                require(items.size < limits.maxArrayItems) {
                    "JSON array exceeds ${limits.maxArrayItems} items"
                }
                items += parseValue(depth + 1)
                skipWhitespace()
                if (consumeIf(']')) break
                expect(',')
            }
            return PixelJsonArray(items.toList())
        }

        /** 解析 JSON 字符串及其标准转义，并拒绝控制字符和过长结果。 */
        private fun parseString(): String {
            expect('"')
            /** 逐字符构造且受限的解码结果。 */
            val result = StringBuilder()
            while (index < source.length) {
                /** 当前尚未解码的源字符。 */
                val character = source[index++]
                when {
                    character == '"' -> return result.toString()
                    character == '\\' -> result.append(parseEscape())
                    character.code < 0x20 -> throw IllegalArgumentException(
                        "Unescaped control character in JSON string at index ${index - 1}",
                    )
                    else -> result.append(character)
                }
                require(result.length <= limits.maxStringChars) {
                    "JSON string exceeds ${limits.maxStringChars} chars"
                }
            }
            throw IllegalArgumentException("Unclosed JSON string")
        }

        /** 解析反斜杠后的一个 JSON 转义序列。 */
        private fun parseEscape(): Char {
            require(index < source.length) { "Unclosed JSON escape" }
            return when (val escaped = source[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> parseUnicodeEscape()
                else -> throw IllegalArgumentException("Unsupported JSON escape \\$escaped")
            }
        }

        /** 解析四位十六进制 Unicode 转义。 */
        private fun parseUnicodeEscape(): Char {
            require(index + 4 <= source.length) { "Truncated JSON unicode escape" }
            /** 四位十六进制转义文本。 */
            val digits = source.substring(index, index + 4)
            /** 解码后的 UTF-16 code unit。 */
            val value = digits.toIntOrNull(radix = 16)
                ?: throw IllegalArgumentException("Invalid JSON unicode escape '$digits'")
            index += 4
            return value.toChar()
        }

        /** 解析仅由资源协议支持的十进制 Int，拒绝前导零、浮点和溢出。 */
        private fun parseInteger(): Int {
            /** 当前整数在源文本中的起始索引。 */
            val start = index
            consumeIf('-')
            require(index < source.length && source[index].isDigit()) {
                "Expected JSON integer at index $index"
            }
            if (source[index] == '0') {
                index += 1
                require(index >= source.length || !source[index].isDigit()) {
                    "JSON integer must not contain leading zero at index $index"
                }
            } else {
                while (index < source.length && source[index].isDigit()) index += 1
            }
            require(index >= source.length || source[index] !in charArrayOf('.', 'e', 'E')) {
                "Floating-point JSON numbers are not supported at index $index"
            }
            /** 完整十进制整数字面量。 */
            val literal = source.substring(start, index)
            return literal.toIntOrNull()
                ?: throw IllegalArgumentException("JSON integer '$literal' exceeds Int range")
        }

        /** 消费固定字面量并返回其值。 */
        private fun parseLiteral(literal: String, value: PixelJsonValue): PixelJsonValue {
            require(source.regionMatches(index, literal, 0, literal.length)) {
                "Invalid JSON literal at index $index"
            }
            index += literal.length
            return value
        }

        /** 跳过 JSON 允许的四种空白字符。 */
        private fun skipWhitespace() {
            while (index < source.length && source[index] in charArrayOf(' ', '\t', '\r', '\n')) {
                index += 1
            }
        }

        /** 返回当前字符，输入结束时返回空。 */
        private fun peek(): Char? = source.getOrNull(index)

        /** 当前字符匹配时消费并返回 true。 */
        private fun consumeIf(expected: Char): Boolean {
            if (peek() != expected) return false
            index += 1
            return true
        }

        /** 要求并消费一个协议字符。 */
        private fun expect(expected: Char) {
            require(consumeIf(expected)) { "Expected '$expected' at JSON index $index" }
        }
    }
}

/** 返回必需字符串字段，并在类型不符时给出稳定错误。 */
internal fun PixelJsonObject.requireString(name: String): String {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: throw IllegalArgumentException("Missing string field '$name'")
    return (value as? PixelJsonString)?.value
        ?: throw IllegalArgumentException("Field '$name' must be a string")
}

/** 返回可选字符串字段；字段存在但类型不符时拒绝。 */
internal fun PixelJsonObject.optionalString(name: String): String? {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: return null
    return (value as? PixelJsonString)?.value
        ?: throw IllegalArgumentException("Field '$name' must be a string")
}

/** 返回必需整数段。 */
internal fun PixelJsonObject.requireInt(name: String): Int {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: throw IllegalArgumentException("Missing int field '$name'")
    return (value as? PixelJsonInteger)?.value
        ?: throw IllegalArgumentException("Field '$name' must be an int")
}

/** 返回可选整数；字段存在但类型不符时拒绝。 */
internal fun PixelJsonObject.optionalInt(name: String): Int? {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: return null
    return (value as? PixelJsonInteger)?.value
        ?: throw IllegalArgumentException("Field '$name' must be an int")
}

/** 返回必需数组字段。 */
internal fun PixelJsonObject.requireArray(name: String): PixelJsonArray {
    return members[name] as? PixelJsonArray
        ?: throw IllegalArgumentException("Missing or invalid array field '$name'")
}

/** 返回可选数组；字段存在但类型不符时拒绝。 */
internal fun PixelJsonObject.optionalArray(name: String): PixelJsonArray? {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: return null
    return value as? PixelJsonArray
        ?: throw IllegalArgumentException("Field '$name' must be an array")
}

/** 返回可选对象；字段存在但类型不符时拒绝。 */
internal fun PixelJsonObject.optionalObject(name: String): PixelJsonObject? {
    /** 字段原始 JSON 值。 */
    val value = members[name] ?: return null
    return value as? PixelJsonObject
        ?: throw IllegalArgumentException("Field '$name' must be an object")
}

/** 把数组严格转换成对象列表。 */
internal fun PixelJsonArray.requireObjects(fieldName: String): List<PixelJsonObject> {
    return items.mapIndexed { index, value ->
        value as? PixelJsonObject
            ?: throw IllegalArgumentException("Field '$fieldName' item[$index] must be an object")
    }
}

/** 把数组严格转换成字符串列表。 */
internal fun PixelJsonArray.requireStrings(fieldName: String): List<String> {
    return items.mapIndexed { index, value ->
        (value as? PixelJsonString)?.value
            ?: throw IllegalArgumentException("Field '$fieldName' item[$index] must be a string")
    }
}

/** 把对象严格转换成字符串 map。 */
internal fun PixelJsonObject.requireStringMap(fieldName: String): Map<String, String> {
    return members.mapValues { (name, value) ->
        (value as? PixelJsonString)?.value
            ?: throw IllegalArgumentException("Field '$fieldName.$name' must be a string")
    }
}
