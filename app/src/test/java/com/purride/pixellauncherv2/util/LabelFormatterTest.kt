package com.purride.pixellauncherv2.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelFormatterTest {

    @Test
    fun displayLabelPreservesCaseAndCollapsesWhitespace() {
        val label = LabelFormatter.displayLabel("  WeChat   设置  ")

        assertEquals("WeChat 设置", label)
    }
}
