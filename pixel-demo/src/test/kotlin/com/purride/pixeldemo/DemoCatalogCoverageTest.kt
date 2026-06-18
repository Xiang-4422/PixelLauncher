package com.purride.pixeldemo

import com.purride.pixeldemo.catalog.DemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoCatalogCoverageTest {

    @Test
    fun catalogContainsExpectedComponentCategories() {
        assertEquals(
            listOf(
                "基础布局",
                "文本输入",
                "交互控件",
                "滚动分页",
                "绘制媒体",
                "动画状态",
                "导航宿主",
                "压力验证",
            ),
            DemoCatalog.categories.map { it.title },
        )
    }

    @Test
    fun allItemsHaveUniqueIdsAndRequiredMetadata() {
        val ids = DemoCatalog.allItems.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Duplicate ids: $duplicates", duplicates.isEmpty())

        DemoCatalog.allItems.forEach { item ->
            assertFalse("id must not be blank", item.id.isBlank())
            assertFalse("${item.id} title must not be blank", item.title.isBlank())
            assertFalse("${item.id} summary must not be blank", item.summary.isBlank())
            assertNotNull("${item.id} category must not be null", item.category)
            assertTrue("${item.id} must expose API tags", item.apis.isNotEmpty())
        }
    }

    @Test
    fun everyItemBelongsToRegisteredCategory() {
        val categoryIds = DemoCatalog.categories.map { it.id }.toSet()
        DemoCatalog.allItems.forEach { item ->
            assertTrue(
                "${item.id} has unknown category ${item.category?.id}",
                item.category?.id in categoryIds,
            )
        }
    }

    @Test
    fun findByIdLocatesEveryRegisteredItem() {
        DemoCatalog.allItems.forEach { item ->
            assertSame(item, DemoCatalog.findById(item.id))
        }
    }

    @Test
    fun findByIdReturnsNullForUnknownId() {
        assertNull(DemoCatalog.findById("__nonexistent_scene_id__"))
    }

    @Test
    fun searchMatchesTitleTagsAndApis() {
        assertEquals("components_text_input", DemoCatalog.search("TextField").first().id)
        assertEquals("components_scroll_paging", DemoCatalog.search("ListView").first().id)
        assertEquals("components_controls", DemoCatalog.search("Button").first().id)
        assertEquals("components_navigation_host", DemoCatalog.search("Navigator").first().id)
        assertEquals("components_paint_media", DemoCatalog.search("canvas").first().id)
    }

    @Test
    fun categoryFilteringAndAdjacentNavigationAreStable() {
        assertEquals(
            DemoCatalog.categories.size,
            DemoCatalog.allItems.size,
        )
        DemoCatalog.categories.forEach { category ->
            assertEquals(1, DemoCatalog.itemsFor(category).size)
            assertEquals(1, DemoCatalog.itemsFor(category.id).size)
        }

        val first = DemoCatalog.allItems.first()
        val second = DemoCatalog.allItems[1]
        val last = DemoCatalog.allItems.last()
        assertNull(DemoCatalog.previousItem(first.id))
        assertSame(second, DemoCatalog.nextItem(first.id))
        assertSame(first, DemoCatalog.previousItem(second.id))
        assertNull(DemoCatalog.nextItem(last.id))
    }
}
