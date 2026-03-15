package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerAlphaIndexModelTest {

    @Test
    fun createMapsLettersAndNonLatinToZ() {
        val apps = listOf(
            AppEntry(label = "Alpha", packageName = "pkg.alpha", activityName = "A"),
            AppEntry(label = "beta", packageName = "pkg.beta", activityName = "B"),
            AppEntry(label = "中文应用", packageName = "pkg.cn", activityName = "C"),
        )

        val model = DrawerAlphaIndexModel.create(
            apps = apps,
            selectedIndex = 2,
        )

        assertEquals(0, model.letterToFirstAppIndex[0])
        assertEquals(1, model.letterToFirstAppIndex[1])
        assertEquals(2, model.letterToFirstAppIndex[25])
        assertEquals(25, model.selectedLetterIndex)
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
