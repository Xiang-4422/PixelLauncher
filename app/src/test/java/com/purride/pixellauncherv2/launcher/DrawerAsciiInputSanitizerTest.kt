package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerAsciiInputSanitizerTest {

    @Test
    fun filterKeepsAsciiLettersDigitsPunctuationAndSpace() {
        val filtered = DrawerAsciiInputSanitizer.filter("abc XYZ 123-_:.")

        assertEquals("abc XYZ 123-_:.", filtered)
    }

    @Test
    fun filterDropsChineseAndOtherNonAsciiCharacters() {
        val filtered = DrawerAsciiInputSanitizer.filter("weixin 微信éß")

        assertEquals("weixin ", filtered)
    }
}
