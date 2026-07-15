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
                "主题",
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
        assertSearchContains("AdaptiveBuilder", "adaptive_localization")
        assertSearchContains("localization", "adaptive_localization")
        assertSearchContains("multi-stack", "adaptive_localization")
        assertSearchContains("Toast", "feedback_dialog")
        assertSearchContains("ToastQueue", "feedback_overlay_tools")
        assertSearchContains("PixelThemeTokens", "theme_showcase")
        assertSearchContains("high-contrast", "theme_showcase")
        assertSearchContains("PixelPopupRoute", "feedback_overlay_tools")
        assertSearchContains("PixelOverlayDismissPolicy", "feedback_overlay_tools")
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
        assertSearchContains("PixelMotionTheme", "animation_motion_showcase")
        assertSearchContains("reduce-motion", "animation_motion_showcase")
        assertSearchContains("Slidable", "animation_motion_showcase")
        assertSearchContains("FocusNode", "input_focus_scope")
        assertSearchContains("FocusTraversalGroup", "input_focus_scope")
        assertSearchContains("FormFieldState", "input_form")
        assertSearchContains("PixelInspectorBoundsOverlay", "debug_inspector_panel")
        assertSearchContains("PixelResourceCache", "deep_resources_sprites")
        assertSearchContains("PixelNavigatorSnapshot", "deep_navigation_runtime")
        assertSearchContains("FormValidator", "input_form")
        assertSearchContains("TalkBack", "input_accessibility_flow")
        assertSearchContains("PixelInspectorPanel", "debug_inspector_panel")
        assertSearchContains("PixelLeafRenderObjectWidget", "deep_inspector_advanced")
        assertSearchContains("PixelPagerSavedState", "deep_state_restoration")
    }

    /** Accessibility showcase remains discoverable with every API required by its core flow. */
    @Test
    fun accessibilityFlowSceneIsRegisteredWithEndToEndApis() {
        val scene = DemoCatalog.findById("input_accessibility_flow")
        assertNotNull(scene)
        assertEquals(DemoCatalog.input.id, scene?.category?.id)
        assertTrue(scene?.tags?.contains("accessibility") == true)
        assertTrue(
            scene?.apis?.containsAll(
                setOf("TextField", "ListViewBuilder", "Slider", "Dropdown", "PixelOverlayHost"),
            ) == true,
        )
    }

    /** Production-overlay scene remains discoverable with route lifecycle and both FIFO lanes. */
    @Test
    fun productionOverlaySceneExposesM43Apis() {
        /** Registered scene whose metadata drives catalog search and API documentation links. */
        val scene = requireNotNull(DemoCatalog.findById("feedback_overlay_tools"))

        assertEquals("ProductionOverlay", scene.title)
        assertTrue("typed-result" in scene.tags)
        assertTrue(
            scene.apis.containsAll(
                setOf(
                    "PixelOverlayHost",
                    "PixelPopupRoute",
                    "PixelOverlayEntry",
                    "PixelOverlayOutcome",
                    "PixelOverlayLayer",
                    "PixelOverlayBarrier",
                    "PixelOverlayDismissPolicy",
                    "ToastQueue",
                    "SnackbarQueue",
                ),
            ),
        )
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
                "theme_tokens",
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

    /** 验证独立 Motion showcase 可从动效分组发现且暴露关键交互 API。 */
    @Test
    fun motionShowcaseIsDiscoverableFromAnimationGroup() {
        val motionScene = requireNotNull(DemoCatalog.findById("animation_motion_showcase"))
        val animationGroup = DemoCatalog.groups.first { it.id == "animation_widgets" }

        assertEquals(DemoCatalog.animation, motionScene.category)
        assertTrue("Motion scene must belong to animation_widgets", motionScene.id in animationGroup.sceneIds)
        assertTrue("Motion scene must expose PixelMotionScope", "PixelMotionScope" in motionScene.apis)
        assertTrue("Motion scene must expose Popover", "Popover" in motionScene.apis)
        assertTrue("Motion scene must expose Slidable", "Slidable" in motionScene.apis)
    }

    /** Integrated adaptive/localization scene remains discoverable with its retained Host flows. */
    @Test
    fun adaptiveLocalizationSceneExposesM53IntegrationApis() {
        /** Registered scene used by the M5-3F interactive acceptance path. */
        val scene = requireNotNull(DemoCatalog.findById("adaptive_localization"))
        /** Layout system group that owns SafeArea, IME and adaptive viewport examples. */
        val layoutSystemGroup = DemoCatalog.groups.first { it.id == "layout_system" }

        assertEquals(DemoCatalog.layout, scene.category)
        assertTrue(scene.id in layoutSystemGroup.sceneIds)
        assertTrue(
            scene.apis.containsAll(
                setOf(
                    "AdaptiveBuilder",
                    "PixelLocalizationProvider",
                    "TextField",
                    "SafeArea",
                    "PixelMultiStackNavigator",
                    "NavigationBar",
                ),
            ),
        )
    }

    private fun assertSearchContains(query: String, sceneId: String) {
        val ids = DemoCatalog.search(query).map { it.id }
        assertTrue("$query should find $sceneId, got $ids", sceneId in ids)
    }
}
