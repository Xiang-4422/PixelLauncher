package com.purride.pixeldemo.showcase

import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelThemeBrightness
import com.purride.pixelui.PixelThemeContrast
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.testing.PixelSemanticsActionArguments
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import com.purride.pixeldemo.catalog.DemoCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5-2 Theme Showcase 的 catalog、生产组件、状态矩阵与键盘切换覆盖。 */
class ThemeShowcaseSceneTest {
    /** 五个主题预设保持完整、顺序稳定，并覆盖明暗与高对比度组合。 */
    @Test
    fun exposesFiveRequiredThemePresets() {
        /** 主题按钮、测试键和文档共同使用的稳定预设顺序。 */
        val presetIds = ThemeShowcasePresets.map { preset -> preset.id }

        assertEquals(
            listOf("light", "dark", "high-contrast-dark", "high-contrast-light", "custom"),
            presetIds,
        )
        assertEquals(PixelThemeBrightness.Light, ThemeShowcasePresets[0].tokens.brightness)
        assertEquals(PixelThemeBrightness.Dark, ThemeShowcasePresets[1].tokens.brightness)
        assertEquals(PixelThemeContrast.High, ThemeShowcasePresets[2].tokens.contrast)
        assertEquals(PixelThemeContrast.High, ThemeShowcasePresets[3].tokens.contrast)
        assertNotEquals(PixelThemeTokens.Dark.colors, ThemeShowcasePresets[4].tokens.colors)
        assertNotEquals(PixelThemeTokens.Dark.spacing, ThemeShowcasePresets[4].tokens.spacing)
        assertNotEquals(PixelThemeTokens.Dark.motion, ThemeShowcasePresets[4].tokens.motion)
    }

    /** 基础 token 样本逐项覆盖 colors 到 labels 的九个完整分组。 */
    @Test
    fun foundationSamplesCoverEveryTokenProperty() {
        /** Light 主题的完整基础 token 样本结构。 */
        val groups = themeShowcaseFoundationSamples(PixelThemeTokens.Light)
        /** 每个基础分组应展示的公共构造属性数量。 */
        val expectedCounts = linkedMapOf(
            "colors" to 22,
            "typography" to 6,
            "spacing" to 6,
            "sizes" to 10,
            "radii" to 5,
            "borders" to 4,
            "elevations" to 4,
            "motion" to 8,
            "labels" to 29,
        )

        assertEquals(expectedCounts.keys.toList(), groups.map { group -> group.id })
        assertEquals(expectedCounts, groups.associate { group -> group.id to group.samples.size })
        groups.forEach { group ->
            /** 同一分组中的属性名必须唯一，避免样本重复掩盖遗漏。 */
            val sampleNames = group.samples.map { sample -> sample.name }
            assertEquals("Duplicate token in ${group.id}", sampleNames.size, sampleNames.toSet().size)
            assertTrue("Blank value in ${group.id}", group.samples.all { sample -> sample.value.isNotBlank() })
        }
    }

    /** 组件清单和状态清单形成完整且无重复的 25×8 笛卡尔积。 */
    @Test
    fun componentMatrixContainsEveryFamilyAndState() {
        /** PixelComponentTokens 构造器中的全部标准组件族。 */
        val expectedFamilyIds = listOf(
            "button",
            "textButton",
            "iconButton",
            "textField",
            "listTile",
            "checkbox",
            "radio",
            "switch",
            "slider",
            "tabs",
            "segmented",
            "navigationBar",
            "navigationRail",
            "valueAdjuster",
            "menu",
            "dropdown",
            "slidable",
            "dialog",
            "bottomSheet",
            "toast",
            "snackbar",
            "tooltip",
            "progress",
            "refresh",
            "scrollbar",
        )
        /** M5-1 要求的八个标准组件状态。 */
        val expectedStateIds = listOf(
            "normal",
            "hovered",
            "pressed",
            "focused",
            "selected",
            "disabled",
            "error",
            "loading",
        )
        /** 全部交叉单元的稳定测试键。 */
        val matrixKeys = ThemeShowcaseComponentFamilies.flatMap { family ->
            ThemeShowcaseStates.map { state -> themeShowcaseMatrixKey(family.id, state.id) }
        }

        assertEquals(expectedFamilyIds, ThemeShowcaseComponentFamilies.map { family -> family.id })
        assertEquals(expectedStateIds, ThemeShowcaseStates.map { state -> state.id })
        assertEquals(25 * 8, matrixKeys.size)
        assertEquals(matrixKeys.size, matrixKeys.toSet().size)
    }

