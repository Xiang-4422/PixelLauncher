package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerSearchSupportTest {

    @Test
    fun metadataIncludesPinyinFullAndInitialForChineseLabel() {
        val metadata = DrawerSearchSupport.buildMetadata(
            label = "支付宝",
            packageName = "com.alipay.mobile",
        )

        assertTrue(metadata.pinyinFull.startsWith("zhifubao"))
        assertTrue(metadata.pinyinInitial.startsWith("zfb"))
    }

    @Test
    fun normalizeForSearchRemovesSpacesAndPunctuation() {
        val normalized = DrawerSearchSupport.normalizeForSearch(" Alipay-Pay  ")
        assertEquals("alipaypay", normalized)
    }

    @Test
    fun nonLatinFallbackLetterIndexIsStableAndNotForcedToZ() {
        val first = DrawerSearchSupport.letterIndexForLabel("Ωmega")
        val second = DrawerSearchSupport.letterIndexForLabel("Ωmega")
        assertEquals(first, second)
        assertTrue(first in 0 until DrawerAlphaIndexModel.lastLetterIndex)
    }
}
