package com.purride.pixeldemo.app

import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Alignment
import com.purride.pixelui.Align
import com.purride.pixelui.Axis
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Container
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListView
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparated
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.PageController
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.nextPage
import com.purride.pixelui.previousPage
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.Theme
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.ThemeData
import com.purride.pixelui.ContainerStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.fillMaxHeight
import com.purride.pixelui.fillMaxWidth
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.showItem
import com.purride.pixelui.size
import com.purride.pixelui.weight

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
        val content: () -> Widget,
    )

    private fun accentUiTheme(): ThemeData {
        return ThemeData(
            textStyle = TextStyle.Accent,
            buttonStyle = ButtonStyle.Accent,
            disabledButtonStyle = ButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = TextStyle(tone = PixelTone.OFF),
            ),
            textFieldStyle = TextFieldStyle(
                borderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                textStyle = TextStyle.Accent,
                placeholderStyle = TextStyle.Default,
            ),
            readOnlyTextFieldStyle = TextFieldStyle(
                borderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                readOnlyBorderTone = PixelTone.ACCENT,
                textStyle = TextStyle.Accent,
                placeholderStyle = TextStyle.Default,
            ),
            containerStyle = ContainerStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                alignment = Alignment.CENTER,
            ),
        )
    }

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
        val scrollController = ScrollController()
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
                        DecoratedBox(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(28),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ACCENT,
                            padding = 0,
                            child = Column(
                                modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                                spacing = 2,
                                children = listOf(
                                    Text("段落", style = TextStyle.Accent),
                                    Text(
                                        data = "这是一段用于验证中文换行与省略的像素文本内容，后续页面可以直接复用这套能力。",
                                        softWrap = true,
                                        maxLines = 3,
                                        overflow = TextOverflow.ELLIPSIS,
                                    ),
                                ),
                            ),
                        ),
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
        val scrollController = ScrollController()
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
                        OutlinedButton(
                            text = "NEXT THEME",
                            onPressed = {
                                currentThemeIndex = (currentThemeIndex + 1) % themes.size
                                applyHostAppearance()
                                hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        OutlinedButton(
                            text = "NEXT SHAPE",
                            onPressed = {
                                currentShapeIndex = (currentShapeIndex + 1) % shapes.size
                                applyHostAppearance()
                                hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        Row(
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
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val controller = TextEditingController()
        val primaryState = controller.create(initialText = "PIXEL")
        val secondaryState = controller.create()
        val readOnlyState = controller.create(initialText = "READ ONLY VALUE THAT SHOULD CLIP")
        val accentTheme = accentUiTheme()
        var liveText = primaryState.text
        var submittedText = ""

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
                        infoCard("READ ONLY", "VISIBLE BUT LOCKED AND CLIPPED"),
                        infoCard("LIVE", liveText.ifEmpty { "(EMPTY)" }, accent = liveText.isNotEmpty()),
                        infoCard("SUBMITTED", submittedText.ifEmpty { "(NONE)" }, accent = submittedText.isNotEmpty()),
                        TextField(
                            state = primaryState,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                            placeholder = "TYPE PRIMARY",
                            style = TextFieldStyle.Default,
                            autofocus = true,
                            textInputAction = TextInputAction.NEXT,
                            onChanged = { text ->
                                liveText = text
                                hostView.requestRender()
                            },
                            onSubmitted = { text ->
                                submittedText = text
                                controller.requestFocus(secondaryState)
                                hostView.requestRender()
                            },
                        ),
                        Theme(
                            data = accentTheme,
                            child = Column(
                                spacing = 2,
                                children = listOf(
                                    TextField(
                                        state = secondaryState,
                                        controller = controller,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                        placeholder = "TYPE SECONDARY",
                                        enabled = primaryState.text.isNotEmpty(),
                                        textInputAction = TextInputAction.DONE,
                                        onSubmitted = { text ->
                                            submittedText = text
                                            hostView.requestRender()
                                        },
                                    ),
                                    TextField(
                                        state = readOnlyState,
                                        controller = controller,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                        placeholder = "READ ONLY",
                                        readOnly = true,
                                    ),
                                    OutlinedButton(
                                        text = "CLEAR SECONDARY",
                                        onPressed = if (secondaryState.text.isNotEmpty()) {
                                            {
                                                controller.clear(secondaryState)
                                                hostView.requestRender()
                                            }
                                        } else {
                                            null
                                        },
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    ),
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = "FOCUS PRIMARY",
                            onPressed = {
                                controller.requestFocus(primaryState)
                                hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        Row(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                            spacing = 2,
                            children = listOf(
                                OutlinedButton(
                                    text = "SELECT ALL",
                                    onPressed = {
                                        controller.selectAll(primaryState)
                                        controller.requestFocus(primaryState)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                ),
                                OutlinedButton(
                                    text = "CURSOR END",
                                    onPressed = {
                                        controller.setSelection(
                                            state = primaryState,
                                            selectionStart = primaryState.text.length,
                                        )
                                        controller.requestFocus(primaryState)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    style = ButtonStyle.Accent,
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = "NULL DISABLED",
                            onPressed = null,
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
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val textController = TextEditingController()
        val titleState = textController.create(initialText = "SCROLL PAGE")
        val noteState = textController.create(initialText = "DRAG TO READ")
        var footerTapped = 0

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR),
            initialTextRasterizer = textRasterizers.default,
            content = {
                SingleChildScrollView(
                    state = scrollState,
                    controller = scrollController,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    child = Column(
                        modifier = PixelModifier.Empty.fillMaxWidth(),
                        spacing = 4,
                        children = listOf(
                            sectionTitle("SINGLE CHILD SCROLL"),
                            infoCard("TITLE", titleState.text.ifEmpty { "(EMPTY)" }, accent = titleState.isFocused),
                            TextField(
                                state = titleState,
                                controller = textController,
                                modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                placeholder = "TYPE TITLE",
                                style = TextFieldStyle.Default,
                            ),
                            infoCard("NOTE", noteState.text.ifEmpty { "(EMPTY)" }, accent = noteState.isFocused),
                            TextField(
                                state = noteState,
                                controller = textController,
                                modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                placeholder = "TYPE NOTE",
                                style = TextFieldStyle(
                                    borderTone = PixelTone.ON,
                                    focusedBorderTone = PixelTone.ACCENT,
                                    textStyle = TextStyle.Accent,
                                    placeholderStyle = TextStyle.Default,
                                ),
                            ),
                            infoCard("SECTION", "A LONG COLUMN"),
                            infoCard("SCROLL", "WHOLE PAGE DRAGS"),
                            infoCard("TARGET", "ONE CHILD TREE"),
                            OutlinedButton(
                                text = "FOOTER TAPS $footerTapped",
                                onPressed = {
                                    footerTapped += 1
                                    hostView.requestRender()
                                },
                                modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                style = ButtonStyle.Accent,
                            ),
                            DecoratedBox(
                                modifier = PixelModifier.Empty.fillMaxWidth().height(18),
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                                child = Center(
                                    modifier = PixelModifier.Empty.fillMaxSize(),
                                    child = Text("BOTTOM CARD"),
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
        val controller = PageController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = Axis.HORIZONTAL,
        )
        var reportedPage = state.currentPage + 1
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PageViewBuilder(
                    axis = Axis.HORIZONTAL,
                    state = state,
                    controller = controller,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    onPageChanged = { page ->
                        reportedPage = page + 1
                        hostView.requestRender()
                    },
                    itemCount = 3,
                    itemBuilder = { index ->
                        when (index) {
                            0 -> pagerPage(
                                title = "H PAGE 1 / NOW $reportedPage",
                                tone = PixelTone.ON,
                                onPrimaryAction = {
                                    controller.nextPage(state)
                                    hostView.requestRender()
                                },
                                primaryActionLabel = "GO 2",
                            )
                            1 -> pagerPage(
                                title = "H PAGE 2 / NOW $reportedPage",
                                tone = PixelTone.ACCENT,
                                onPrimaryAction = {
                                    controller.nextPage(state)
                                    hostView.requestRender()
                                },
                                onSecondaryAction = {
                                    controller.previousPage(state)
                                    hostView.requestRender()
                                },
                                primaryActionLabel = "GO 3",
                                secondaryActionLabel = "BACK 1",
                            )
                            else -> pagerPage(
                                title = "H PAGE 3 / NOW $reportedPage",
                                tone = PixelTone.ON,
                                onPrimaryAction = {
                                    controller.jumpToPage(state, 0)
                                    hostView.requestRender()
                                },
                                primaryActionLabel = "BACK 1",
                            )
                        }
                    },
                )
            },
        )
    }

    private fun verticalPagerScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PageController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = Axis.VERTICAL,
        )
        var reportedPage = state.currentPage + 1
        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 80,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PageView(
                    axis = Axis.VERTICAL,
                    state = state,
                    controller = controller,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    onPageChanged = { page ->
                        reportedPage = page + 1
                        hostView.requestRender()
                    },
                    pages = listOf(
                        pagerPage(
                            title = "V PAGE 1 / NOW $reportedPage",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.nextPage(state)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 2",
                        ),
                        pagerPage(
                            title = "V PAGE 2 / NOW $reportedPage",
                            tone = PixelTone.ACCENT,
                            onPrimaryAction = {
                                controller.nextPage(state)
                                hostView.requestRender()
                            },
                            onSecondaryAction = {
                                controller.previousPage(state)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO 3",
                            secondaryActionLabel = "BACK 1",
                        ),
                        pagerPage(
                            title = "V PAGE 3 / NOW $reportedPage",
                            tone = PixelTone.ON,
                            onPrimaryAction = {
                                controller.jumpToPage(state, 0)
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
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        var count = 0
        var accentMode = false
        val themedCard = ThemeData(
            textStyle = TextStyle.Accent,
            buttonStyle = ButtonStyle.Accent,
            disabledButtonStyle = ButtonStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                textStyle = TextStyle.Accent.copy(
                    tone = PixelTone.OFF,
                ),
            ),
            textFieldStyle = TextFieldStyle(
                borderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                textStyle = TextStyle.Accent,
                placeholderStyle = TextStyle.Default,
            ),
            readOnlyTextFieldStyle = TextFieldStyle(
                borderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                readOnlyBorderTone = PixelTone.ACCENT,
                textStyle = TextStyle.Accent,
                placeholderStyle = TextStyle.Default,
            ),
            containerStyle = ContainerStyle(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ACCENT,
                alignment = Alignment.CENTER,
            ),
        )

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
                        Container(
                            width = 40,
                            height = 16,
                            padding = EdgeInsets.all(2),
                            child = Theme(
                                data = themedCard,
                                child = Center(
                                    modifier = PixelModifier.Empty.fillMaxSize(),
                                    child = Text("THEME"),
                                ),
                            ),
                        ),
                        Container(
                            width = 28,
                            height = 12,
                            margin = EdgeInsets.only(left = 6),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ACCENT,
                            child = Center(
                                modifier = PixelModifier.Empty.fillMaxSize(),
                                child = Text("MARGIN", style = TextStyle.Accent),
                            ),
                        ),
                        Theme(
                            data = themedCard,
                            child = Column(
                                spacing = 2,
                                children = listOf(
                                    OutlinedButton(
                                        text = "INCREASE",
                                        onPressed = {
                                            count += 1
                                            hostView.requestRender()
                                        },
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    ),
                                    OutlinedButton(
                                        text = "THEMED DISABLED",
                                        onPressed = null,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    ),
                                ),
                            ),
                        ),
                        OutlinedButton(
                            text = "TOGGLE ACCENT",
                            onPressed = {
                                accentMode = !accentMode
                                hostView.requestRender()
                            },
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                        ),
                        Row(
                            spacing = 4,
                            children = listOf(
                                demoSquare("LEFT"),
                                demoSquare("RIGHT"),
                            ),
                        ),
                        Row(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(18),
                            spacing = 2,
                            children = listOf(
                                DecoratedBox(
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = Center(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        child = Text("1X"),
                                    ),
                                ),
                                DecoratedBox(
                                    modifier = PixelModifier.Empty.weight(2f).fillMaxHeight(),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ACCENT,
                                    child = Center(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        child = Text("2X", style = TextStyle.Accent),
                                    ),
                                ),
                            ),
                        ),
                        Row(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(20),
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            children = listOf(
                                DecoratedBox(
                                    modifier = PixelModifier.Empty.size(18, 8),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    child = Center(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        child = Text("MID"),
                                    ),
                                ),
                                DecoratedBox(
                                    modifier = PixelModifier.Empty.size(18, 14),
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ACCENT,
                                    child = Center(
                                        modifier = PixelModifier.Empty.fillMaxSize(),
                                        child = Text("TALL", style = TextStyle.Accent),
                                    ),
                                ),
                            ),
                        ),
                        GestureDetector(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(24),
                            onTap = {
                                accentMode = !accentMode
                                hostView.requestRender()
                            },
                            child = DecoratedBox(
                                modifier = PixelModifier.Empty.fillMaxSize(),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ON,
                            padding = 2,
                            child = Column(
                                modifier = PixelModifier.Empty.fillMaxSize(),
                                spacing = 2,
                                mainAxisAlignment = MainAxisAlignment.END,
                                crossAxisAlignment = CrossAxisAlignment.END,
                                children = listOf(
                                    DecoratedBox(
                                        modifier = PixelModifier.Empty.size(16, 6),
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ON,
                                        child = Center(
                                            modifier = PixelModifier.Empty.fillMaxSize(),
                                            child = Text("END"),
                                        ),
                                    ),
                                    DecoratedBox(
                                        modifier = PixelModifier.Empty.size(24, 6),
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ACCENT,
                                        child = Align(
                                            modifier = PixelModifier.Empty.fillMaxSize(),
                                            alignment = Alignment.CENTER,
                                            child = Text("ALIGN", style = TextStyle.Accent),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                )
            },
        )
    }

    private fun listScene(
        hostView: PixelHostView,
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = ScrollController()
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
                Column(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("VERTICAL LIST"),
                        infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.scrollOffsetPx > 0f),
                        infoCard("TAPS", tapCount.toString()),
                        Row(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                            spacing = 2,
                            children = listOf(
                                OutlinedButton(
                                    text = "SHOW 1",
                                    onPressed = {
                                        controller.showItem(state, itemIndex = 0)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                ),
                                OutlinedButton(
                                    text = "SHOW 8",
                                    onPressed = {
                                        controller.showItem(state, itemIndex = 7)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    style = ButtonStyle.Accent,
                                ),
                            ),
                        ),
                        Row(
                            modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                            spacing = 2,
                            children = listOf(
                                OutlinedButton(
                                    text = "TOP",
                                    onPressed = {
                                        controller.jumpToStart(state)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                ),
                                OutlinedButton(
                                    text = "BOTTOM",
                                    onPressed = {
                                        controller.jumpToEnd(state)
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                    style = ButtonStyle.Accent,
                                ),
                            ),
                        ),
                        ListViewBuilder(
                            itemCount = 8,
                            state = state,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(40),
                            itemBuilder = { index ->
                                OutlinedButton(
                                    text = if (index == 0) {
                                        "TAP ITEM ${index + 1}"
                                    } else {
                                        "LIST ITEM ${index + 1}"
                                    },
                                    onPressed = {
                                        if (index == 0) {
                                            tapCount += 1
                                            hostView.requestRender()
                                        }
                                    },
                                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    style = if (index % 2 == 0) {
                                        ButtonStyle.Accent
                                    } else {
                                        ButtonStyle.Default
                                    },
                                )
                            },
                            spacing = 3,
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
        val textController = TextEditingController()
        val nameState = textController.create(initialText = "PIXEL")
        val cityState = textController.create(initialText = "SHANGHAI")
        val listController = ScrollController()
        val listState = listController.create()
        val accentTheme = accentUiTheme()
        var selectedLabel = "ITEM 1"

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            content = {
                Column(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("FORM + LIST"),
                        infoCard("NAME", nameState.text.ifEmpty { "(EMPTY)" }, accent = nameState.isFocused),
                        infoCard("CITY", cityState.text.ifEmpty { "(EMPTY)" }, accent = cityState.isFocused),
                        infoCard("SELECTED", selectedLabel),
                        Theme(
                            data = accentTheme,
                            child = Column(
                                spacing = 2,
                                children = listOf(
                                    TextField(
                                        state = nameState,
                                        controller = textController,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                        placeholder = "TYPE NAME",
                                    ),
                                    TextField(
                                        state = cityState,
                                        controller = textController,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(16),
                                        placeholder = "TYPE CITY",
                                    ),
                                    Row(
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                        spacing = 2,
                                        children = listOf(
                                            OutlinedButton(
                                                text = "SHOW 1",
                                                onPressed = {
                                                    listController.showItem(listState, itemIndex = 0)
                                                    hostView.requestRender()
                                                },
                                                modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                            ),
                                            OutlinedButton(
                                                text = "SHOW 6",
                                                onPressed = {
                                                    listController.showItem(listState, itemIndex = 5)
                                                    hostView.requestRender()
                                                },
                                                modifier = PixelModifier.Empty.weight(1f).fillMaxHeight(),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        ListView(
                            state = listState,
                            controller = listController,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(24),
                            spacing = 3,
                            items = List(6) { index ->
                                val label = "ITEM ${index + 1}"
                                OutlinedButton(
                                    text = label,
                                    onPressed = {
                                        selectedLabel = label
                                        hostView.requestRender()
                                    },
                                    modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                    style = if (selectedLabel == label) {
                                        ButtonStyle.Accent
                                    } else {
                                        ButtonStyle.Default
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
        val pagerController = PageController()
        val pagerState = pagerController.create(
            pageCount = 2,
            currentPage = 0,
            axis = Axis.VERTICAL,
        )
        val listController = ScrollController()
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
                PageView(
                    axis = Axis.VERTICAL,
                    state = pagerState,
                    controller = pagerController,
                    modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                    pages = listOf(
                        pagerPage(
                            title = "OUTER PAGE 1",
                            tone = PixelTone.ACCENT,
                            onPrimaryAction = {
                                pagerController.nextPage(pagerState)
                                hostView.requestRender()
                            },
                            primaryActionLabel = "GO LIST",
                        ),
                        DecoratedBox(
                            modifier = PixelModifier.Empty.fillMaxSize(),
                            fillTone = PixelTone.OFF,
                            borderTone = PixelTone.ACCENT,
                            child = Column(
                                modifier = PixelModifier.Empty.fillMaxSize().padding(3),
                                spacing = 4,
                                children = listOf(
                                    Text("PAGE 2 LIST", style = TextStyle.Accent),
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
                                    ListView(
                                        state = listState,
                                        controller = listController,
                                        modifier = PixelModifier.Empty.fillMaxWidth().height(56),
                                        spacing = 3,
                                        items = List(7) { index ->
                                            OutlinedButton(
                                                text = "INNER ITEM ${index + 1}",
                                                onPressed = {
                                                    itemTapCount += 1
                                                    hostView.requestRender()
                                                },
                                                modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                                                style = if (index % 2 == 0) {
                                                    ButtonStyle.Accent
                                                } else {
                                                    ButtonStyle.Default
                                                },
                                            )
                                        },
                                    ),
                                    OutlinedButton(
                                        text = "BACK PAGE 1",
                                        onPressed = {
                                            pagerController.previousPage(pagerState)
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
    ): Widget {
        val resolvedRasterizer = textRasterizer ?: PixelBitmapFont.Default
        val titleHeight = resolvedRasterizer.measureHeight(text)
        return Container(
            modifier = PixelModifier.Empty.fillMaxWidth(),
            height = titleHeight + 6,
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ACCENT,
            padding = EdgeInsets.all(3),
            child = Center(
                modifier = PixelModifier.Empty.fillMaxSize(),
                child = Text(
                    text,
                    style = TextStyle(
                        tone = PixelTone.ACCENT,
                        textRasterizer = textRasterizer,
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
    ): Widget {
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

        return Container(
            modifier = PixelModifier.Empty.fillMaxWidth(),
            height = cardHeight,
            fillTone = PixelTone.OFF,
            borderTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
            padding = EdgeInsets.all(3),
            child = Column(
                modifier = PixelModifier.Empty.fillMaxSize(),
                spacing = 2,
                children = listOf(
                    Text(trimmedLabel, style = TextStyle.Accent),
                    Text(
                        trimmedValue,
                        style = TextStyle(
                            tone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                            textRasterizer = valueRasterizer,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun swatch(tone: PixelTone, label: String): Widget = Container(
        width = 22,
        height = 18,
        fillTone = tone,
        borderTone = PixelTone.ON,
        child = Center(
            modifier = PixelModifier.Empty.fillMaxSize(),
            child = Text(
                label,
                style = if (tone == PixelTone.OFF) {
                    TextStyle.Default
                } else {
                    TextStyle.Accent
                },
            ),
        ),
    )

    private fun demoSquare(label: String): Widget = Container(
        width = 28,
        height = 28,
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ON,
        child = Center(
            modifier = PixelModifier.Empty.fillMaxSize(),
            child = Text(label),
        ),
    )

    private fun pagerPage(
        title: String,
        tone: PixelTone,
        onPrimaryAction: () -> Unit,
        primaryActionLabel: String,
        onSecondaryAction: (() -> Unit)? = null,
        secondaryActionLabel: String? = null,
    ): Widget = Container(
        modifier = PixelModifier.Empty.fillMaxSize(),
        fillTone = PixelTone.OFF,
        borderTone = tone,
        padding = EdgeInsets.all(3),
        child = Column(
            modifier = PixelModifier.Empty.fillMaxSize(),
            spacing = 4,
            children = buildList {
                add(
                    Center(
                        modifier = PixelModifier.Empty.fillMaxWidth().height(20),
                        child = Text(
                            title,
                            style = TextStyle(tone = tone),
                        ),
                    ),
                )
                add(
                    OutlinedButton(
                        text = primaryActionLabel,
                        onPressed = onPrimaryAction,
                        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
                    ),
                )
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    add(
                        OutlinedButton(
                            text = secondaryActionLabel,
                            onPressed = onSecondaryAction,
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
        controller: ScrollController,
        children: List<Widget>,
    ): Widget {
        return SingleChildScrollView(
            state = state,
            controller = controller,
            modifier = PixelModifier.Empty.fillMaxSize().padding(4),
            child = Column(
                modifier = PixelModifier.Empty.fillMaxWidth(),
                spacing = 4,
                children = children,
            ),
        )
    }
}
