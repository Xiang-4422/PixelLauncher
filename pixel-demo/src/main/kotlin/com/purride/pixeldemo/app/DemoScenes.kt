package com.purride.pixeldemo.app

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelButton
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelCrossAxisAlignment
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelList
import com.purride.pixelui.PixelMainAxisAlignment
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelRow
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelText
import com.purride.pixelui.PixelTextField
import com.purride.pixelui.PixelTextFieldStyle
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.PixelSingleChildScrollView
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.fillMaxHeight
import com.purride.pixelui.fillMaxWidth
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.size
import com.purride.pixelui.weight
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController

/**
 * Demo scene 定义集合。
 *
 * 每个 scene 都通过 `pixel-ui` 的组件树搭建，避免再退回手写 renderer。
 */
object DemoScenes {

    data class DemoScene(
        val initialProfile: ScreenProfile,
        val initialPalette: PixelPalette,
        val initialTextRasterizer: PixelTextRasterizer,
        val content: () -> com.purride.pixelui.PixelNode,
    )

    fun create(
        sceneKind: DemoSceneKind,
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
        applyPreferredProfile: (ScreenProfile) -> Unit,
    ): DemoScene {
        return when (sceneKind) {
            DemoSceneKind.TEXT -> textScene(textRasterizers)
            DemoSceneKind.PALETTE -> paletteScene(hostView, textRasterizers, applyPreferredProfile)
            DemoSceneKind.TEXT_FIELD -> textFieldScene(hostView, textRasterizers)
            DemoSceneKind.SINGLE_CHILD_SCROLL -> singleChildScrollScene(hostView, textRasterizers)
            DemoSceneKind.HORIZONTAL_PAGER -> horizontalPagerScene(hostView, textRasterizers)
            DemoSceneKind.VERTICAL_PAGER -> verticalPagerScene(hostView, textRasterizers)
            DemoSceneKind.LIST -> listScene(hostView, textRasterizers)
            DemoSceneKind.FORM_AND_LIST -> formAndListScene(hostView, textRasterizers)
            DemoSceneKind.PAGER_AND_LIST -> pagerAndListScene(hostView, textRasterizers)
            DemoSceneKind.LAYOUT_AND_CLICK -> layoutAndClickScene(hostView, textRasterizers)
        }
    }

