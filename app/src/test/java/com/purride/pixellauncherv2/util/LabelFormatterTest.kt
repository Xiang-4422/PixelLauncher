package com.purride.pixellauncherv2.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Coverage for [LabelFormatter] — whitespace collapsing, blank fallbacks and the
 * lowercased sort key. All JVM-safe; no Android dependencies.
 */
class LabelFormatterTest {

    @Test
    fun displayLabel_collapsesInnerWhitespaceAndTrims() {
        assertEquals("HELLO WORLD", LabelFormatter.displayLabel("  HELLO   WORLD  "))
    }

    @Test
    fun displayLabel_blankReturnsAppPlaceholder() {
        assertEquals("APP", LabelFormatter.displayLabel("   "))
        assertEquals("APP", LabelFormatter.displayLabel(""))
    }

    @Test
    fun fallbackLabel_usesLabelWhenNotBlank() {
        assertEquals("CAMERA", LabelFormatter.fallbackLabel("  CAMERA ", "com.android.camera"))
    }

    @Test
    fun fallbackLabel_fallsBackToPackageTailWhenLabelBlank() {
        assertEquals("camera", LabelFormatter.fallbackLabel("   ", "com.android.camera"))
    }

    @Test
    fun fallbackLabel_blankLabelAndPackageReturnsApp() {
        assertEquals("app", LabelFormatter.fallbackLabel("", ""))
    }

    @Test
    fun sortKey_collapsesWhitespaceAndLowercases() {
        assertEquals("hello world", LabelFormatter.sortKey("  HELLO   World "))
    }
}
