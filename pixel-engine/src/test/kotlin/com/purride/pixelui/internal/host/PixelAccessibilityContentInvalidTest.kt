package com.purride.pixelui.internal.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM contract for Android AccessibilityNodeInfo invalid-content mapping. */
class PixelAccessibilityContentInvalidTest {
    /** Missing or whitespace-only semantic errors do not mark platform content invalid. */
    @Test
    fun absentErrorsRemainValid() {
        assertFalse(pixelAccessibilityContentInvalid(null))
        assertFalse(pixelAccessibilityContentInvalid(""))
        assertFalse(pixelAccessibilityContentInvalid("   "))
    }

    /** Any speakable semantic error marks the corresponding platform node invalid. */
    @Test
    fun nonBlankErrorMarksContentInvalid() {
        assertTrue(pixelAccessibilityContentInvalid("Invalid account"))
    }
}
