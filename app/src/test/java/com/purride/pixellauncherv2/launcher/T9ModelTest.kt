package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T9ModelTest {

    @Test
    fun digitForLetterFollowsE161Mapping() {
        assertEquals('2', T9Model.digitForLetter('a'))
        assertEquals('2', T9Model.digitForLetter('C'))
        assertEquals('7', T9Model.digitForLetter('s'))
        assertEquals('9', T9Model.digitForLetter('Z'))
        assertNull(T9Model.digitForLetter('1'))
        assertNull(T9Model.digitForLetter('中'))
    }

    @Test
    fun letterHintCoversKeysWithLetters() {
        assertEquals("ABC", T9Model.letterHint('2'))
        assertEquals("PQRS", T9Model.letterHint('7'))
        assertEquals("WXYZ", T9Model.letterHint('9'))
        assertEquals("+", T9Model.letterHint('0'))
        assertEquals("", T9Model.letterHint('1'))
        assertEquals("", T9Model.letterHint('#'))
    }

    @Test
    fun isSearchableQueryRequiresPureDigits() {
        assertTrue(T9Model.isSearchableQuery("926"))
        assertFalse(T9Model.isSearchableQuery(""))
        // 带拨号符号说明在拨特殊号码，不该再按姓名检索。
        assertFalse(T9Model.isSearchableQuery("*#06#"))
        assertFalse(T9Model.isSearchableQuery("+8613"))
    }

    @Test
    fun matchesNumberSubstringRegardlessOfSeparators() {
        assertTrue(T9Model.matches("138", name = "", phonetic = "", number = "138 0013-8000"))
        assertTrue(T9Model.matches("8000", name = "", phonetic = "", number = "13800138000"))
        assertFalse(T9Model.matches("999", name = "", phonetic = "", number = "13800138000"))
    }

    @Test
    fun matchesLatinNameByWordInitials() {
        // "LI SI" 首字母 L(5) S(7)
        assertTrue(T9Model.matches("57", name = "LI SI", phonetic = "", number = "10010"))
        assertTrue(T9Model.matches("5", name = "LI SI", phonetic = "", number = "10010"))
        assertFalse(T9Model.matches("58", name = "LI SI", phonetic = "", number = "10010"))
    }

    @Test
    fun matchesLatinNameFromAnyWordStart() {
        // "SAN" → 726，应从第二个词起算命中
        assertTrue(T9Model.matches("726", name = "ZHANG SAN", phonetic = "", number = "10086"))
        // "ZHANG" → 94264
        assertTrue(T9Model.matches("9426", name = "ZHANG SAN", phonetic = "", number = "10086"))
    }

    @Test
    fun matchesChineseNameThroughPhonetic() {
        // 中文名本身无字母；拼音参与匹配（中文 ROM 的 sort_key 通常是拼音）
        assertTrue(T9Model.matches("926", name = "王五", phonetic = "WANG WU", number = "13912345678"))
        assertTrue(T9Model.matches("98", name = "王五", phonetic = "WANG WU", number = "13912345678"))
        assertFalse(T9Model.matches("53", name = "王五", phonetic = "WANG WU", number = "13912345678"))
    }

    @Test
    fun chineseNameWithoutPhoneticFallsBackToNumberOnly() {
        assertFalse(T9Model.matches("926", name = "王五", phonetic = "", number = "13912345678"))
        assertTrue(T9Model.matches("1391", name = "王五", phonetic = "", number = "13912345678"))
    }

    @Test
    fun dialSymbolsSkipNameSearchButKeepNumberSearch() {
        // *#06# 这类输入不该匹配姓名
        assertFalse(T9Model.matches("*#06#", name = "ZHANG SAN", phonetic = "", number = "10086"))
        // 但数字部分仍可命中号码
        assertTrue(T9Model.matches("+8610086", name = "", phonetic = "", number = "8610086"))
    }

    @Test
    fun emptyQueryNeverMatches() {
        assertFalse(T9Model.matches("", name = "ZHANG SAN", phonetic = "", number = "10086"))
    }

    @Test
    fun separatorsInNameAreTreatedAsWordBoundaries() {
        // 分隔符被当作词边界后，首字母串为 L(5) S(7)。
        assertTrue(T9Model.matches("57", name = "LI·SI", phonetic = "", number = "1"))
        assertTrue(T9Model.matches("57", name = "LI-SI", phonetic = "", number = "1"))
        // 反证分隔符确实生效：同样的字母不带分隔符时只有一个词，
        // 首字母串是 "5"、整串是 "5474"，"57" 两条通路都不该命中。
        assertFalse(T9Model.matches("57", name = "LISI", phonetic = "", number = "1"))
    }
}
