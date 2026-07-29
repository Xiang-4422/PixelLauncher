package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactPhone
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactListModelTest {

    @Test
    fun rowsInsertHeaderAtEachInitialChange() {
        val rows = ContactListModel.rows(
            listOf(
                contact(name = "Alice", phonetic = "alice"),
                contact(name = "Adam", phonetic = "adam"),
                contact(name = "Bob", phonetic = "bob"),
                // 拼音缺失时降级用展示名首字符
                contact(name = "Carol", phonetic = ""),
            ),
        )

        val kinds = rows.map { row ->
            when (row) {
                is ContactListModel.Row.Header -> "H:${row.letter}"
                is ContactListModel.Row.Person -> "P:${row.contact.displayName}"
            }
        }
        assertEquals(
            listOf("H:A", "P:Alice", "P:Adam", "H:B", "P:Bob", "H:C", "P:Carol"),
            kinds,
        )
    }

    @Test
    fun initialFallsBackToHashForNonLetters() {
        // 汉字且无拼音、数字开头、空名——都归 # 组，不猜字母
        assertEquals("#", ContactListModel.initialOf(contact(name = "张三", phonetic = "")))
        assertEquals("#", ContactListModel.initialOf(contact(name = "10086", phonetic = "")))
        assertEquals("#", ContactListModel.initialOf(contact(name = "", phonetic = "")))
        // 中文 ROM 提供拼音排序键时按拼音分组
        assertEquals("Z", ContactListModel.initialOf(contact(name = "张三", phonetic = "zhang san")))
    }

    @Test
    fun numberBadgeOnlyAppearsForMultipleNumbers() {
        assertEquals("", ContactListModel.numberBadge(1))
        assertEquals("x2", ContactListModel.numberBadge(2))
    }

    @Test
    fun phoneTypeLabelMapsKnownTypesAndCustomLabel() {
        assertEquals("MOBILE", ContactListModel.phoneTypeLabel(type = 2, customLabel = ""))
        assertEquals("HOME", ContactListModel.phoneTypeLabel(type = 1, customLabel = ""))
        assertEquals("WORK", ContactListModel.phoneTypeLabel(type = 3, customLabel = ""))
        assertEquals("MAIN", ContactListModel.phoneTypeLabel(type = 12, customLabel = ""))
        assertEquals("OTHER", ContactListModel.phoneTypeLabel(type = 7, customLabel = ""))
        // 自定义类型取用户写的标签原文大写
        assertEquals("私人", ContactListModel.phoneTypeLabel(type = 0, customLabel = "私人"))
        assertEquals("BOSS", ContactListModel.phoneTypeLabel(type = 0, customLabel = " boss "))
        // 未知类型不瞎标
        assertEquals("", ContactListModel.phoneTypeLabel(type = 99, customLabel = ""))
    }

    private fun contact(name: String, phonetic: String): ContactDetail = ContactDetail(
        contactId = name.hashCode().toLong(),
        lookupKey = "key-$name",
        rawContactId = 1L,
        displayName = name,
        phoneticName = phonetic,
        numbers = listOf(ContactPhone(dataId = 1L, number = "10086", typeLabel = "MOBILE")),
    )
}
