package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [DrawerSearchSupport] — the drawer search/sort metadata builder
 * (label normalisation, pinyin tokenisation, A–Z letter indexing). Uses pinyin4j,
 * a pure-Java library, so it runs on a plain JVM.
 */
class DrawerSearchSupportTest {

    @Test
    fun normalizeForSearch_lowercasesAndStripsNoise() {
        assertEquals("helloworld", DrawerSearchSupport.normalizeForSearch("Hello World!"))
        assertEquals("wifi", DrawerSearchSupport.normalizeForSearch("Wi-Fi"))
        assertEquals("abc", DrawerSearchSupport.normalizeForSearch("A_B-C"))
        assertEquals("", DrawerSearchSupport.normalizeForSearch("   "))
    }

    @Test
    fun buildMetadata_asciiLabelKeepsLatinTokens() {
        val meta = DrawerSearchSupport.buildMetadata(label = "Gmail", packageName = "com.google.android.gm")
        assertEquals("gmail", meta.normalizedLabel)
        assertEquals("gmail", meta.pinyinFull)
        assertEquals("g", meta.pinyinInitial)
        assertEquals(6, meta.letterIndex) // 'G'
        assertEquals("gm", meta.normalizedAlias) // package substringAfterLast('.')
    }

    @Test
    fun buildMetadata_chineseLabelProducesPinyin() {
        val meta = DrawerSearchSupport.buildMetadata(label = "微信", packageName = "com.tencent.mm")
        assertEquals("weixin", meta.pinyinFull)
        assertEquals("wx", meta.pinyinInitial)
        assertEquals(22, meta.letterIndex) // pinyin initial 'w' -> 'W'
        assertEquals("mm", meta.normalizedAlias)
    }

    @Test
    fun letterIndexForLabel_mapsLatinInitial() {
        assertEquals(0, DrawerSearchSupport.letterIndexForLabel("Apple"))
        assertEquals(25, DrawerSearchSupport.letterIndexForLabel("zebra"))
        assertEquals(12, DrawerSearchSupport.letterIndexForLabel("  Mango")) // leading space trimmed
    }

    @Test
    fun letterIndexForLabel_usesPinyinInitialForChinese() {
        assertEquals(22, DrawerSearchSupport.letterIndexForLabel("微信")) // wei -> 'W'
        assertEquals(13, DrawerSearchSupport.letterIndexForLabel("你好")) // ni  -> 'N'
    }
}
