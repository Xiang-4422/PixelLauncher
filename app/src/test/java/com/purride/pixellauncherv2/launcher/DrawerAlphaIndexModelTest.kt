package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerAlphaIndexModelTest {

    @Test
    fun createMapsChineseLabelByPinyinInitialInsteadOfZBucket() {
        val apps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "A"),
            AppEntry(label = "beta", packageName = "pkg.beta", activityName = "B"),
            AppEntry(label = "微信", packageName = "pkg.wechat", activityName = "C"),
        )

        val model = DrawerAlphaIndexModel.create(
            apps = apps,
            selectedIndex = 2,
        )

        assertEquals(0, model.letterToFirstAppIndex[0])
        assertEquals(1, model.letterToFirstAppIndex[1])
        assertEquals(2, model.letterToFirstAppIndex[22]) // W
        assertEquals(22, model.selectedLetterIndex)
    }

    @Test
    fun nonLatinWithoutPinyinUsesDistributedFallbackBuckets() {
        val omegaIndex = DrawerAlphaIndexModel.letterIndexForLabel("Ωmega")
        val cyrillicIndex = DrawerAlphaIndexModel.letterIndexForLabel("Журнал")

        assertEquals(false, omegaIndex == DrawerAlphaIndexModel.lastLetterIndex)
        assertEquals(false, cyrillicIndex == DrawerAlphaIndexModel.lastLetterIndex)
    }

    @Test
    fun resolveNearestLetterPrefersLeftOnTie() {
        val apps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "A"),
            AppEntry(label = "Echo", packageName = "pkg.echo", activityName = "E"),
        )
        val model = DrawerAlphaIndexModel.create(
            apps = apps,
            selectedIndex = 0,
        )

        val targetIndex = model.resolveNearestLetterAppIndex(2)

        assertEquals(0, targetIndex)
    }

    @Test
    fun resolveNearestLetterReturnsNullWhenNoApps() {
        val model = DrawerAlphaIndexModel.create(
            apps = emptyList(),
            selectedIndex = 0,
        )

        assertNull(model.resolveNearestLetterAppIndex(10))
    }
}
