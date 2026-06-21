package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [DrawerAsciiInputSanitizer.filter] — uppercases printable ASCII
 * (code points 32..126) and drops control characters, DEL and non-ASCII input.
 * JVM-safe; no Android dependencies.
 */
class DrawerAsciiInputSanitizerTest {

    @Test
    fun filter_uppercasesPrintableAscii() {
        assertEquals("HELLO 123!", DrawerAsciiInputSanitizer.filter("Hello 123!"))
    }

    @Test
    fun filter_dropsControlCharacters() {
        assertEquals("AB", DrawerAsciiInputSanitizer.filter("A\tB\n"))
    }

    @Test
    fun filter_dropsNonAsciiAndDel() {
        assertEquals("AB", DrawerAsciiInputSanitizer.filter("a你b")) // CJK char dropped
        assertEquals("X", DrawerAsciiInputSanitizer.filter("X")) // DEL (127) dropped
    }

    @Test
    fun filter_keepsBoundaryPrintables() {
        // unit separator (31) dropped; space (32) and tilde (126) kept
        assertEquals(" ~", DrawerAsciiInputSanitizer.filter(" ~"))
    }

    @Test
    fun filter_emptyReturnsEmpty() {
        assertEquals("", DrawerAsciiInputSanitizer.filter(""))
    }
}