    /** 生产画廊注册表精确覆盖 25 个公开工厂，且标识、工厂名和测试键均无重复。 */
    @Test
    fun productionGalleryRegistryIsExactAndUnique() {
        /** 需求指定的公开生产工厂顺序。 */
        val expectedFactories = listOf(
            "OutlinedButton",
            "TextButton",
            "IconButton",
            "TextField",
            "ListTile",
            "Checkbox",
            "Radio",
            "Switch",
            "Slider",
            "Tabs",
            "SegmentedControl",
            "NavigationBar",
            "NavigationRail",
            "ValueAdjuster",
            "Menu",
            "Dropdown",
            "Slidable",
            "Dialog",
            "BottomSheet",
            "Toast",
            "Snackbar",
            "Tooltip",
            "ProgressBar",
            "RefreshIndicator",
            "Scrollbar",
        )
        /** 生产注册项按 token 图谱顺序导出的稳定标识。 */
        val ids = ThemeShowcaseProductionComponents.map { sample -> sample.id }
        /** 每个生产注册项声明的真实公开工厂名。 */
        val factories = ThemeShowcaseProductionComponents.map { sample -> sample.factoryName }
        /** 由生产注册项生成的全部稳定测试键。 */
        val keys = ids.map(::themeShowcaseProductionKey)

        assertEquals(25, ThemeShowcaseProductionComponents.size)
        assertEquals(ThemeShowcaseComponentFamilies.map { family -> family.id }, ids)
        assertEquals(expectedFactories, factories)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(factories.size, factories.toSet().size)
        assertEquals(keys.size, keys.toSet().size)
    }

