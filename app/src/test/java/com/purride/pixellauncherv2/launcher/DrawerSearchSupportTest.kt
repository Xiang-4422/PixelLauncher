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
    fun buildMetadata_userAliasesAreNormalizedAndDeduped() {
        val meta = DrawerSearchSupport.buildMetadata(
            label = "Bank",
            aliases = listOf("Pay Me", "pay-me", "账 单"),
            packageName = "com.example.bank",
        )

        assertEquals(listOf("payme", "账单"), meta.normalizedUserAliases)
    }

    @Test
    fun matchSourceLabel_explainsNonLabelSearchHits() {
        assertEquals(
            "ALIAS",
            DrawerSearchSupport.matchSourceLabel(
                AppEntry(
                    label = "Bank",
                    packageName = "com.example.bank",
                    activityName = "BankActivity",
                    aliases = listOf("pay bill"),
                ),
                "pay",
            ),
        )
        assertEquals(
            "PINYIN",
            DrawerSearchSupport.matchSourceLabel(
                AppEntry(label = "微信", packageName = "com.tencent.mm", activityName = "MainActivity"),
                "wx",
            ),
        )
        assertEquals(
            "PKG",
            DrawerSearchSupport.matchSourceLabel(
                AppEntry(label = "Browser", packageName = "org.mozilla.firefox", activityName = "MainActivity"),
                "mozilla",
            ),
        )
        assertEquals(
            "ACT",
            DrawerSearchSupport.matchSourceLabel(
                AppEntry(label = "Camera", packageName = "com.example.camera", activityName = "CaptureActivity"),
                "capture",
            ),
        )
    }

    @Test
    fun matchSourceLabel_omitsVisibleLabelHits() {
        assertEquals(
            null,
            DrawerSearchSupport.matchSourceLabel(
                AppEntry(label = "Gmail", packageName = "com.google.android.gm", activityName = "MailActivity"),
                "gm",
            ),
        )
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
