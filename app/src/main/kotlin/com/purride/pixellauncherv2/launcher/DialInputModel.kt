package com.purride.pixellauncherv2.launcher

/**
 * 拨号盘输入的纯编辑逻辑。
 *
 * 只接受可拨字符，并给输入长度设上限——号码框是单行显示，无节制增长会把
 * 界面挤爆。不做任何号码美化分组：像素字体下额外的空格反而更难读。
 */
object DialInputModel {

    /** 拨号盘可产出的字符：数字、* #、以及国际前缀与暂停/等待符。 */
    private const val DIALABLE_CHARS = "0123456789*#+,;"

    /** 输入上限。国际号码最长 15 位，留足暂停符与前缀的余量。 */
    const val MAX_LENGTH = 24

    /** 大陆手机号位数，用于分组显示。 */
    private const val MAINLAND_MOBILE_LENGTH = 11

    /** 头部省略号；像素字体里单字符 … 未必有字形，用 ASCII 点。 */
    private const val ELLIPSIS = ".."

    /** 追加一个字符；非可拨字符与超长输入原样返回。 */
    fun append(current: String, digit: Char): String {
        if (digit !in DIALABLE_CHARS) return current
        if (current.length >= MAX_LENGTH) return current
        // "+" 只在最开头有意义，中间出现的一律忽略。
        if (digit == '+' && current.isNotEmpty()) return current
        return current + digit
    }

    /** 删除末位字符。 */
    fun backspace(current: String): String =
        if (current.isEmpty()) current else current.dropLast(1)

    /** 是否可以发起呼叫。 */
    fun isCallable(input: String): Boolean = input.any { it in DIALABLE_CHARS }

    /**
     * 号码框展示文本。空输入时给出提示占位，避免出现一行空白。
     *
     * 大陆 11 位手机号按 3/4/4 分组，长号码更易逐段核对；其余形态原样显示
     * （国际号码、短号、带 * # 的特殊号码分组规则各异，强行切分反而更难读）。
     */
    fun displayText(input: String, placeholder: String = "ENTER NUMBER"): String {
        if (input.isEmpty()) return placeholder
        return groupMainlandMobile(input)
    }

    /**
     * 超长号码的截断：**保留尾部、在头部加省略号**。
     *
     * 尾号是用户核对刚按下的数字的唯一依据，从尾部截断等于把刚输入的内容藏起来。
     * [maxChars] 由调用方按当前字号能容纳的字符数给出。
     */
    fun truncateKeepingTail(text: String, maxChars: Int): String {
        if (maxChars <= 0 || text.length <= maxChars) return text
        // 连省略号都放不下时直接给尾部：尾号比一串点更有信息量。
        if (maxChars <= ELLIPSIS.length) return text.takeLast(maxChars)
        return ELLIPSIS + text.takeLast(maxChars - ELLIPSIS.length)
    }

    /** 大陆手机号 3/4/4 分组；不满足条件时原样返回。 */
    private fun groupMainlandMobile(input: String): String {
        if (input.length != MAINLAND_MOBILE_LENGTH) return input
        if (!input.all(Char::isDigit)) return input
        if (!input.startsWith("1")) return input
        return "${input.substring(0, 3)} ${input.substring(3, 7)} ${input.substring(7)}"
    }

    /** 硬件按键码到拨号字符的映射；非拨号键返回 null。 */
    fun digitForKeyCode(keyCode: Int): Char? = when (keyCode) {
        KEYCODE_0, KEYCODE_NUMPAD_0 -> '0'
        KEYCODE_1, KEYCODE_NUMPAD_1 -> '1'
        KEYCODE_2, KEYCODE_NUMPAD_2 -> '2'
        KEYCODE_3, KEYCODE_NUMPAD_3 -> '3'
        KEYCODE_4, KEYCODE_NUMPAD_4 -> '4'
        KEYCODE_5, KEYCODE_NUMPAD_5 -> '5'
        KEYCODE_6, KEYCODE_NUMPAD_6 -> '6'
        KEYCODE_7, KEYCODE_NUMPAD_7 -> '7'
        KEYCODE_8, KEYCODE_NUMPAD_8 -> '8'
        KEYCODE_9, KEYCODE_NUMPAD_9 -> '9'
        KEYCODE_STAR -> '*'
        KEYCODE_POUND -> '#'
        KEYCODE_PLUS -> '+'
        else -> null
    }

    /** 拨号盘按键的显示顺序：3 列 4 行。 */
    val keypadRows: List<List<Char>> = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#'),
    )

    // android.view.KeyEvent 常量，避免纯逻辑对象依赖 Android 类型。
    private const val KEYCODE_0 = 7
    private const val KEYCODE_1 = 8
    private const val KEYCODE_2 = 9
    private const val KEYCODE_3 = 10
    private const val KEYCODE_4 = 11
    private const val KEYCODE_5 = 12
    private const val KEYCODE_6 = 13
    private const val KEYCODE_7 = 14
    private const val KEYCODE_8 = 15
    private const val KEYCODE_9 = 16
    private const val KEYCODE_STAR = 17
    private const val KEYCODE_POUND = 18
    private const val KEYCODE_PLUS = 81
    private const val KEYCODE_NUMPAD_0 = 144
    private const val KEYCODE_NUMPAD_1 = 145
    private const val KEYCODE_NUMPAD_2 = 146
    private const val KEYCODE_NUMPAD_3 = 147
    private const val KEYCODE_NUMPAD_4 = 148
    private const val KEYCODE_NUMPAD_5 = 149
    private const val KEYCODE_NUMPAD_6 = 150
    private const val KEYCODE_NUMPAD_7 = 151
    private const val KEYCODE_NUMPAD_8 = 152
    private const val KEYCODE_NUMPAD_9 = 153
}
