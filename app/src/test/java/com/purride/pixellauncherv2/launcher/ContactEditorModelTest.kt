package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEditorModelTest {

    @Test
    fun nameRequiresNonBlankContent() {
        assertTrue(ContactEditorModel.isValidName("张三"))
        assertTrue(ContactEditorModel.isValidName(" Alice "))
        assertFalse(ContactEditorModel.isValidName(""))
        assertFalse(ContactEditorModel.isValidName("   "))
    }

    @Test
    fun numberDraftAllowsEmptyButRejectsGarbage() {
        // 空串合法：编辑时表示"不新增号码"
        assertTrue(ContactEditorModel.isValidNumberDraft(""))
        assertTrue(ContactEditorModel.isValidNumberDraft("13800138000"))
        assertTrue(ContactEditorModel.isValidNumberDraft("+86 138-0013-8000"))
        assertTrue(ContactEditorModel.isValidNumberDraft("(0571) 2801 1258"))
        // 无数字或含字母的一律拒绝
        assertFalse(ContactEditorModel.isValidNumberDraft("++--"))
        assertFalse(ContactEditorModel.isValidNumberDraft("call me"))
        assertFalse(ContactEditorModel.isValidNumberDraft("138abc"))
    }

    @Test
    fun creatingRequiresBothNameAndNumberEditingOnlyName() {
        // 新建：姓名 + 号码缺一不可——没有号码的联系人在拨号目录里不可见也不可操作
        assertTrue(ContactEditorModel.canSave("张三", "10086", hasExistingContact = false))
        assertFalse(ContactEditorModel.canSave("张三", "", hasExistingContact = false))
        assertFalse(ContactEditorModel.canSave("", "10086", hasExistingContact = false))
        // 编辑：号码留空表示不加，仅要求姓名合法
        assertTrue(ContactEditorModel.canSave("张三", "", hasExistingContact = true))
        assertFalse(ContactEditorModel.canSave("", "", hasExistingContact = true))
        // 编辑时填了号码就必须合法
        assertFalse(ContactEditorModel.canSave("张三", "garbage", hasExistingContact = true))
    }
}