    private fun textScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = PixelListController()
        val scrollState = scrollController.create()
        val compactRasterizer = PixelBitmapFont(
            glyphWidth = 4,
            glyphHeight = 5,
            letterSpacing = 1,
            lineSpacing = 1,
        )
        val wideRasterizer = textRasterizers.emphasis
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.terminalGreen(),
            initialTextRasterizer = textRasterizers.default,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("文本与字体", textRasterizer = wideRasterizer),
                        infoCard("中文", "像素中文已经接通"),
                        infoCard("混排", "HELLO 你好 UI", valueRasterizer = wideRasterizer),
                        infoCard("紧凑", "COMPACT 4X5", valueRasterizer = compactRasterizer),
                        infoCard("复用", "复用 Launcher 的字形包"),
                    ),
                )
            },
        )
    }

    private fun paletteScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
        applyPreferredProfile: (ScreenProfile) -> Unit,
    ): DemoScene {
        val scrollController = PixelListController()
        val scrollState = scrollController.create()
        val themes = PixelTheme.entries
        val shapes = PixelShape.entries
        var currentThemeIndex = 0
        var currentShapeIndex = 0

        fun applyHostAppearance() {
            hostView.setPalette(PixelPalette.fromTheme(themes[currentThemeIndex]))
            applyPreferredProfile(defaultProfile(pixelShape = shapes[currentShapeIndex]))
        }

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(themes[currentThemeIndex]),
            initialTextRasterizer = textRasterizers.default,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("PALETTE AND SHAPE"),
                        infoCard("THEME", themes[currentThemeIndex].name),
                        infoCard("SHAPE", shapes[currentShapeIndex].name),
                        PixelButton(
                            text = "NEXT THEME",
                            onClick = {
                            currentThemeIndex = (currentThemeIndex + 1) % themes.size
                            applyHostAppearance()
                            hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        PixelButton(
                            text = "NEXT SHAPE",
                            onClick = {
                            currentShapeIndex = (currentShapeIndex + 1) % shapes.size
                            applyHostAppearance()
                            hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        PixelRow(
                            spacing = 4,
                            children = listOf(
                                swatch(PixelTone.ON, "ON"),
                                swatch(PixelTone.ACCENT, "ACCENT"),
                                swatch(PixelTone.OFF, "OFF"),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private fun textFieldScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = PixelListController()
        val scrollState = scrollController.create()
        val controller = PixelTextFieldController()
        val primaryState = controller.create(initialText = "PIXEL")
        val secondaryState = controller.create()

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("TEXT FIELD"),
                        infoCard("PRIMARY", primaryState.text.ifEmpty { "(EMPTY)" }, accent = primaryState.isFocused),
                        infoCard("SECONDARY", secondaryState.text.ifEmpty { "(EMPTY)" }, accent = secondaryState.isFocused),
                        PixelTextField(
                            state = primaryState,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                            placeholder = "TYPE PRIMARY",
                            style = PixelTextFieldStyle.Default,
                        ),
                        PixelTextField(
                            state = secondaryState,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                            placeholder = "TYPE SECONDARY",
                            style = PixelTextFieldStyle(
                                borderTone = PixelTone.ON,
                                focusedBorderTone = PixelTone.ACCENT,
                                textStyle = PixelTextStyle.Accent,
                                placeholderStyle = PixelTextStyle.Default,
                            ),
                        ),
                        PixelButton(
                            text = "CLEAR SECONDARY",
                            onClick = {
                                controller.updateText(secondaryState, "")
                                hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                    ),
                )
            },
        )
    }

    private fun singleChildScrollScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = PixelListController()
        val scrollState = scrollController.create()
        val textController = PixelTextFieldController()
        val titleState = textController.create(initialText = "SCROLL PAGE")
        val noteState = textController.create(initialText = "DRAG TO READ")
        var footerTapped = 0

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelSingleChildScrollView(
                    state = scrollState,
                    controller = scrollController,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    child = PixelColumn(
                        modifier = PixelModifier.Empty.fillMaxWidth(),
                        spacing = 4,
                        children = listOf(
                            sectionTitle("SINGLE CHILD SCROLL"),
                            infoCard("TITLE", titleState.text.ifEmpty { "(EMPTY)" }, accent = titleState.isFocused),
                            PixelTextField(
                                state = titleState,
                                controller = textController,
                                modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                placeholder = "TYPE TITLE",
                                style = PixelTextFieldStyle.Default,
                            ),
                            infoCard("NOTE", noteState.text.ifEmpty { "(EMPTY)" }, accent = noteState.isFocused),
                            PixelTextField(
                                state = noteState,
                                controller = textController,
                                modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                placeholder = "TYPE NOTE",
                                style = PixelTextFieldStyle(
                                    borderTone = PixelTone.ON,
                                    focusedBorderTone = PixelTone.ACCENT,
                                    textStyle = PixelTextStyle.Accent,
                                    placeholderStyle = PixelTextStyle.Default,
                                ),
                            ),
                            infoCard("SECTION", "A LONG COLUMN"),
                            infoCard("SCROLL", "WHOLE PAGE DRAGS"),
                            infoCard("TARGET", "ONE CHILD TREE"),
                            PixelButton(
                                text = "FOOTER TAPS $footerTapped",
                                onClick = {
                                    footerTapped += 1
                                    hostView.requestRender()
                                },
                                modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                style = com.purride.pixelui.PixelButtonStyle.Accent,
                            ),
                            PixelSurface(
                                modifier = PixelModifier.Empty.fillMaxWidth().height(18),
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                                child = PixelBox(
                                    modifier = PixelModifier.Empty.fillMaxSize(),
                                    alignment = PixelAlignment.CENTER,
                                    children = listOf(PixelText("BOTTOM CARD")),
                                ),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private fun horizontalPagerScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PixelPagerController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = PixelAxis.HORIZONTAL,
        )
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelPager(
                    axis = PixelAxis.HORIZONTAL,
                    state = state,
                    controller = controller,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    pages = listOf(
                        pagerPage(
                            title = "H PAGE 1",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.syncToPage(state, 1)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 2",
                        ),
                        pagerPage(
                            title = "H PAGE 2",
                            tone = PixelTone.ACCENT,
                            onPrimaryAction = {
                                controller.syncToPage(state, 2)
                                hostView.requestRender()
                            },
                            onSecondaryAction = {
                                controller.syncToPage(state, 0)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 3",
                            secondaryActionLabel = "BACK 1",
                        ),
                        pagerPage(
                            title = "H PAGE 3",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.syncToPage(state, 0)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "BACK 1",
                        ),
                    ),
                )
            },
        )
    }

    private fun verticalPagerScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PixelPagerController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = PixelAxis.VERTICAL,
        )
        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 80,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelPager(
                    axis = PixelAxis.VERTICAL,
                    state = state,
                    controller = controller,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    pages = listOf(
                        pagerPage(
                            title = "V PAGE 1",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.syncToPage(state, 1)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 2",
                        ),
                        pagerPage(
                            title = "V PAGE 2",
                            tone = PixelTone.ACCENT,
                            onPrimaryAction = {
                                controller.syncToPage(state, 2)
                                hostView.requestRender()
                            },
                            onSecondaryAction = {
                                controller.syncToPage(state, 0)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 3",
                            secondaryActionLabel = "BACK 1",
                        ),
                        pagerPage(
                            title = "V PAGE 3",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.syncToPage(state, 0)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "BACK 1",
                        ),
                    ),
                )
            },
        )
    }

    private fun layoutAndClickScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = PixelListController()
        val scrollState = scrollController.create()
        var count = 0
        var accentMode = false

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("LAYOUT AND CLICK"),
                        infoCard("COUNTER", count.toString(), accent = accentMode),
                        PixelButton(
                            text = "INCREASE",
                            onClick = {
                            count += 1
                            hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        PixelButton(
                            text = "TOGGLE ACCENT",
                            onClick = {
                            accentMode = !accentMode
                            hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        PixelRow(
                            spacing = 4,
                            children = listOf(
                                demoSquare("LEFT"),
                                demoSquare("RIGHT"),
                            ),
                        ),
                        PixelRow(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(18),
                            spacing = 2,
                            children = listOf(
                                PixelSurface(
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = PixelBox(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        alignment = PixelAlignment.CENTER,
                                        children = listOf(PixelText("1X")),
                                    ),
                                ),
                                PixelSurface(
                                    modifier = PixelModifier.Empty.weight(2f).fillMaxHeight(),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ACCENT,
                                    child = PixelBox(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        alignment = PixelAlignment.CENTER,
                                        children = listOf(PixelText("2X", style = PixelTextStyle.Accent)),
                                    ),
                                ),
                            ),
                        ),
                        PixelRow(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(20),
                            spacing = 2,
                            mainAxisAlignment = PixelMainAxisAlignment.CENTER,
                            crossAxisAlignment = PixelCrossAxisAlignment.CENTER,
                            children = listOf(
                                PixelSurface(
                                    modifier = PixelModifier.Empty.size(18, 8),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = PixelBox(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        alignment = PixelAlignment.CENTER,
                                        children = listOf(PixelText("MID")),
                                    ),
                                ),
                                PixelSurface(
                                    modifier = PixelModifier.Empty.size(18, 14),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ACCENT,
                                    child = PixelBox(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        alignment = PixelAlignment.CENTER,
                                        children = listOf(PixelText("TALL", style = PixelTextStyle.Accent)),
                                    ),
                                ),
                            ),
                        ),
                        PixelSurface(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(24),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ON,
                            padding = 2,
                            child = PixelColumn(
                                modifier = PixelModifier.Empty.fillMaxSize(),
                                spacing = 2,
                                mainAxisAlignment = PixelMainAxisAlignment.END,
                                crossAxisAlignment = PixelCrossAxisAlignment.END,
                                children = listOf(
                                    PixelSurface(
                                        modifier = PixelModifier.Empty.size(16, 6),
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ON,
                                        child = PixelBox(
                                            modifier = PixelModifier.Empty.fillMaxSize(),
                                            alignment = PixelAlignment.CENTER,
                                            children = listOf(PixelText("END")),
                                        ),
                                    ),
                                    PixelSurface(
                                        modifier = PixelModifier.Empty.size(24, 6),
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ACCENT,
                                        child = PixelBox(
                                            modifier = PixelModifier.Empty.fillMaxSize(),
                                            alignment = PixelAlignment.CENTER,
                                            children = listOf(PixelText("ALIGN", style = PixelTextStyle.Accent)),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private fun listScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PixelListController()
        val state = controller.create()
        var tapCount = 0

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("VERTICAL LIST"),
                        infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.scrollOffsetPx > 0f),
                        infoCard("TAPS", tapCount.toString()),
                        PixelRow(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                            spacing = 2,
                            children = listOf(
                                PixelButton(
                                    text = "SHOW 1",
                                    onClick = {
                                        controller.scrollItemIntoView(state, itemIndex = 0)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                ),
                                PixelButton(
                                    text = "SHOW 8",
                                    onClick = {
                                        controller.scrollItemIntoView(state, itemIndex = 7)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    style = com.purride.pixelui.PixelButtonStyle.Accent,
                                ),
                            ),
                        ),
                        PixelList(
                            state = state,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(40),
                            spacing = 3,
                            items = List(8) { index ->
                                PixelButton(
                                    text = if (index == 0) {
                                        "TAP ITEM ${index + 1}"
                                    } else {
                                        "LIST ITEM ${index + 1}"
                                    },
                                    onClick = {
                                        if (index == 0) {
                                            tapCount += 1
                                            hostView.requestRender()
                                        }
                                    },
                                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    style = if (index % 2 == 0) {
                                        com.purride.pixelui.PixelButtonStyle.Accent
                                    } else {
                                        com.purride.pixelui.PixelButtonStyle.Default
                                    },
                                )
                            },
                        ),
                    ),
                )
            },
        )
    }

    private fun formAndListScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val textController = PixelTextFieldController()
        val nameState = textController.create(initialText = "PIXEL")
        val cityState = textController.create(initialText = "SHANGHAI")
        val listController = PixelListController()
        val listState = listController.create()
        var selectedLabel = "ITEM 1"

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("FORM + LIST"),
                        infoCard("NAME", nameState.text.ifEmpty { "(EMPTY)" }, accent = nameState.isFocused),
                        infoCard("CITY", cityState.text.ifEmpty { "(EMPTY)" }, accent = cityState.isFocused),
                        infoCard("SELECTED", selectedLabel),
                        PixelTextField(
                            state = nameState,
                            controller = textController,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                            placeholder = "TYPE NAME",
                            style = PixelTextFieldStyle.Default,
                        ),
                        PixelTextField(
                            state = cityState,
                            controller = textController,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                            placeholder = "TYPE CITY",
                            style = PixelTextFieldStyle(
                                borderTone = PixelTone.ON,
                                focusedBorderTone = PixelTone.ACCENT,
                                textStyle = PixelTextStyle.Accent,
                                placeholderStyle = PixelTextStyle.Default,
                            ),
                        ),
                        PixelRow(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                            spacing = 2,
                            children = listOf(
                                PixelButton(
                                    text = "SHOW 1",
                                    onClick = {
                                        listController.scrollItemIntoView(listState, itemIndex = 0)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                ),
                                PixelButton(
                                    text = "SHOW 6",
                                    onClick = {
                                        listController.scrollItemIntoView(listState, itemIndex = 5)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    style = com.purride.pixelui.PixelButtonStyle.Accent,
                                ),
                            ),
                        ),
                        PixelList(
                            state = listState,
                            controller = listController,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(24),
                            spacing = 3,
                            items = List(6) { index ->
                                val label = "ITEM ${index + 1}"
                                PixelButton(
                                    text = label,
                                    onClick = {
                                        selectedLabel = label
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    style = if (selectedLabel == label) {
                                        com.purride.pixelui.PixelButtonStyle.Accent
                                    } else {
                                        com.purride.pixelui.PixelButtonStyle.Default
                                    },
                                )
                            },
                        ),
                    ),
                )
            },
        )
    }

    private fun pagerAndListScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val pagerController = PixelPagerController()
        val pagerState = pagerController.create(
            pageCount = 2,
            currentPage = 0,
            axis = PixelAxis.VERTICAL,
        )
        val listController = PixelListController()
        val listState = listController.create()
        var itemTapCount = 0

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PixelPager(
                    axis = PixelAxis.VERTICAL,
                    state = pagerState,
                    controller = pagerController,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    pages = listOf(
                        pagerPage(
                            title = "OUTER PAGE 1",
                            tone = PixelTone.ACCENT,
                            onPrimaryAction = {
                                pagerController.syncToPage(pagerState, 1)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO LIST",
                        ),
                        PixelSurface(
                            modifier = PixelModifier.Empty.fillMaxSize(),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ACCENT,
                            child = PixelColumn(
                                modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                                spacing = 4,
                                children = listOf(
                                    PixelText(
                                        "PAGE 2 LIST",
                                        style = PixelTextStyle.Accent,
                                    ),
                                    infoCard(
                                        label = "TIP",
                                        value = "LIST FIRST THEN PAGE",
                                        accent = true,
                                    ),
                                    infoCard(
                                        label = "TAPS",
                                        value = itemTapCount.toString(),
                                        accent = itemTapCount > 0,
                                    ),
                                    PixelList(
                                        state = listState,
                                        controller = listController,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(56),
                                        spacing = 3,
                                        items = List(7) { index ->
                                            PixelButton(
                                                text = "INNER ITEM ${index + 1}",
                                                onClick = {
                                                    itemTapCount += 1
                                                    hostView.requestRender()
                                                },
                                                modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                                style = if (index % 2 == 0) {
                                                    com.purride.pixelui.PixelButtonStyle.Accent
                                                } else {
                                                    com.purride.pixelui.PixelButtonStyle.Default
                                                },
                                            )
                                        },
                                    ),
                                    PixelButton(
                                        text = "BACK PAGE 1",
                                        onClick = {
                                            pagerController.syncToPage(pagerState, 0)
                                            hostView.requestRender()
                                        },
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            },
        )
    }

    private fun sectionTitle(
        text: String,
        textRasterizer: PixelTextRasterizer? = null,
    ): com.purride.pixelui.PixelNode {
        val resolvedRasterizer = textRasterizer ?: PixelBitmapFont.Default
        val titleHeight = resolvedRasterizer.measureHeight(text)
        return PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(titleHeight + 6),
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        padding = 0,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize().padding(3),
            alignment = PixelAlignment.CENTER,
            children = listOf(
                PixelText(
                    text,
                    style = PixelTextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = textRasterizer,
                    ),
                ),
            ),
        ),
    )
    }

    private fun infoCard(
        label: String,
        value: String,
        accent: Boolean = false,
        valueRasterizer: PixelTextRasterizer? = null,
    ): com.purride.pixelui.PixelNode {
        val labelRasterizer = PixelBitmapFont.Default
        val resolvedValueRasterizer = valueRasterizer ?: PixelBitmapFont.Default
        val trimmedLabel = trimSingleLineText(
            text = label,
            rasterizer = labelRasterizer,
            maxWidth = 76,
        )
        val trimmedValue = trimSingleLineText(
            text = value,
            rasterizer = resolvedValueRasterizer,
            maxWidth = 76,
        )
        val cardHeight = labelRasterizer.measureHeight(trimmedLabel) +
            resolvedValueRasterizer.measureHeight(trimmedValue) +
            8

        return PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(cardHeight),
        fillTone = PixelTone.OFF,
        borderTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
        padding = 0,
        child = PixelColumn(
            modifier = PixelModifier.Empty.fillMaxSize().padding(3),
            spacing = 2,
            children = listOf(
                PixelText(trimmedLabel, style = PixelTextStyle.Accent),
                PixelText(
                    trimmedValue,
                    style = PixelTextStyle(
                        tone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                        textRasterizer = valueRasterizer,
                    ),
                ),
            ),
        ),
    )
    }

    private fun swatch(tone: PixelTone, label: String) = PixelSurface(
        modifier = PixelModifier.Empty.size(22, 18),
        fillTone = tone,
        borderTone = PixelTone.ON,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = PixelAlignment.CENTER,
            children = listOf(
                PixelText(
                    label,
                    style = if (tone == PixelTone.OFF) {
                        PixelTextStyle.Default
                    } else {
                        PixelTextStyle.Accent
                    },
                ),
            ),
        ),
    )

    private fun demoSquare(label: String) = PixelSurface(
        modifier = PixelModifier.Empty.size(28, 28),
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ON,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = PixelAlignment.CENTER,
            children = listOf(
                PixelText(label),
            ),
        ),
    )

    private fun pagerPage(
        title: String,
        tone: PixelTone,
        onPrimaryAction: () -> Unit,
        primaryActionLabel: String,
        onSecondaryAction: (() -> Unit)? = null,
        secondaryActionLabel: String? = null,
    ) = PixelSurface(
        modifier = PixelModifier.Empty.fillMaxSize(),
        fillTone = PixelTone.OFF,
        borderTone = tone,
        child = PixelColumn(
            modifier = PixelModifier.Empty.fillMaxSize().padding(3),
            spacing = 4,
            children = buildList {
                add(
                    PixelBox(
                        modifier = PixelModifier.Empty.fillMaxWidth().height(20),
                        alignment = PixelAlignment.CENTER,
                        children = listOf(
                            PixelText(
                                title,
                                style = PixelTextStyle(tone = tone),
                            ),
                        ),
                    ),
                )
                add(
                    PixelButton(
                        text = primaryActionLabel,
                        onClick = onPrimaryAction,
                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                    ),
                )
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    add(
                        PixelButton(
                            text = secondaryActionLabel,
                            onClick = onSecondaryAction,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                    )
                }
            },
        ),
    )

    private fun defaultProfile(pixelShape: PixelShape = PixelShape.SQUARE): ScreenProfile {
        return ScreenProfile(
            logicalWidth = 96,
            logicalHeight = 96,
            dotSizePx = 8,
            pixelShape = pixelShape,
        )
    }

    /**
     * 当前 demo 卡片先按单行文本做裁剪，避免中文或中英混排接上真实字形后直接溢出边框。
     *
     * 后续如果 `pixel-ui` 内核补了通用单行裁剪或多行换行能力，这里可以再回收。
     */
    private fun trimSingleLineText(
        text: String,
        rasterizer: PixelTextRasterizer,
        maxWidth: Int,
    ): String {
        if (text.isEmpty() || maxWidth <= 0) {
            return ""
        }
        if (rasterizer.measureText(text) <= maxWidth) {
            return text
        }

        val ellipsis = "..."
        val builder = StringBuilder(text.length)
        text.forEach { character ->
            val candidate = builder.toString() + character
            if (rasterizer.measureText(candidate) > maxWidth) {
                val safeText = builder.toString()
                if (safeText.isEmpty()) {
                    return ""
                }
                if (rasterizer.measureText(safeText) <= maxWidth &&
                    rasterizer.measureText(safeText + ellipsis) <= maxWidth
                ) {
                    return safeText + ellipsis
                }

                val fallbackBuilder = StringBuilder(safeText)
                while (fallbackBuilder.isNotEmpty() &&
                    rasterizer.measureText(fallbackBuilder.toString() + ellipsis) > maxWidth
                ) {
                    fallbackBuilder.deleteCharAt(fallbackBuilder.lastIndex)
                }
                return if (fallbackBuilder.isEmpty()) {
                    ""
                } else {
                    fallbackBuilder.toString() + ellipsis
                }
            }
            builder.append(character)
        }
        return builder.toString()
    }

    /**
     * 普通 demo 页面统一使用单子节点滚动容器做根布局。
     *
     * 这样页面内容继续变多时，不会因为逻辑高度固定而把底部组件直接裁掉。
     * `Pager` / `List` 专项验证页继续保留各自的根容器，不混进额外滚动层。
     */
    private fun scrollableRoot(
        state: com.purride.pixelui.state.PixelListState,
        controller: PixelListController,
        children: List<com.purride.pixelui.PixelNode>,
    ): com.purride.pixelui.PixelNode {
        return PixelSingleChildScrollView(
            state = state,
            controller = controller,
            modifier = PixelModifier.Empty.fillMaxSize().padding(4),
            child = PixelColumn(
                modifier = PixelModifier.Empty.fillMaxWidth(),
                spacing = 4,
                children = children,
            ),
        )
    }
}
