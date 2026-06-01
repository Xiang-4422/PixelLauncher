package com.purride.pixellauncherv2.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for [DrawerAlphaIndexModel] — the A–Z quick-jump rail (first app per
 * letter + nearest-letter resolution). Pure, JVM-safe (letter indexing delegates
 * to DrawerSearchSupport).
 */
class DrawerAlphaIndexModelTest {

    private fun app(label: String) = AppEntry(label = label, packageName = "p.${label.lowercase()}", activityName = "Act")

    private val apps = listOf(app("Apple"), app("Apricot"), app("Banana"), app("Cherry"))

    @Test
    fun create_recordsFirstAppIndexPerLetter() {
        val model = DrawerAlphaIndexModel.create(apps = apps, selectedIndex = 0)
        assertEquals(0, model.letterToFirstAppIndex[0]) // A -> Apple (first A wins over Apricot)
        assertEquals(2, model.letterToFirstAppIndex[1]) // B -> Banana
        assertEquals(3, model.letterToFirstAppIndex[2]) // C -> Cherry
        assertEquals(-1, model.letterToFirstAppIndex[3]) // D -> none
    }

    @Test
    fun create_selectedLetterFollowsSelection() {
        val model = DrawerAlphaIndexModel.create(apps = apps, selectedIndex = 2) // Banana
        assertEquals(1, model.selectedLetterIndex)
    }

    @Test
    fun resolveNearestLetterAppIndex_directHit() {
        val model = DrawerAlphaIndexModel.create(apps = apps, selectedIndex = 0)
        assertEquals(0, model.resolveNearestLetterAppIndex(0)) // A
        assertEquals(2, model.resolveNearestLetterAppIndex(1)) // B
        assertEquals(3, model.resolveNearestLetterAppIndex(2)) // C
    }

    @Test
    fun resolveNearestLetterAppIndex_fallsBackToNearestPresentLetter() {
        val model = DrawerAlphaIndexModel.create(apps = apps, selectedIndex = 0)
        // E (4) has no app; nearest present scanning outward is C (2) -> app index 3
        assertEquals(3, model.resolveNearestLetterAppIndex(4))
        // Z (25) -> nearest present is still C -> 3
        assertEquals(3, model.resolveNearestLetterAppIndex(25))
    }

    @Test
    fun resolveNearestLetterAppIndex_emptyReturnsNull() {
        val model = DrawerAlphaIndexModel.create(apps = emptyList(), selectedIndex = 0)
        assertNull(model.resolveNearestLetterAppIndex(5))
        assertEquals(0, model.selectedLetterIndex)
    }

    @Test
    fun letterAt_mapsIndexToLetterAndClamps() {
        assertEquals('A', DrawerAlphaIndexModel.letterAt(0))
        assertEquals('Z', DrawerAlphaIndexModel.letterAt(25))
        assertEquals('Z', DrawerAlphaIndexModel.letterAt(99))
    }
}
