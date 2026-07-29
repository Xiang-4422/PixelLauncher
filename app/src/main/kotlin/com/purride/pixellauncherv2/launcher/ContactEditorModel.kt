package com.purride.pixellauncherv2.launcher

/**
 * 联系人编辑器的纯校验逻辑。
 *
 * 编辑器语义刻意极简：一个姓名字段 + 一个"新增号码"字段；既有号码只能整条删除，
 * 不做逐位改号——动态数量的输入框在点阵 UI 上成本高且易错，改号 = 删旧 + 加新。
 */
object ContactEditorModel {

    /** 号码允许的字符：与拨号盘一致，另放行常见分隔符（空格、连字符、括号）。 */
    private const val NUMBER_CHARS = "0123456789*#+,; ()-"

    /** 姓名：去空白后非空即可，不做字种限制。 */
    fun isValidName(name: String): Boolean = name.trim().isNotEmpty()

    /**
     * 号码草稿是否可保存：**空串合法**（编辑时表示"不新增号码"），
     * 非空则必须含至少一位数字且全部字符可拨/可作分隔。
     */
    fun isValidNumberDraft(number: String): Boolean {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.none(Char::isDigit)) return false
        return trimmed.all { char -> char in NUMBER_CHARS }
    }

    /**
     * 是否可以保存：新建（[hasExistingContact] 为 false）必须同时给出姓名与号码
     * ——没有号码的联系人在这个拨号目录里既不可见也不可操作；
     * 编辑既有联系人只要求姓名合法，号码字段留空表示不加。
     */
    fun canSave(name: String, numberDraft: String, hasExistingContact: Boolean): Boolean {
        if (!isValidName(name)) return false
        if (!isValidNumberDraft(numberDraft)) return false
        if (!hasExistingContact && numberDraft.trim().isEmpty()) return false
        return true
    }
}
