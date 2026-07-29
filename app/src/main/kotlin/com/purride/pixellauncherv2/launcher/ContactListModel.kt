package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.ContactDetail

/**
 * 联系人目录页的纯展示逻辑：拼音首字母分组与号码类型标签。
 *
 * 不依赖 Android 类型（ContactsContract 的 TYPE_* 常量按值复制），保持 JVM 可测。
 */
object ContactListModel {

    /** 目录列表的一行：分组字母头或联系人。 */
    sealed interface Row {
        /** 分组字母头（A–Z 或 #）。 */
        data class Header(val letter: String) : Row

        /** 联系人条目。 */
        data class Person(val contact: ContactDetail) : Row
    }

    /**
     * 把已按排序键排好的联系人摊平成「字母头 + 条目」的行序列。
     *
     * 首字母取拼音排序键（Chinese ROM 上 SORT_KEY_PRIMARY 即拼音），缺失时降级
     * 用展示名；非 A–Z 一律归入 `#` 组。输入顺序原样保留——排序是 provider 的
     * 职责，这里重排会与 T9/系统通讯录的顺序打架。
     */
    fun rows(contacts: List<ContactDetail>): List<Row> {
        val rows = ArrayList<Row>(contacts.size + GROUP_ESTIMATE)
        var currentLetter: String? = null
        contacts.forEach { contact ->
            val letter = initialOf(contact)
            if (letter != currentLetter) {
                currentLetter = letter
                rows += Row.Header(letter)
            }
            rows += Row.Person(contact)
        }
        return rows
    }

    /**
     * 联系人的分组首字母：A–Z 大写，其余归 `#`。
     *
     * 优先用 provider 的分桶字母（phonebook_label）——它按 collator 把汉字归到
     * 拼音首字母（阿→A、爸→B），与系统通讯录一致。没有它时退回排序键/展示名的
     * 首字符启发：在 SORT_KEY 为汉字原文的 ROM 上，这条降级会把中文联系人都归
     * `#`，分组名存实亡，所以分桶字母能取就必须取。
     */
    fun initialOf(contact: ContactDetail): String {
        val labeled = contact.groupLabel.firstOrNull { !it.isWhitespace() }?.uppercaseChar()
        if (labeled != null) {
            return if (labeled in 'A'..'Z') labeled.toString() else FALLBACK_GROUP
        }
        val source = contact.phoneticName.ifBlank { contact.displayName }
        val first = source.firstOrNull { !it.isWhitespace() } ?: return FALLBACK_GROUP
        val upper = first.uppercaseChar()
        return if (upper in 'A'..'Z') upper.toString() else FALLBACK_GROUP
    }

    /** 多号码徽标：单号码为空，多号码显示 xN（与通话记录合并徽标同形）。 */
    fun numberBadge(numberCount: Int): String = if (numberCount > 1) "x$numberCount" else ""

    /**
     * 号码类型标签。[type] 为 ContactsContract 的 Phone.TYPE 值；
     * 自定义类型（TYPE_CUSTOM）取用户写的 [customLabel] 原文大写。
     * 未知类型返回空——瞎标一个类型不如不标。
     */
    fun phoneTypeLabel(type: Int, customLabel: String): String = when (type) {
        TYPE_CUSTOM -> customLabel.trim().uppercase()
        TYPE_HOME -> "HOME"
        TYPE_MOBILE -> "MOBILE"
        TYPE_WORK -> "WORK"
        TYPE_MAIN -> "MAIN"
        TYPE_OTHER -> "OTHER"
        else -> ""
    }

    /** 分组数量的容量预估，只影响 ArrayList 扩容。 */
    private const val GROUP_ESTIMATE = 27

    private const val FALLBACK_GROUP = "#"

    // android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_* 常量，按值复制。
    private const val TYPE_CUSTOM = 0
    private const val TYPE_HOME = 1
    private const val TYPE_MOBILE = 2
    private const val TYPE_WORK = 3
    private const val TYPE_OTHER = 7
    private const val TYPE_MAIN = 12
}
