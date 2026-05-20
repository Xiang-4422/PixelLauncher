package com.purride.pixeldemo

import com.purride.pixeldemo.catalog.DemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogCoverageTest {

    @Test
    fun catalogContainsExpectedSectionCount() {
        assertEquals(
            "Expected 8 sections (foundation/interaction/scroll/theme/composition/extension/stress/integration)",
            8,
            DemoCatalog.sections.size,
        )
    }

    @Test
    fun catalogContainsExpectedSceneCount() {
        val total = DemoCatalog.sections.sumOf { it.scenes.size }
        assertEquals(
            "Expected 47 scenes total",
            47,
            total,
        )
    }

    @Test
    fun allSceneIdsAreUniqueAndNonEmpty() {
        val ids = DemoCatalog.sections.flatMap { it.scenes }.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals(
            "Scene ids are not unique: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}",
            ids.size,
            uniqueIds.size,
        )
        ids.forEach { id ->
            assertFalse("Scene id must not be blank", id.isBlank())
        }
    }

    @Test
    fun allScenesHaveNonEmptyTitlesAndDescriptions() {
        DemoCatalog.sections.flatMap { it.scenes }.forEach { scene ->
            assertFalse("Scene '${scene.id}' has blank title", scene.title.isBlank())
            assertFalse("Scene '${scene.id}' has blank description", scene.description.isBlank())
        }
    }

    @Test
    fun findByIdLocatesEveryRegisteredScene() {
        DemoCatalog.sections.flatMap { it.scenes }.forEach { scene ->
            val found = DemoCatalog.findById(scene.id)
            assertNotNull("findById('${scene.id}') returned null", found)
            assertEquals(scene.id, found!!.id)
        }
    }

    @Test
    fun findByIdReturnsNullForUnknownId() {
        val result = DemoCatalog.findById("__nonexistent_scene_id__")
        assertTrue("findById should return null for unknown id", result == null)
    }

    @Test
    fun sectionTitlesAreNonEmpty() {
        DemoCatalog.sections.forEach { section ->
            assertFalse("Section title must not be blank", section.title.isBlank())
        }
    }
}