    /** 完整展厅会真实挂载 25 个公开组件 identity，并由每个组件导出生产语义。 */
    @Test
    fun productionGalleryBuildsEveryRealKeyAndSemanticLabel() {
        /** 足够容纳完整生产画廊的离屏 Host。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = themeShowcaseBodyForTest(key = "theme-showcase-production-root"),
                logicalWidth = 240,
                logicalHeight = 1_900,
            )

            ThemeShowcaseProductionComponents.forEach { sample ->
                assertTrue(
                    "${sample.factoryName} did not mount its real public key",
                    tester.exists(find.byKey(themeShowcaseProductionKey(sample.id))),
                )
                assertTrue(
                    "${sample.factoryName} exported no production semantics",
                    tester.semanticsNodesByLabel(sample.semanticLabel).isNotEmpty(),
                )
            }
        } finally {
            tester.dispose()
        }
    }

    /** 新增选择、图标、导航和表单装饰均来自可交互的真实公开工厂。 */
    @Test
    fun newProductionControlsAreInteractiveAndDecoratedFieldRemainsEditable() {
        /** 足够容纳 25 项生产画廊并执行真实语义动作的离屏 Host。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = themeShowcaseBodyForTest(key = "theme-showcase-m5-2-root"),
                logicalWidth = 240,
                logicalHeight = 2_200,
            )

            /** 可编辑 TextField 上合并 FormFieldDecoration 的唯一语义节点。 */
            val decoratedField = tester.semanticsNodesByLabel("GALLERY TEXT FIELD *").single()
            assertTrue(tester.exists(find.byText("PROFILE NAME *")))
            assertTrue(tester.exists(find.byText("EDITABLE PUBLIC FIELD")))
            assertTrue(tester.exists(find.byText("5/24")))
            assertTrue(
                tester.performSemanticsAction(
                    decoratedField.id,
                    PixelSemanticsAction.SET_TEXT,
                    PixelSemanticsActionArguments(text = "PIXELS"),
                ),
            )
            tester.pumpFrame(deltaMs = 0)
            assertEquals(
                "PIXELS",
                tester.semanticsNodesByLabel("GALLERY TEXT FIELD *").single().value,
            )
            assertTrue(tester.exists(find.byText("6/24")))

            /** IconButton 的合并语义边界必须直接执行公开点击动作。 */
            val iconButton = tester.semanticsNodesByLabel("GALLERY ICON BUTTON").single()
            assertTrue(tester.performSemanticsAction(iconButton.id, PixelSemanticsAction.CLICK))

            /** Radio 的受控选择请求应回写并重建为 selected/checked。 */
            val radio = tester.semanticsNodesByLabel("GALLERY RADIO").single()
            assertEquals(false, radio.selected)
            assertTrue(tester.performSemanticsAction(radio.id, PixelSemanticsAction.CLICK))
            assertEquals(true, tester.semanticsNodesByLabel("GALLERY RADIO").single().selected)

            /** NavigationBar 集合节点用于定位其重名 SEARCH 目的地。 */
            val navigationBar =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION BAR").single()
            /** Bar 内由稳定业务 id 驱动的 SEARCH 目的地。 */
            val barSearch = tester.semanticsNodesByLabel("SEARCH").single { node ->
                node.parentId == navigationBar.id
            }
            assertTrue(tester.performSemanticsAction(barSearch.id, PixelSemanticsAction.CLICK))
            /** 受控更新后的 Bar 集合节点。 */
            val updatedNavigationBar =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION BAR").single()
            assertEquals(
                true,
                tester.semanticsNodesByLabel("SEARCH").single { node ->
                    node.parentId == updatedNavigationBar.id
                }.selected,
            )

            /** NavigationRail 集合节点用于定位其 SETTINGS 目的地。 */
            val navigationRail =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION RAIL").single()
            /** Rail 内由稳定业务 id 驱动的 SETTINGS 目的地。 */
            val railSettings = tester.semanticsNodesByLabel("SETTINGS").single { node ->
                node.parentId == navigationRail.id
            }
            assertTrue(tester.performSemanticsAction(railSettings.id, PixelSemanticsAction.CLICK))
            /** 受控更新后的 Rail 集合节点。 */
            val updatedNavigationRail =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION RAIL").single()
            assertEquals(
                true,
                tester.semanticsNodesByLabel("SETTINGS").single { node ->
                    node.parentId == updatedNavigationRail.id
                }.selected,
            )

            /** 使用真实主题按钮触发完整 token 图谱重建。 */
            val darkButton = tester.semanticsNodesByLabel("DARK").single { node -> node.enabled }
            assertTrue(tester.performSemanticsAction(darkButton.id, PixelSemanticsAction.CLICK))
            assertEquals("dark", tester.semanticsNodesByLabel("Active theme").single().value)
            assertEquals(
                "PIXELS",
                tester.semanticsNodesByLabel("GALLERY TEXT FIELD *").single().value,
            )
            assertTrue(tester.exists(find.byText("6/24")))
            assertEquals(true, tester.semanticsNodesByLabel("GALLERY RADIO").single().selected)
            /** Dark 主题下保留受控 SEARCH 选择的 Bar 集合。 */
            val darkNavigationBar =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION BAR").single()
            assertEquals(
                true,
                tester.semanticsNodesByLabel("SEARCH").single { node ->
                    node.parentId == darkNavigationBar.id
                }.selected,
            )
            /** Dark 主题下保留受控 SETTINGS 选择的 Rail 集合。 */
            val darkNavigationRail =
                tester.semanticsNodesByLabel("GALLERY NAVIGATION RAIL").single()
            assertEquals(
                true,
                tester.semanticsNodesByLabel("SETTINGS").single { node ->
                    node.parentId == darkNavigationRail.id
                }.selected,
            )
        } finally {
            tester.dispose()
        }
    }

