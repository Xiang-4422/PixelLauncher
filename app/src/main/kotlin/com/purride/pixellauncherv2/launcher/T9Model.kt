package com.purride.pixellauncherv2.launcher

/**
 * T9 智能拨号的纯匹配逻辑（字母↔数字映射遵循 ITU-T E.161 国际标准）。
 *
 * 匹配三条通路，命中任一即算匹配：
 * 1. 号码子串——输入的数字出现在号码里；
 * 2. 姓名整串——姓名字母转成数字后以输入开头，允许从任意词首起算；
 * 3. 姓名首字母——各词首字母转成的数字串以输入开头。
 *
 * 中文姓名本身没有字母，靠调用方传入拼音（中文 ROM 的 SORT_KEY_PRIMARY
 * 通常就是拼音）参与同样的匹配。不含 Android 依赖，便于完整单测。
 */
object T9Model {

    /** 字母到按键数字的映射（E.161）。 */
    fun digitForLetter(letter: Char): Char? = when (letter.lowercaseChar()) {
        'a', 'b', 'c' -> '2'
        'd', 'e', 'f' -> '3'
        'g', 'h', 'i' -> '4'
        'j', 'k', 'l' -> '5'
        'm', 'n', 'o' -> '6'
        'p', 'q', 'r', 's' -> '7'
        't', 'u', 'v' -> '8'
        'w', 'x', 'y', 'z' -> '9'
        else -> null
    }

    /** 按键上的字母副标；无字母的键返回空串。 */
    fun letterHint(key: Char): String = when (key) {
        '2' -> "ABC"
        '3' -> "DEF"
        '4' -> "GHI"
        '5' -> "JKL"
        '6' -> "MNO"
        '7' -> "PQRS"
        '8' -> "TUV"
        '9' -> "WXYZ"
        '0' -> "+"
        else -> ""
    }

    /**
     * 输入是否可用于 T9 检索。
     *
     * 只有纯数字才做姓名匹配：一旦带上 * # + 等拨号符号，说明用户在拨特殊号码
     * （如 *#06#、+86 前缀），此时按姓名检索没有意义，只做号码匹配。
     */
    fun isSearchableQuery(query: String): Boolean =
        query.isNotEmpty() && query.all(Char::isDigit)

    /**
     * 判断一个联系人是否命中输入。
     *
     * [name] 展示名，[phonetic] 拼音或注音（可空），[number] 号码。
     */
    fun matches(query: String, name: String, phonetic: String, number: String): Boolean {
        val digitsQuery = query.filter(Char::isDigit)
        if (digitsQuery.isEmpty()) {
            return false
        }
        if (numberContains(number, digitsQuery)) {
            return true
        }
        // 号码里含 * # 时不再按姓名检索。
        if (!isSearchableQuery(query)) {
            return false
        }
        return nameMatches(name, digitsQuery) || nameMatches(phonetic, digitsQuery)
    }

    /** 号码去掉分隔符后是否包含输入数字串。 */
    fun numberContains(number: String, digitsQuery: String): Boolean {
        if (digitsQuery.isEmpty()) return false
        return number.filter(Char::isDigit).contains(digitsQuery)
    }

    /** 姓名（或拼音）是否命中：从任意词首起算的整串前缀，或各词首字母串前缀。 */
    private fun nameMatches(text: String, digitsQuery: String): Boolean {
        if (text.isBlank()) return false
        val words = text.split(*WORD_SEPARATORS).filter(String::isNotBlank)
        if (words.isEmpty()) return false
        // 各词首字母：输 "57" 命中 "LI SI"。
        val initials = words.mapNotNull { word -> word.firstOrNull()?.let(::digitForLetter) }
            .joinToString("")
        if (initials.startsWith(digitsQuery)) {
            return true
        }
        // 从任意词首起算的整串：输 "747" 命中 "SI" 起头的部分。
        for (index in words.indices) {
            val tailDigits = words.drop(index).joinToString("").mapNotNull(::digitForLetter).joinToString("")
            if (tailDigits.startsWith(digitsQuery)) {
                return true
            }
        }
        return false
    }

    private val WORD_SEPARATORS = charArrayOf(' ', '\t', '-', '·', '.', ',')
}
