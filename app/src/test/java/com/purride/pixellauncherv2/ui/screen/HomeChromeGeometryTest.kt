package com.purride.pixellauncherv2.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/** HOME 底栏宽度必须使用调用方提供的当前 CHROME 栅格测量。 */
class HomeChromeGeometryTest {

    @Test
    fun actionSegmentWidth_usesCurrentChromeMeasurementAndPadding() {
        val measuredTexts = mutableListOf<String>()

        val width = homeActionSegmentWidth("CALL") { text ->
            measuredTexts += text
            32
        }

        assertEquals(listOf("CALL"), measuredTexts)
        assertEquals(36, width)
    }
}
