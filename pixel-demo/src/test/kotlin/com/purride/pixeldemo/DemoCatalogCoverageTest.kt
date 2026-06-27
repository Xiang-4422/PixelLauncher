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
        assertSearchContains("TextField", "text_text_field")
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
        assertSearchContains("KeyboardAvoidingView", "layout_ime_avoiding")
        assertSearchContains("Toast", "controls_dialog")
        assertSearchContains("ToastQueue", "controls_overlay_tools")
        assertSearchContains("LoadStateView", "controls_load_state_view")
        assertSearchContains("ListViewBuilder", "scroll_list_view")
        assertSearchContains("GridViewBuilder", "scroll_grid_view")
        assertSearchContains("PageViewBuilder", "scroll_page_view")
        assertSearchContains("SwipeRefreshScaffold", "scroll_swipe_refresh_scaffold")
        assertSearchContains("SliverAppBar", "scroll_custom_scroll_view")
        assertSearchContains("Sprite", "paint_image")
        assertSearchContains("AnimatedPadding", "animation_animated_container")
        assertSearchContains("AnimatedBuilder", "animation_tween_animation_builder")
        assertSearchContains("FocusNode", "nav_focus_scope")
        assertSearchContains("FormFieldState", "nav_form")
        assertSearchContains("PixelInspectorBoundsOverlay", "debug_inspector_panel")
        assertSearchContains("PixelResourceCache", "deep_resources_sprites")
        assertSearchContains("PixelNavigatorSnapshot", "deep_navigation_runtime")
        assertSearchContains("FormValidator", "nav_form")
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
                "layout_padding",
                "layout_align",
                "layout_sized_box",
                "layout_visibility",
                "layout_container",
                "layout_row",
                "layout_column",
                "layout_expanded",
                "layout_flexible",
                "layout_spacer",
                "layout_wrap",
                "layout_stack",
                "layout_positioned",
                "layout_opacity",
                "layout_clip_rect",
                "layout_transform_translate",
                "layout_decorated_box",
                "layout_aspect_ratio",
                "layout_constrained_box",
                "layout_fitted_box",
                "layout_safe_area",
                "layout_ime_avoiding",
                "layout_gesture_detector",
                "controls_divider",
                "controls_gap",
            ),
            columns[1].map { it.scene?.id },
        )
        assertEquals("layout_padding", columns[1].first().scene?.id)

        DemoTreeCatalog.categories.forEach { category ->
            assertTrue("${category.id} must contain leaf scenes", category.children.isNotEmpty())
            category.children.forEach { leaf ->
                assertTrue("${leaf.id} must be a leaf scene", leaf.isLeaf)
                assertNotNull("${leaf.id} must reference a scene", leaf.scene)
            }
        }
    }

    @Test
    fun leafOrderMatchesUiUxSections() {
        assertEquals(
            listOf(
                "layout_padding",
                "layout_align",
                "layout_sized_box",
                "layout_visibility",
                "layout_container",
                "layout_row",
                "layout_column",
                "layout_expanded",
                "layout_flexible",
                "layout_spacer",
                "layout_wrap",
                "layout_stack",
                "layout_positioned",
                "layout_opacity",
                "layout_clip_rect",
                "layout_transform_translate",
                "layout_decorated_box",
                "layout_aspect_ratio",
                "layout_constrained_box",
                "layout_fitted_box",
                "layout_safe_area",
                "layout_ime_avoiding",
                "layout_gesture_detector",
                "controls_divider",
                "controls_gap",
                "text_text",
                "text_rich_text",
                "text_text_field",
                "nav_focus_scope",
                "nav_form",
                "nav_semantics",
                "controls_outlined_button",
                "controls_list_tile",
                "controls_selection_list",
                "controls_section_list",
                "controls_value_adjuster",
                "controls_checkbox",
                "controls_tabs",
                "controls_slider",
                "controls_progress_bar",
                "controls_activity_indicator",
                "controls_load_state_view",
                "controls_badge",
                "controls_dialog",
                "controls_overlay_tools",
                "scroll_single_child_scroll_view",
                "scroll_list_view",
                "scroll_grid_view",
                "scroll_page_view",
                "scroll_scrollbar",
                "scroll_refresh_indicator",
                "scroll_swipe_refresh_scaffold",
                "scroll_custom_scroll_view",
                "deep_state_restoration",
                "controls_icon",
                "paint_line",
                "paint_circle",
                "paint_polygon",
                "paint_path",
                "paint_custom_paint",
                "paint_image",
                "deep_resources_sprites",
                "animation_animated_container",
                "animation_tween_animation_builder",
                "controls_app_scaffold",
                "deep_navigation_runtime",
                "debug_overlay",
                "debug_inspector_panel",
                "deep_inspector_advanced",
                "deep_performance_lab",
            ),
            DemoCatalog.allItems.map { it.id },
        )
    }

    private fun assertSearchContains(query: String, sceneId: String) {
        val ids = DemoCatalog.search(query).map { it.id }
        assertTrue("$query should find $sceneId, got $ids", sceneId in ids)
    }
}
