package com.purride.pixeldemo

import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoTreeCatalog
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
                "布局",
                "文本",
                "输入",
                "控件",
                "反馈",
                "滚动",
                "绘制",
                "动效",
                "导航",
                "调试",
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
        assertSearchContains("TextField", "input_text_field")
        assertSearchContains("ListView", "scroll_list_view")
        assertSearchContains("Button", "controls_outlined_button")
        assertSearchContains("Navigator", "deep_navigation_runtime")
        assertSearchContains("canvas", "paint_custom_paint")
        assertSearchContains("AnimatedSprite", "deep_resources_sprites")
        assertSearchContains("PaddingDirectional", "layout_padding")
        assertSearchContains("Center", "layout_align")
        assertSearchContains("Visibility", "layout_visibility")
        assertSearchContains("ContainerDirectional", "layout_container")
        assertSearchContains("PositionedFill", "layout_positioned")
        assertSearchContains("OptionList", "controls_selection_list")
        assertSearchContains("SectionList", "controls_section_list")
        assertSearchContains("Stepper", "controls_value_adjuster")
        assertSearchContains("Switch", "controls_checkbox")
        assertSearchContains("SegmentedControl", "controls_tabs")
        assertSearchContains("ShortcutHint", "controls_shortcut_hint")
        assertSearchContains("KeyboardAvoidingView", "layout_ime_avoiding")
        assertSearchContains("Toast", "feedback_dialog")
        assertSearchContains("ToastQueue", "feedback_overlay_tools")
        assertSearchContains("Dropdown", "feedback_popover_menu")
        assertSearchContains("Tooltip", "feedback_popover_menu")
        assertSearchContains("LoadStateView", "feedback_load_state_view")
        assertSearchContains("ListViewBuilder", "scroll_list_view")
        assertSearchContains("GridViewBuilder", "scroll_grid_view")
        assertSearchContains("PageViewBuilder", "scroll_page_view")
        assertSearchContains("SwipeRefreshScaffold", "scroll_swipe_refresh_scaffold")
        assertSearchContains("SliverAppBar", "scroll_custom_scroll_view")
        assertSearchContains("Sprite", "paint_image")
        assertSearchContains("AnimatedPadding", "animation_animated_container")
        assertSearchContains("AnimatedVisibility", "animation_animated_container")
        assertSearchContains("AnimatedBuilder", "animation_tween_animation_builder")
        assertSearchContains("FocusNode", "input_focus_scope")
        assertSearchContains("FocusTraversalGroup", "input_focus_scope")
        assertSearchContains("FormFieldState", "input_form")
        assertSearchContains("PixelInspectorBoundsOverlay", "debug_inspector_panel")
        assertSearchContains("PixelResourceCache", "deep_resources_sprites")
        assertSearchContains("PixelNavigatorSnapshot", "deep_navigation_runtime")
        assertSearchContains("FormValidator", "input_form")
        assertSearchContains("PixelInspectorPanel", "debug_inspector_panel")
        assertSearchContains("PixelLeafRenderObjectWidget", "deep_inspector_advanced")
        assertSearchContains("PixelPagerSavedState", "deep_state_restoration")
    }

    @Test
    fun categoryFilteringAndAdjacentNavigationAreStable() {
        assertTrue(DemoCatalog.allItems.size > DemoCatalog.categories.size)
        DemoCatalog.categories.forEach { category ->
            val items = DemoCatalog.itemsFor(category)
            assertTrue("${category.id} must contain at least one scene", items.isNotEmpty())
            assertEquals(items, DemoCatalog.itemsFor(category.id))
        }

        val first = DemoCatalog.allItems.first()
        val second = DemoCatalog.allItems[1]
        val last = DemoCatalog.allItems.last()
        assertNull(DemoCatalog.previousItem(first.id))
        assertSame(second, DemoCatalog.nextItem(first.id))
        assertSame(first, DemoCatalog.previousItem(second.id))
        assertNull(DemoCatalog.nextItem(last.id))
    }

    @Test
    fun demoTreeCatalogMirrorsUiUxCatalogOrder() {
        assertEquals(DemoCatalog.categories.map { it.id }, DemoTreeCatalog.categories.map { it.id })
        assertEquals(DemoCatalog.allItems, DemoTreeCatalog.leafScenes)
        assertEquals(listOf(DemoCatalog.categories.first().id), DemoTreeCatalog.defaultSelectedPath())

        val columns = DemoTreeCatalog.visibleColumns(DemoTreeCatalog.defaultSelectedPath())
        assertEquals(DemoCatalog.categories.size, columns.first().size)
        assertEquals(
            listOf(
                "layout_box",
                "layout_flex",
                "layout_stack",
                "layout_system",
            ),
            columns[1].map { it.id },
        )
        assertEquals(
            listOf("layout_padding", "layout_align", "layout_sized_box", "layout_visibility", "layout_container"),
            DemoTreeCatalog.visibleColumns(listOf("layout", "layout_box"))[2].take(5).map { it.scene?.id },
        )

        DemoTreeCatalog.categories.forEach { category ->
            assertTrue("${category.id} must contain groups", category.children.isNotEmpty())
            category.children.forEach { group ->
                assertFalse("${group.id} must be a group", group.isLeaf)
                assertTrue("${group.id} must contain leaf scenes", group.children.isNotEmpty())
                group.children.forEach { leaf ->
                    assertTrue("${leaf.id} must be a leaf scene", leaf.isLeaf)
                    assertNotNull("${leaf.id} must reference a scene", leaf.scene)
                }
            }
        }
    }

    @Test
    fun groupOrderMatchesUiUxSections() {
        assertEquals(
            listOf(
                "layout_box",
                "layout_flex",
                "layout_stack",
                "layout_system",
                "text_rendering",
                "input_text",
                "input_focus",
                "controls_actions",
                "controls_selection",
                "controls_value",
                "feedback_status",
                "feedback_overlay",
                "scroll_views",
                "scroll_refresh",
                "paint_primitives",
                "paint_resources",
                "animation_widgets",
                "navigation_runtime",
                "debug_tools",
            ),
            DemoCatalog.groups.map { it.id },
        )
        assertEquals(
            listOf(
                "layout_padding",
                "layout_align",
                "layout_sized_box",
                "layout_visibility",
                "layout_container",
                "layout_decorated_box",
                "layout_aspect_ratio",
                "layout_constrained_box",
                "layout_fitted_box",
                "layout_divider",
                "layout_gap",
            ),
            DemoCatalog.groups.first { it.id == "layout_box" }.sceneIds,
        )
        assertEquals(DemoCatalog.groups.flatMap { it.sceneIds }, DemoCatalog.allItems.map { it.id })
    }

    private fun assertSearchContains(query: String, sceneId: String) {
        val ids = DemoCatalog.search(query).map { it.id }
        assertTrue("$query should find $sceneId, got $ids", sceneId in ids)
    }
}