    /** Checkbox、Switch、Slider、Tabs 和 Dropdown 的公开交互值均跨主题保留。 */
    @Test
    fun productionComponentStatePersistsAcrossThemeSwitch() {
        /** 覆盖主题按钮、五个受控组件和 Dropdown 弹层的 retained 离屏 Host。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = themeShowcaseBodyForTest(key = "theme-showcase-persistence-root"),
                logicalWidth = 240,
                logicalHeight = 900,
            )

            /** 切换主题前尚未勾选的真实 Checkbox 语义节点。 */
            val unchecked = tester.semanticsNodesByLabel("GALLERY CHECKBOX").single()
            assertEquals(false, unchecked.checked)
            assertTrue(tester.performSemanticsAction(unchecked.id, PixelSemanticsAction.CLICK))
            assertEquals(
                true,
                tester.semanticsNodesByLabel("GALLERY CHECKBOX").single().checked,
            )

            /** 切换主题前默认开启的真实 Switch 语义节点。 */
            val enabledSwitch = tester.semanticsNodesByLabel("GALLERY SWITCH").single()
            assertEquals(true, enabledSwitch.checked)
            assertTrue(tester.performSemanticsAction(enabledSwitch.id, PixelSemanticsAction.CLICK))
            assertEquals(
                false,
                tester.semanticsNodesByLabel("GALLERY SWITCH").single().checked,
            )

            /** 通过公开 SET_PROGRESS 语义动作提交给真实 Slider 的目标值。 */
            val requestedSliderValue = 0.78f
            /** 切换主题前导出初始结构化范围的真实 Slider 节点。 */
            val initialSlider = tester.semanticsNodesByLabel("GALLERY SLIDER").single()
            assertEquals(0.42f, initialSlider.rangeInfo?.current ?: -1f, 0.001f)
            assertTrue(
                tester.performSemanticsAction(
                    initialSlider.id,
                    PixelSemanticsAction.SET_PROGRESS,
                    PixelSemanticsActionArguments(progress = requestedSliderValue),
                ),
            )
            assertEquals(
                requestedSliderValue,
                tester.semanticsNodesByLabel("GALLERY SLIDER").single().rangeInfo?.current ?: -1f,
                0.001f,
            )

            /** 切换主题前未选中的真实 Tabs 第二项语义节点。 */
            val secondTab = tester.semanticsNodesByLabel("GALLERY TAB B").single()
            assertEquals(false, secondTab.selected)
            assertTrue(tester.performSemanticsAction(secondTab.id, PixelSemanticsAction.CLICK))
            assertEquals(
                true,
                tester.semanticsNodesByLabel("GALLERY TAB B").single().selected,
            )

            /** 切换主题前折叠且值为 A 的真实 Dropdown 锚点。 */
            val collapsedDropdown = tester.semanticsNodesByLabel("GALLERY DROPDOWN").single()
            assertEquals("A", collapsedDropdown.value)
            assertEquals(false, collapsedDropdown.expanded)
            assertTrue(
                tester.performSemanticsAction(collapsedDropdown.id, PixelSemanticsAction.EXPAND),
            )
            tester.pumpFrame(deltaMs = 0)
            tester.pumpFrame(deltaMs = 1_000)
            /** 展开弹层后由真实 MenuItem 导出的 B 选项。 */
            val dropdownOptionB = tester.semanticsNodesByLabel("B").single { node -> node.enabled }
            assertEquals(false, dropdownOptionB.selected)
            assertTrue(tester.performSemanticsAction(dropdownOptionB.id, PixelSemanticsAction.CLICK))
            tester.pumpFrame(deltaMs = 1_000)
            /** 选择 B 后已关闭并更新公开语义值的 Dropdown 锚点。 */
            val dropdownBeforeThemeSwitch =
                tester.semanticsNodesByLabel("GALLERY DROPDOWN").single()
            assertEquals("B", dropdownBeforeThemeSwitch.value)
            assertEquals(false, dropdownBeforeThemeSwitch.expanded)

            /** 通过真实主题按钮切换到完整 Dark token 图谱。 */
            val darkButton = tester.semanticsNodesByLabel("DARK").single { node -> node.enabled }
            assertTrue(tester.performSemanticsAction(darkButton.id, PixelSemanticsAction.CLICK))

            assertEquals("dark", tester.semanticsNodesByLabel("Active theme").single().value)
            assertEquals(
                true,
                tester.semanticsNodesByLabel("GALLERY CHECKBOX").single().checked,
            )
            assertEquals(
                false,
                tester.semanticsNodesByLabel("GALLERY SWITCH").single().checked,
            )
            assertEquals(
                requestedSliderValue,
                tester.semanticsNodesByLabel("GALLERY SLIDER").single().rangeInfo?.current ?: -1f,
                0.001f,
            )
            assertEquals(
                true,
                tester.semanticsNodesByLabel("GALLERY TAB B").single().selected,
            )
            /** 切换 Dark 后仍导出 B 和折叠状态的 Dropdown 锚点。 */
            val dropdownAfterThemeSwitch =
                tester.semanticsNodesByLabel("GALLERY DROPDOWN").single()
            assertEquals("B", dropdownAfterThemeSwitch.value)
            assertEquals(false, dropdownAfterThemeSwitch.expanded)
            assertTrue(
                tester.performSemanticsAction(
                    dropdownAfterThemeSwitch.id,
                    PixelSemanticsAction.EXPAND,
                ),
            )
            tester.pumpFrame(deltaMs = 0)
            tester.pumpFrame(deltaMs = 1_000)
            /** Dark 主题下重新展开后仍保持选中的公开 B 选项。 */
            val selectedDropdownOptionB =
                tester.semanticsNodesByLabel("B").single { node -> node.enabled }
            assertEquals(true, selectedDropdownOptionB.selected)
            assertTrue(tester.exists(find.byKey(themeShowcaseProductionKey("checkbox"))))
            assertTrue(tester.exists(find.byKey(themeShowcaseProductionKey("switch"))))
            assertTrue(tester.exists(find.byKey(themeShowcaseProductionKey("slider"))))
            assertTrue(tester.exists(find.byKey(themeShowcaseProductionKey("tabs"))))
            assertTrue(tester.exists(find.byKey(themeShowcaseProductionKey("dropdown"))))
        } finally {
            tester.dispose()
        }
    }

    /** Theme Showcase 可从独立主题分类、分组和 catalog 搜索发现。 */
    @Test
    fun sceneIsDiscoverableFromThemeCatalog() {
        /** Catalog 中注册的主题展厅场景。 */
        val scene = DemoCatalog.findById("theme_showcase")
        /** 独立主题分组。 */
        val group = DemoCatalog.groups.first { candidate -> candidate.id == "theme_tokens" }

        assertNotNull(scene)
        assertEquals(DemoCatalog.theme, scene?.category)
        assertTrue("theme_showcase" in group.sceneIds)
        assertTrue(scene?.apis?.contains("PixelThemeTokens") == true)
        assertTrue(
            scene?.apis?.containsAll(ThemeShowcaseComponentFamilies.map { family -> family.title }) == true,
        )
        assertTrue("theme_showcase" in DemoCatalog.search("high-contrast").map { item -> item.id })
    }

    /** 真实场景用 Tab/Enter 切换主题，并保留首尾状态矩阵节点。 */
    @Test
    fun keyboardSwitchesThemeAndBuildsCompleteMatrix() {
        /** 离屏 Host，直接运行 catalog 使用的同一 retained 展厅主体。 */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = themeShowcaseBodyForTest(key = "theme-showcase-test-root"),
                logicalWidth = 220,
                logicalHeight = 240,
            )

            assertEquals("light", tester.semanticsNodesByLabel("Active theme").single().value)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("LIGHT").single().focused)
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(tester.semanticsNodesByLabel("DARK").single().focused)
            assertTrue(tester.pressKey(PixelKey.ENTER))
            assertEquals("dark", tester.semanticsNodesByLabel("Active theme").single().value)

            assertTrue(tester.exists(find.byKey(themeShowcaseMatrixKey("button", "normal"))))
            assertTrue(tester.exists(find.byKey(themeShowcaseMatrixKey("scrollbar", "loading"))))

            /** CUSTOM 主题按钮的当前可执行语义节点。 */
            val customButton = tester.semanticsNodesByLabel("CUSTOM").single { node -> node.enabled }
            assertTrue(tester.performSemanticsAction(customButton.id, PixelSemanticsAction.CLICK))
            assertEquals("custom", tester.semanticsNodesByLabel("Active theme").single().value)
            assertTrue(tester.exists(find.byKey(ThemeShowcaseActivePresetKey)))
        } finally {
            tester.dispose()
        }
    }
}
