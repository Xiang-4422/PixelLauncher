package com.purride.pixeldemo.app

import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.Align
import com.purride.pixelui.AlignDirectional
import com.purride.pixelui.Axis
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Container
import com.purride.pixelui.ContainerDirectional
import com.purride.pixelui.DecoratedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.Expanded
import com.purride.pixelui.FlexFit
import com.purride.pixelui.Flexible
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListView
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.ListViewSeparated
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageView
import com.purride.pixelui.PageViewBuilder
import com.purride.pixelui.PageController
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedDirectional
import com.purride.pixelui.PositionedFill
import com.purride.pixelui.PixelTextSpan
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.RichText
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.nextPage
import com.purride.pixelui.previousPage
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Builder
import com.purride.pixelui.ListenableBuilder
import com.purride.pixelui.Row
import com.purride.pixelui.RootWidgetProvider
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulBuilder
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.Theme
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.ThemeData
import com.purride.pixelui.ContainerStyle
import com.purride.pixelui.Directionality
import com.purride.pixelui.InheritedNotifier
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import com.purride.pixelui.fling
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.showItem
import com.purride.pixelui.Padding
import com.purride.pixeldemo.app.customrender.SpinningSquareWidget

/**
 * Demo scene 定义集合。
 *
 * 每个 scene 都通过 pixel-engine UI layer 的组件树搭建，避免再退回手写 renderer。
 */
object DemoScenes {

    data class DemoScene(
        val initialProfile: ScreenProfile,
        val initialPalette: PixelPalette,
        val initialTextRasterizer: PixelTextRasterizer,
        val initialThemeData: ThemeData? = null,
        val content: RootWidgetProvider,
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
            DemoSceneKind.PIPELINE_TEXT_SURFACE -> pipelineTextSurfaceScene(textRasterizers)
            DemoSceneKind.TEXT -> textScene(textRasterizers)
            DemoSceneKind.PALETTE -> paletteScene(hostView, textRasterizers, applyPreferredProfile)
            DemoSceneKind.TEXT_FIELD -> textFieldScene(textRasterizers)
            DemoSceneKind.SINGLE_CHILD_SCROLL -> singleChildScrollScene(textRasterizers)
            DemoSceneKind.HORIZONTAL_PAGER -> horizontalPagerScene(textRasterizers)
            DemoSceneKind.VERTICAL_PAGER -> verticalPagerScene(textRasterizers)
            DemoSceneKind.LIST -> listScene(textRasterizers)
            DemoSceneKind.VIRTUAL_LIST -> virtualListScene(textRasterizers)
            DemoSceneKind.SCROLL_STRESS -> scrollStressScene(textRasterizers)
            DemoSceneKind.FORM_AND_LIST -> formAndListScene(textRasterizers)
            DemoSceneKind.PAGER_AND_LIST -> pagerAndListScene(textRasterizers)
            DemoSceneKind.TEXT_INPUT_MULTILINE -> textInputMultilineScene(textRasterizers)
            DemoSceneKind.RICH_TEXT -> richTextScene(textRasterizers)
            DemoSceneKind.THEME_STATES -> themeStatesScene(textRasterizers)
            DemoSceneKind.ENGINE_STABILITY_GATE -> engineStabilityGateScene(textRasterizers)
            DemoSceneKind.ENVIRONMENT -> environmentScene(textRasterizers)
            DemoSceneKind.LAUNCHER_LIKE -> launcherLikeScene(textRasterizers)
            DemoSceneKind.DRAWER_LIKE -> drawerLikeScene(textRasterizers)
            DemoSceneKind.DRAWER_GATE_V2 -> drawerGateV2Scene(textRasterizers)
            DemoSceneKind.LAYOUT_AND_CLICK -> layoutAndClickScene(textRasterizers)
            DemoSceneKind.CUSTOM_RENDER_OBJECT -> customRenderObjectScene(textRasterizers)
        }
    }

    /**
     * 第一版新渲染管线验证页。
     *
     * 这里刻意只使用首批受支持的 `Text + Surface + Align + Flex` 相关组件，以及点击 modifier，
     * 用来验证新 pipeline 已经能端到端承接真实页面，而不是只停留在内部骨架。
     */
    private fun pipelineTextSurfaceScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        var accent = false
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.terminalGreen(),
            initialTextRasterizer = textRasterizers.default,
            content = {
                Center(
                    child = StatefulBuilder { _, setState ->
                        SizedBox(
                            width = 64,
                            height = 32,
                            child = Align(
                                alignment = if (accent) Alignment.BOTTOM_END else Alignment.TOP_START,
                                child = GestureDetector(
                                    onTap = {
                                        setState {
                                            accent = !accent
                                        }
                                    },
                                    child = Container(
                                        width = 52,
                                        height = 26,
                                        padding = EdgeInsets.all(2),
                                        fillTone = if (accent) PixelTone.ACCENT else PixelTone.OFF,
                                        borderTone = PixelTone.ON,
                                        alignment = Alignment.CENTER,
                                        child = Column(
                                            spacing = 1,
                                            mainAxisSize = MainAxisSize.MIN,
                                            mainAxisAlignment = MainAxisAlignment.CENTER,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Text(
                                                    data = if (accent) "ACTIVE" else "PIPE",
                                                    style = if (accent) TextStyle.Default else TextStyle.Accent,
                                                    textAlign = TextAlign.CENTER,
                                                ),
                                                Row(
                                                    mainAxisAlignment = if (accent) {
                                                        MainAxisAlignment.SPACE_BETWEEN
                                                    } else {
                                                        MainAxisAlignment.CENTER
                                                    },
                                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                                    children = listOf(
                                                        Text("A"),
                                                        Text("B"),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    },
                )
            },
        )
    }

    private fun textScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        var localStateHighlighted = false
        val scopedCounter = ValueNotifier(0)
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
                        SizedBox(
                            height = 48,
                            child = DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                                padding = 0,
                                child = Container(
                                    padding = EdgeInsets.all(3),
                                    child = Column(
                                        spacing = 2,
                                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                        children = listOf(
                                            Text("TEXT ALIGN", style = TextStyle.Accent),
                                            SizedBox(
                                                height = 6,
                                                child = Text("START", textAlign = TextAlign.START),
                                            ),
                                            SizedBox(
                                                height = 6,
                                                child = Text("CENTER", textAlign = TextAlign.CENTER),
                                            ),
                                            SizedBox(
                                                height = 6,
                                                child = Text("END", textAlign = TextAlign.END),
                                            ),
                                            Directionality(
                                                textDirection = TextDirection.RTL,
                                                child = SizedBox(
                                                    height = 6,
                                                    child = Text("RTL START/END", textAlign = TextAlign.START),
                                                ),
                                            ),
                                            Directionality(
                                                textDirection = TextDirection.RTL,
                                                child = SizedBox(
                                                    height = 6,
                                                    child = Text("RTL END/START", textAlign = TextAlign.END),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        EnvironmentInfoWidget(
                            label = "HOST ENV",
                        ),
                        Directionality(
                            textDirection = TextDirection.RTL,
                            child = Theme(
                                data = ThemeData(
                                    textStyle = TextStyle.Accent,
                                    buttonStyle = ButtonStyle.Accent,
                                    containerStyle = ContainerStyle(
                                        fillTone = PixelTone.OFF,
                                        borderTone = PixelTone.ACCENT,
                                        alignment = Alignment.CENTER,
                                    ),
                                ),
                                child = EnvironmentInfoWidget(
                                    label = "LOCAL OVERRIDE",
                                ),
                            ),
                        ),
                        Builder { context ->
                            val mediaQuery = MediaQuery.of(context)
                            infoCard(
                                label = "BUILDER",
                                value = "VIEWPORT ${mediaQuery.logicalWidth}x${mediaQuery.logicalHeight}",
                                accent = mediaQuery.logicalWidth >= mediaQuery.logicalHeight,
                            )
                        },
                        Builder { context ->
                            val direction = Directionality.of(context)
                            StatefulBuilder { _, setState ->
                                SizedBox(
                                    height = 14,
                                    child = OutlinedButton(
                                        text = "LOCAL STATE ${if (localStateHighlighted) "ON" else direction.name}",
                                        onPressed = {
                                            setState {
                                                localStateHighlighted = !localStateHighlighted
                                            }
                                        },
                                        style = if (localStateHighlighted) ButtonStyle.Accent else ButtonStyle.Default,
                                    ),
                                )
                            }
                        },
                        ScopedCounterScope(
                            notifier = scopedCounter,
                            child = Column(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    ScopedCounterInfoWidget(),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "SCOPE COUNT +1",
                                            onPressed = {
                                                scopedCounter.value = scopedCounter.value + 1
                                            },
                                            style = ButtonStyle.Accent,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 28,
                            child = DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ACCENT,
                                padding = 0,
                                child = Container(
                                    padding = EdgeInsets.all(3),
                                    child = Column(
                                        spacing = 2,
                                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
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

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.entries.first()),
            initialTextRasterizer = textRasterizers.default,
            content = {
                PaletteSceneWidget(
                    hostView = hostView,
                    applyPreferredProfile = applyPreferredProfile,
                    scrollController = scrollController,
                    scrollState = scrollState,
                )
            },
        )
    }

    private fun textFieldScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val controller = TextEditingController()
        val primaryState = controller.create(initialText = "PIXEL")
        val secondaryState = controller.create()
        val readOnlyState = controller.create(initialText = "READ ONLY VALUE THAT SHOULD CLIP")
        val accentTheme = accentUiTheme()
        val liveText = ValueNotifier(primaryState.text)
        val submittedText = ValueNotifier("")
        val localThemeTapCount = ValueNotifier(0)

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = accentTheme,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("TEXT FIELD"),
                        ListenableBuilder(
                            listenable = controller,
                            builder = {
                                Column(
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        infoCard("PRIMARY", primaryState.text.ifEmpty { "(EMPTY)" }, accent = primaryState.isFocused),
                                        infoCard("SECONDARY", secondaryState.text.ifEmpty { "(EMPTY)" }, accent = secondaryState.isFocused),
                                        infoCard("READ ONLY", "VISIBLE BUT LOCKED AND CLIPPED"),
                                        ValueListenableBuilder(
                                            listenable = liveText,
                                        ) { _, value ->
                                            infoCard("LIVE", value.ifEmpty { "(EMPTY)" }, accent = value.isNotEmpty())
                                        },
                                        ValueListenableBuilder(
                                            listenable = submittedText,
                                        ) { _, value ->
                                            infoCard("SUBMITTED", value.ifEmpty { "(NONE)" }, accent = value.isNotEmpty())
                                        },
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = primaryState,
                                                controller = controller,
                                                placeholder = "TYPE PRIMARY",
                                                style = TextFieldStyle.Default,
                                                autofocus = true,
                                                textInputAction = TextInputAction.NEXT,
                                                onChanged = { text ->
                                                    liveText.value = text
                                                },
                                                onSubmitted = { text ->
                                                    submittedText.value = text
                                                    controller.requestFocus(secondaryState)
                                                },
                                            ),
                                        ),
                                        Column(
                                            spacing = 2,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                SizedBox(
                                                    height = 16,
                                                    child = TextField(
                                                        state = secondaryState,
                                                        controller = controller,
                                                        placeholder = "TYPE SECONDARY",
                                                        enabled = primaryState.text.isNotEmpty(),
                                                        textInputAction = TextInputAction.DONE,
                                                        onSubmitted = { text ->
                                                            submittedText.value = text
                                                        },
                                                    ),
                                                ),
                                                SizedBox(
                                                    height = 16,
                                                    child = TextField(
                                                        state = readOnlyState,
                                                        controller = controller,
                                                        placeholder = "READ ONLY",
                                                        readOnly = true,
                                                    ),
                                                ),
                                                SizedBox(
                                                    height = 14,
                                                    child = OutlinedButton(
                                                        text = "CLEAR SECONDARY",
                                                        onPressed = if (secondaryState.text.isNotEmpty()) {
                                                            { controller.clear(secondaryState) }
                                                        } else {
                                                            null
                                                        },
                                                    ),
                                                ),
                                            ),
                                        ),
                                        Theme(
                                            data = ThemeData(
                                                textStyle = TextStyle.Default,
                                                buttonStyle = ButtonStyle.Default,
                                                disabledButtonStyle = ButtonStyle.Disabled,
                                                textFieldStyle = TextFieldStyle.Default,
                                                readOnlyTextFieldStyle = TextFieldStyle.Default.copy(
                                                    readOnlyBorderTone = PixelTone.ON,
                                                ),
                                                disabledTextFieldStyle = TextFieldStyle.Default.copy(
                                                    disabledBorderTone = PixelTone.ON,
                                                ),
                                                containerStyle = ContainerStyle.Default,
                                            ),
                                            child = Container(
                                                padding = EdgeInsets.all(2),
                                                child = Column(
                                                    spacing = 2,
                                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                    children = listOf(
                                                        Text("LOCAL DEFAULT OVERRIDE"),
                                                        ValueListenableBuilder(
                                                            listenable = localThemeTapCount,
                                                        ) { _, tapCount ->
                                                            SizedBox(
                                                                height = 14,
                                                                child = OutlinedButton(
                                                                    text = "LOCAL DEFAULT $tapCount",
                                                                    onPressed = {
                                                                        localThemeTapCount.value = tapCount + 1
                                                                    },
                                                                ),
                                                            )
                                                        },
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = OutlinedButton(
                                                text = "FOCUS PRIMARY",
                                                onPressed = {
                                                    controller.requestFocus(primaryState)
                                                },
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "SELECT ALL",
                                                            onPressed = {
                                                                controller.selectAll(primaryState)
                                                                controller.requestFocus(primaryState)
                                                            },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "CURSOR END",
                                                            onPressed = {
                                                                controller.setSelection(
                                                                    state = primaryState,
                                                                    selectionStart = primaryState.text.length,
                                                                )
                                                                controller.requestFocus(primaryState)
                                                            },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = OutlinedButton(
                                                text = "NULL DISABLED",
                                                onPressed = null,
                                            ),
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                )
            },
        )
    }

    private fun singleChildScrollScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val textController = TextEditingController()
        val titleState = textController.create(initialText = "SCROLL PAGE")
        val noteState = textController.create(initialText = "DRAG TO READ")
        val footerTapped = ValueNotifier(0)

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR),
            initialTextRasterizer = textRasterizers.default,
            content = {
                SingleChildScrollView(
                    state = scrollState,
                    controller = scrollController,
                    child = Container(
                        padding = EdgeInsets.all(4),
                        child = ListenableBuilder(
                            listenable = textController,
                            builder = {
                                Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("SINGLE CHILD SCROLL"),
                                        infoCard("TITLE", titleState.text.ifEmpty { "(EMPTY)" }, accent = titleState.isFocused),
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = titleState,
                                                controller = textController,
                                                placeholder = "TYPE TITLE",
                                                style = TextFieldStyle.Default,
                                            ),
                                        ),
                                        infoCard("NOTE", noteState.text.ifEmpty { "(EMPTY)" }, accent = noteState.isFocused),
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = noteState,
                                                controller = textController,
                                                placeholder = "TYPE NOTE",
                                                style = TextFieldStyle(
                                                    borderTone = PixelTone.ON,
                                                    focusedBorderTone = PixelTone.ACCENT,
                                                    textStyle = TextStyle.Accent,
                                                    placeholderStyle = TextStyle.Default,
                                                ),
                                            ),
                                        ),
                                        infoCard("SECTION", "A LONG COLUMN"),
                                        infoCard("SCROLL", "WHOLE PAGE DRAGS"),
                                        infoCard("TARGET", "ONE CHILD TREE"),
                                        ValueListenableBuilder(
                                            listenable = footerTapped,
                                        ) { _, tapCount ->
                                            SizedBox(
                                                height = 14,
                                                child = OutlinedButton(
                                                    text = "FOOTER TAPS $tapCount",
                                                    onPressed = {
                                                        footerTapped.value = tapCount + 1
                                                    },
                                                    style = ButtonStyle.Accent,
                                                ),
                                            )
                                        },
                                        SizedBox(
                                            height = 18,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Center(
                                                    child = Text("BOTTOM CARD"),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            },
                        ),
                    ),
                )
            },
        )
    }

    private fun horizontalPagerScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PageController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = Axis.HORIZONTAL,
        )
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                Container(
                    padding = EdgeInsets.all(3),
                    child = ListenableBuilder(
                        listenable = controller,
                        builder = {
                            PageViewBuilder(
                                axis = Axis.HORIZONTAL,
                                state = state,
                                controller = controller,
                                itemCount = 3,
                                itemBuilder = { index ->
                                    when (index) {
                                        0 -> pagerPage(
                                            title = "H PAGE 1 / NOW ${state.currentPage + 1}",
                                            tone = PixelTone.ON,
                                            onPrimaryAction = {
                                                controller.nextPage(state)
                                            },
                                            primaryActionLabel = "GO 2",
                                        )
                                        1 -> pagerPage(
                                            title = "H PAGE 2 / NOW ${state.currentPage + 1}",
                                            tone = PixelTone.ACCENT,
                                            onPrimaryAction = {
                                                controller.nextPage(state)
                                            },
                                            onSecondaryAction = {
                                                controller.previousPage(state)
                                            },
                                            primaryActionLabel = "GO 3",
                                            secondaryActionLabel = "BACK 1",
                                        )
                                        else -> pagerPage(
                                            title = "H PAGE 3 / NOW ${state.currentPage + 1}",
                                            tone = PixelTone.ON,
                                            onPrimaryAction = {
                                                controller.jumpToPage(state, 0)
                                            },
                                            primaryActionLabel = "BACK 1",
                                        )
                                    }
                                },
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun verticalPagerScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = PageController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = Axis.VERTICAL,
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
                Container(
                    padding = EdgeInsets.all(3),
                    child = ListenableBuilder(
                        listenable = controller,
                        builder = {
                            PageView(
                                axis = Axis.VERTICAL,
                                state = state,
                                controller = controller,
                                pages = listOf(
                                    pagerPage(
                                        title = "V PAGE 1 / NOW ${state.currentPage + 1}",
                                        tone = PixelTone.ON,
                                        onPrimaryAction = {
                                            controller.nextPage(state)
                                        },
                                        primaryActionLabel = "GO 2",
                                    ),
                                    pagerPage(
                                        title = "V PAGE 2 / NOW ${state.currentPage + 1}",
                                        tone = PixelTone.ACCENT,
                                        onPrimaryAction = {
                                            controller.nextPage(state)
                                        },
                                        onSecondaryAction = {
                                            controller.previousPage(state)
                                        },
                                        primaryActionLabel = "GO 3",
                                        secondaryActionLabel = "BACK 1",
                                    ),
                                    pagerPage(
                                        title = "V PAGE 3 / NOW ${state.currentPage + 1}",
                                        tone = PixelTone.ON,
                                        onPrimaryAction = {
                                            controller.jumpToPage(state, 0)
                                        },
                                        primaryActionLabel = "BACK 1",
                                    ),
                                ),
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun layoutAndClickScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            content = {
                LayoutAndClickSceneWidget(
                    scrollController = scrollController,
                    scrollState = scrollState,
                )
            },
        )
    }

    private fun listScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = ScrollController()
        val state = controller.create()
        val tapCount = ValueNotifier(0)

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                ListenableBuilder(
                    listenable = controller,
                    builder = {
                        ValueListenableBuilder(
                            listenable = tapCount,
                        ) { _, taps ->
                            Container(
                                padding = EdgeInsets.all(4),
                                child = Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("VERTICAL LIST"),
                                        infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.scrollOffsetPx > 0f),
                                        infoCard("TAPS", taps.toString()),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "SHOW 1",
                                                            onPressed = {
                                                                controller.showItem(state, itemIndex = 0)
                                                            },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "SHOW 8",
                                                            onPressed = {
                                                                controller.showItem(state, itemIndex = 7)
                                                            },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "TOP",
                                                            onPressed = {
                                                                controller.jumpToStart(state)
                                                            },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "BOTTOM",
                                                            onPressed = {
                                                                controller.jumpToEnd(state)
                                                            },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 40,
                                            child = ListViewBuilder(
                                                itemCount = 8,
                                                state = state,
                                                controller = controller,
                                                itemBuilder = { index ->
                                                    SizedBox(
                                                        height = 14,
                                                        child = OutlinedButton(
                                                            text = if (index == 0) {
                                                                "TAP ITEM ${index + 1}"
                                                            } else {
                                                                "LIST ITEM ${index + 1}"
                                                            },
                                                            onPressed = {
                                                                if (index == 0) {
                                                                    tapCount.value = taps + 1
                                                                }
                                                            },
                                                            style = if (index % 2 == 0) {
                                                                ButtonStyle.Accent
                                                            } else {
                                                                ButtonStyle.Default
                                                            },
                                                        ),
                                                    )
                                                },
                                                spacing = 3,
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
        )
    }

    private fun virtualListScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = ScrollController()
        val state = controller.create()
        val selected = ValueNotifier("NONE")
        val itemCount = 1_000

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR),
            initialTextRasterizer = textRasterizers.default,
            content = {
                ListenableBuilder(controller) {
                    ValueListenableBuilder(selected) { _, current ->
                        Container(
                            padding = EdgeInsets.all(4),
                            child = Column(
                                spacing = 4,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    sectionTitle("VIRTUAL LIST"),
                                    infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.isSettling),
                                    infoCard("SELECTED", current),
                                    SizedBox(
                                        height = 14,
                                        child = Row(
                                            spacing = 2,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Expanded(
                                                    child = OutlinedButton(
                                                        text = "TOP",
                                                        onPressed = { controller.jumpToStart(state) },
                                                    ),
                                                ),
                                                Expanded(
                                                    child = OutlinedButton(
                                                        text = "500",
                                                        onPressed = { controller.showItem(state, 499) },
                                                        style = ButtonStyle.Accent,
                                                    ),
                                                ),
                                                Expanded(
                                                    child = OutlinedButton(
                                                        text = "END",
                                                        onPressed = { controller.jumpToEnd(state) },
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "FLING DOWN",
                                            onPressed = {
                                                controller.fling(state, velocityPxPerSecond = -900f)
                                            },
                                            pressed = state.isSettling,
                                        ),
                                    ),
                                    SizedBox(
                                        height = 52,
                                        child = ListViewBuilder(
                                            itemCount = itemCount,
                                            state = state,
                                            controller = controller,
                                            itemExtent = 13,
                                            cacheExtent = 2,
                                            spacing = 2,
                                            itemBuilder = { index ->
                                                val label = "ROW ${index + 1}"
                                                SizedBox(
                                                    height = 13,
                                                    child = OutlinedButton(
                                                        text = label,
                                                        onPressed = { selected.value = label },
                                                        selected = current == label,
                                                    ),
                                                )
                                            },
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            },
        )
    }

    private fun textInputMultilineScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val textController = TextEditingController()
        val noteState = textController.create(initialText = "第一行\nSECOND LINE")
        val submitted = ValueNotifier("")

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = accentUiTheme(),
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("MULTILINE INPUT"),
                        ListenableBuilder(textController) {
                            Column(
                                spacing = 4,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    infoCard("LINES", noteState.text.lines().size.toString(), accent = noteState.isFocused),
                                    ValueListenableBuilder(submitted) { _, value ->
                                        infoCard("SUBMITTED", value.ifEmpty { "(NONE)" }, accent = value.isNotEmpty())
                                    },
                                    SizedBox(
                                        height = 34,
                                        child = TextField(
                                            state = noteState,
                                            controller = textController,
                                            placeholder = "TYPE NOTE",
                                            minLines = 2,
                                            maxLines = 4,
                                            textInputAction = TextInputAction.DONE,
                                            onSubmitted = { text ->
                                                submitted.value = text.take(24)
                                            },
                                        ),
                                    ),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "SELECT ALL",
                                            onPressed = {
                                                textController.selectAll(noteState)
                                                textController.requestFocus(noteState)
                                            },
                                        ),
                                    ),
                                ),
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun richTextScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("RICH TEXT"),
                        SizedBox(
                            height = 28,
                            child = DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ACCENT,
                                padding = 2,
                                alignment = Alignment.TOP_START,
                                child = RichText(
                                    spans = listOf(
                                        PixelTextSpan("PIXEL ", TextStyle.Accent),
                                        PixelTextSpan("ENGINE ", TextStyle.Default),
                                        PixelTextSpan(
                                            text = "富文本 MIXED WRAP",
                                            style = TextStyle(
                                                tone = PixelTone.ACCENT,
                                                textRasterizer = textRasterizers.emphasis,
                                            ),
                                        ),
                                    ),
                                    softWrap = true,
                                    maxLines = 3,
                                    overflow = TextOverflow.ELLIPSIS,
                                ),
                            ),
                        ),
                        infoCard("STYLE", "SPAN TONE SWITCH"),
                        infoCard("WRAP", "CJK AND ASCII"),
                    ),
                )
            },
        )
    }

    private fun themeStatesScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val textController = TextEditingController()
        val inputState = textController.create(initialText = "FOCUSED")
        val readOnlyState = textController.create(initialText = "READ ONLY")
        val selected = ValueNotifier(false)
        val stateTheme = ThemeData(
            tokens = PixelThemeTokens(
                textTone = PixelTone.ON,
                accentTone = PixelTone.ACCENT,
                borderTone = PixelTone.ON,
                selectedBorderTone = PixelTone.ACCENT,
                pressedBorderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                disabledBorderTone = PixelTone.ON,
                readOnlyBorderTone = PixelTone.ACCENT,
            ),
        )

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = stateTheme,
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("THEME STATES"),
                        ValueListenableBuilder(selected) { _, isSelected ->
                            Column(
                                spacing = 4,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "SELECTED ${if (isSelected) "ON" else "OFF"}",
                                            onPressed = { selected.value = !isSelected },
                                            selected = isSelected,
                                        ),
                                    ),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "PRESSED STYLE",
                                            onPressed = { },
                                            pressed = true,
                                        ),
                                    ),
                                    Container(
                                        height = 20,
                                        selected = isSelected,
                                        pressed = !isSelected,
                                        padding = EdgeInsets.all(3),
                                        child = Text(if (isSelected) "CONTAINER SELECTED" else "CONTAINER PRESSED"),
                                    ),
                                    SizedBox(
                                        height = 16,
                                        child = TextField(
                                            state = inputState,
                                            controller = textController,
                                            placeholder = "FOCUS ME",
                                            autofocus = true,
                                        ),
                                    ),
                                    SizedBox(
                                        height = 16,
                                        child = TextField(
                                            state = readOnlyState,
                                            controller = textController,
                                            readOnly = true,
                                        ),
                                    ),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "DISABLED",
                                            onPressed = null,
                                        ),
                                    ),
                                ),
                            )
                        },
                    ),
                )
            },
        )
    }

    private fun engineStabilityGateScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        val pagerController = PageController()
        val pagerState = pagerController.create(
            pageCount = 2,
            currentPage = 0,
            axis = Axis.VERTICAL,
        )
        val lazyController = ScrollController()
        val lazyState = lazyController.create(initialScrollOffsetPx = 28f)
        val innerScrollController = ScrollController()
        val innerScrollState = innerScrollController.create()
        val inputController = TextEditingController()
        val inputState = inputController.create(initialText = "ENGINE\nREADY")
        val selected = ValueNotifier("ROW 3")
        val gateTheme = ThemeData(
            tokens = PixelThemeTokens(
                textTone = PixelTone.ON,
                accentTone = PixelTone.ACCENT,
                borderTone = PixelTone.ON,
                selectedBorderTone = PixelTone.ACCENT,
                pressedBorderTone = PixelTone.ACCENT,
                focusedBorderTone = PixelTone.ACCENT,
                readOnlyBorderTone = PixelTone.ACCENT,
            ),
        )

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = gateTheme,
            content = {
                Theme(
                    data = gateTheme,
                    child = scrollableRoot(
                        state = scrollState,
                        controller = scrollController,
                        children = listOf(
                            sectionTitle("ENGINE STABILITY"),
                            DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ACCENT,
                                padding = 2,
                                alignment = Alignment.TOP_START,
                                child = RichText(
                                    spans = listOf(
                                        PixelTextSpan("LAYOUT ", TextStyle.Accent),
                                        PixelTextSpan("LAZY LIST ", TextStyle.Default),
                                        PixelTextSpan(
                                            text = "富文本 INPUT THEME",
                                            style = TextStyle(
                                                tone = PixelTone.ACCENT,
                                                textRasterizer = textRasterizers.emphasis,
                                            ),
                                        ),
                                    ),
                                    softWrap = true,
                                    maxLines = 3,
                                    overflow = TextOverflow.ELLIPSIS,
                                ),
                            ),
                            ValueListenableBuilder(selected) { _, current ->
                                Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        Row(
                                            spacing = 3,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Expanded(
                                                    child = infoCard("SELECTED", current, accent = true),
                                                ),
                                                Expanded(
                                                    child = Container(
                                                        height = 18,
                                                        selected = current.endsWith("3"),
                                                        pressed = !current.endsWith("3"),
                                                        padding = EdgeInsets.all(2),
                                                        child = Text("STATE BOX"),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 46,
                                            child = ListViewBuilder(
                                                itemCount = 80,
                                                state = lazyState,
                                                controller = lazyController,
                                                itemExtent = 12,
                                                cacheExtent = 2,
                                                spacing = 2,
                                                itemBuilder = { index ->
                                                    val label = "ROW ${index + 1}"
                                                    SizedBox(
                                                        height = 12,
                                                        child = OutlinedButton(
                                                            text = label,
                                                            onPressed = { selected.value = label },
                                                            selected = label == current,
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "TOP",
                                                            onPressed = { lazyController.jumpToStart(lazyState) },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "END",
                                                            onPressed = { lazyController.jumpToEnd(lazyState) },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "FLING",
                                                            onPressed = {
                                                                lazyController.fling(
                                                                    state = lazyState,
                                                                    velocityPxPerSecond = -360f,
                                                                )
                                                            },
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                )
                            },
                            SizedBox(
                                height = 32,
                                child = TextField(
                                    state = inputState,
                                    controller = inputController,
                                    placeholder = "MULTILINE",
                                    minLines = 2,
                                    maxLines = 3,
                                    textInputAction = TextInputAction.DONE,
                                ),
                            ),
                            SizedBox(
                                height = 58,
                                child = PageView(
                                    axis = Axis.VERTICAL,
                                    state = pagerState,
                                    controller = pagerController,
                                    pages = listOf(
                                        DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            padding = 2,
                                            child = Column(
                                                spacing = 3,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Text("PAGER + LIST", style = TextStyle.Accent),
                                                    infoCard("NESTED", "SCROLL HANDOFF", accent = true),
                                                    SizedBox(
                                                        height = 14,
                                                        child = OutlinedButton(
                                                            text = "NEXT PAGE",
                                                            onPressed = { pagerController.nextPage(pagerState) },
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ACCENT,
                                            padding = 2,
                                            child = SingleChildScrollView(
                                                state = innerScrollState,
                                                controller = innerScrollController,
                                                child = Column(
                                                    spacing = 3,
                                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                    children = listOf(
                                                        Text("INNER SCROLL", style = TextStyle.Accent),
                                                        infoCard("TEXT", "RICH + INPUT READY"),
                                                        infoCard("THEME", "TOKENS ACTIVE", accent = true),
                                                        SizedBox(
                                                            height = 14,
                                                            child = OutlinedButton(
                                                                text = "PREV PAGE",
                                                                onPressed = { pagerController.previousPage(pagerState) },
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
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

    private fun formAndListScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val textController = TextEditingController()
        val nameState = textController.create(initialText = "PIXEL")
        val cityState = textController.create(initialText = "SHANGHAI")
        val listController = ScrollController()
        val listState = listController.create()
        val accentTheme = accentUiTheme()
        val selectedLabel = ValueNotifier("ITEM 1")

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = accentTheme,
            content = {
                ListenableBuilder(
                    listenable = textController,
                    builder = {
                        ListenableBuilder(
                            listenable = listController,
                            builder = {
                                ValueListenableBuilder(
                                    listenable = selectedLabel,
                                ) { _, currentSelection ->
                                    Container(
                                        padding = EdgeInsets.all(4),
                                        child = Column(
                                            spacing = 4,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                sectionTitle("FORM + LIST"),
                                                infoCard("NAME", nameState.text.ifEmpty { "(EMPTY)" }, accent = nameState.isFocused),
                                                infoCard("CITY", cityState.text.ifEmpty { "(EMPTY)" }, accent = cityState.isFocused),
                                                infoCard("SELECTED", currentSelection),
                                                Column(
                                                    spacing = 2,
                                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                    children = listOf(
                                                        SizedBox(
                                                            height = 16,
                                                            child = TextField(
                                                                state = nameState,
                                                                controller = textController,
                                                                placeholder = "TYPE NAME",
                                                            ),
                                                        ),
                                                        SizedBox(
                                                            height = 16,
                                                            child = TextField(
                                                                state = cityState,
                                                                controller = textController,
                                                                placeholder = "TYPE CITY",
                                                            ),
                                                        ),
                                                        SizedBox(
                                                            height = 14,
                                                            child = Row(
                                                                spacing = 2,
                                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                                children = listOf(
                                                                    Expanded(
                                                                        child = OutlinedButton(
                                                                            text = "SHOW 1",
                                                                            onPressed = {
                                                                                listController.showItem(listState, itemIndex = 0)
                                                                            },
                                                                        ),
                                                                    ),
                                                                    Expanded(
                                                                        child = OutlinedButton(
                                                                            text = "SHOW 6",
                                                                            onPressed = {
                                                                                listController.showItem(listState, itemIndex = 5)
                                                                            },
                                                                        ),
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                                SizedBox(
                                                    height = 24,
                                                    child = ListView(
                                                        state = listState,
                                                        controller = listController,
                                                        spacing = 3,
                                                        items = List(6) { index ->
                                                            val label = "ITEM ${index + 1}"
                                                            SizedBox(
                                                                height = 14,
                                                                child = OutlinedButton(
                                                                    text = label,
                                                                    onPressed = {
                                                                        selectedLabel.value = label
                                                                    },
                                                                    style = if (currentSelection == label) {
                                                                        ButtonStyle.Accent
                                                                    } else {
                                                                        ButtonStyle.Default
                                                                    },
                                                                ),
                                                            )
                                                        },
                                                    ),
                                                ),
                                            ),
                                        ),
                                    )
                                }
                            },
                        )
                    },
                )
            },
        )
    }

    private fun pagerAndListScene(
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
        val itemTapCount = ValueNotifier(0)

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            content = {
                ValueListenableBuilder(
                    listenable = itemTapCount,
                    builder = { _, tapCount ->
                        PageView(
                            axis = Axis.VERTICAL,
                            state = pagerState,
                            controller = pagerController,
                            pages = listOf(
                                pagerPage(
                                    title = "OUTER PAGE 1",
                                    tone = PixelTone.ACCENT,
                                    onPrimaryAction = {
                                        pagerController.nextPage(pagerState)
                                    },
                                    primaryActionLabel = "GO LIST",
                                ),
                                DecoratedBox(
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ACCENT,
                                    child = Container(
                                        padding = EdgeInsets.all(3),
                                        child = Column(
                                            spacing = 4,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Text("PAGE 2 LIST", style = TextStyle.Accent),
                                                infoCard(
                                                    label = "TIP",
                                                    value = "LIST FIRST THEN PAGE",
                                                    accent = true,
                                                ),
                                                infoCard(
                                                    label = "TAPS",
                                                    value = tapCount.toString(),
                                                    accent = tapCount > 0,
                                                ),
                                                SizedBox(
                                                    height = 56,
                                                    child = ListView(
                                                        state = listState,
                                                        controller = listController,
                                                        spacing = 3,
                                                        items = List(7) { index ->
                                                            SizedBox(
                                                                height = 14,
                                                                child = OutlinedButton(
                                                                    text = "INNER ITEM ${index + 1}",
                                                                    onPressed = {
                                                                        itemTapCount.value = tapCount + 1
                                                                    },
                                                                    style = if (index % 2 == 0) {
                                                                        ButtonStyle.Accent
                                                                    } else {
                                                                        ButtonStyle.Default
                                                                    },
                                                                ),
                                                            )
                                                        },
                                                    ),
                                                ),
                                                SizedBox(
                                                    height = 14,
                                                    child = OutlinedButton(
                                                        text = "BACK PAGE 1",
                                                        onPressed = {
                                                            pagerController.previousPage(pagerState)
                                                        },
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    },
                )
            },
        )
    }

    private fun scrollStressScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val controller = ScrollController()
        val state = controller.create()
        val selected = ValueNotifier("NONE")
        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 84,
                logicalHeight = 112,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            content = {
                ListenableBuilder(
                    listenable = controller,
                    builder = {
                        ValueListenableBuilder(selected) { _, current ->
                            Container(
                                padding = EdgeInsets.all(4),
                                child = Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("SCROLL STRESS"),
                                        infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.isSettling),
                                        infoCard("SELECTED", current),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "SHOW 1",
                                                            onPressed = { controller.showItem(state, 0) },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "SHOW 20",
                                                            onPressed = { controller.showItem(state, 19) },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 52,
                                            child = ListViewBuilder(
                                                itemCount = 20,
                                                state = state,
                                                controller = controller,
                                                spacing = 2,
                                                itemBuilder = { index ->
                                                    val tall = index % 4 == 0
                                                    SizedBox(
                                                        height = if (tall) 22 else 14,
                                                        child = OutlinedButton(
                                                            text = "ROW ${index + 1} ${if (tall) "TALL" else "COMPACT"}",
                                                            onPressed = { selected.value = "ROW ${index + 1}" },
                                                            style = if (index % 3 == 0) ButtonStyle.Accent else ButtonStyle.Default,
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    },
                )
            },
        )
    }

    private fun environmentScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val scrollController = ScrollController()
        val scrollState = scrollController.create()
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.ICE_LCD),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = ThemeData(
                textStyle = TextStyle.Accent,
                containerStyle = ContainerStyle(
                    fillTone = PixelTone.OFF,
                    borderTone = PixelTone.ACCENT,
                    alignment = Alignment.CENTER,
                ),
            ),
            content = {
                scrollableRoot(
                    state = scrollState,
                    controller = scrollController,
                    children = listOf(
                        sectionTitle("ENVIRONMENT"),
                        EnvironmentInfoWidget(label = "HOST DEFAULT"),
                        Directionality(
                            textDirection = TextDirection.RTL,
                            child = EnvironmentInfoWidget(label = "LOCAL RTL"),
                        ),
                        Theme(
                            data = ThemeData(
                                textStyle = TextStyle.Default,
                                containerStyle = ContainerStyle(
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    alignment = Alignment.CENTER,
                                ),
                            ),
                            child = EnvironmentInfoWidget(label = "LOCAL THEME"),
                        ),
                        Directionality(
                            textDirection = TextDirection.RTL,
                            child = Theme(
                                data = ThemeData(
                                    textStyle = TextStyle.Accent,
                                    buttonStyle = ButtonStyle.Accent,
                                ),
                                child = Column(
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        EnvironmentInfoWidget(label = "NESTED BOTH"),
                                        SizedBox(
                                            height = 14,
                                            child = OutlinedButton(
                                                text = "THEMED ACTION",
                                                onPressed = { },
                                            ),
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

    private fun launcherLikeScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val textController = TextEditingController()
        val queryState = textController.create()
        val listController = ScrollController()
        val listState = listController.create()
        val mode = ValueNotifier("HOME")
        val apps = listOf(
            "Calendar", "Camera", "Messages", "Music", "Maps", "Settings",
            "Weather", "Clock", "Notes", "Terminal", "Browser", "Files",
        )
        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 96,
                logicalHeight = 128,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.terminalGreen(),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = accentUiTheme(),
            content = {
                ListenableBuilder(textController) {
                    ListenableBuilder(listController) {
                        ValueListenableBuilder(mode) { _, currentMode ->
                            val filteredApps = apps.filter { app ->
                                app.contains(queryState.text, ignoreCase = true)
                            }
                            Container(
                                padding = EdgeInsets.all(4),
                                child = Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("PIXEL LAUNCHER"),
                                        Row(
                                            spacing = 3,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Expanded(child = infoCard("MODE", currentMode, accent = currentMode == "DRAWER")),
                                                Expanded(child = infoCard("SCREEN", "42M")),
                                            ),
                                        ),
                                        infoCard(
                                            label = "STATUS",
                                            value = "CALL 0 / SMS 2 / RAIN 30MIN WINDOW",
                                            accent = true,
                                        ),
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = queryState,
                                                controller = textController,
                                                placeholder = "SEARCH APP",
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "HOME",
                                                            onPressed = { mode.value = "HOME" },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "DRAWER",
                                                            onPressed = { mode.value = "DRAWER" },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 46,
                                            child = ListViewBuilder(
                                                itemCount = filteredApps.size.coerceAtLeast(1),
                                                state = listState,
                                                controller = listController,
                                                spacing = 2,
                                                itemBuilder = { index ->
                                                    val label = filteredApps.getOrNull(index) ?: "NO RESULTS"
                                                    SizedBox(
                                                        height = 13,
                                                        child = OutlinedButton(
                                                            text = label.uppercase(),
                                                            onPressed = {
                                                                mode.value = "OPEN $label"
                                                            },
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun drawerLikeScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val textController = TextEditingController()
        val queryState = textController.create()
        val listController = ScrollController()
        val listState = listController.create()
        val selectedApp = ValueNotifier("NONE")
        val mockApps = listOf(
            "Alarm", "Browser", "Calculator", "Calendar", "Camera", "Clock",
            "Contacts", "Files", "Gallery", "Maps", "Messages", "Music",
            "Notes", "Phone", "Photos", "Recorder", "Settings", "Terminal",
            "Translator", "Weather", "WeChat", "YouTube", "Zen Mode", "Zero Mail",
        )

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 96,
                logicalHeight = 128,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = accentUiTheme(),
            content = {
                ListenableBuilder(textController) {
                    ListenableBuilder(listController) {
                        ValueListenableBuilder(selectedApp) { _, selected ->
                            val query = queryState.text.trim()
                            val filteredApps = mockApps.filter { app ->
                                query.isEmpty() || app.contains(query, ignoreCase = true)
                            }
                            Container(
                                padding = EdgeInsets.all(4),
                                child = Column(
                                    spacing = 4,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("DRAWER GATE"),
                                        infoCard("QUERY", query.ifEmpty { "(EMPTY)" }),
                                        infoCard("RESULTS", filteredApps.size.toString(), accent = filteredApps.isEmpty()),
                                        infoCard("SELECTED", selected),
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = queryState,
                                                controller = textController,
                                                placeholder = "TYPE TO FILTER",
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "TOP",
                                                            onPressed = { listController.jumpToStart(listState) },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "MID",
                                                            onPressed = {
                                                                listController.showItem(
                                                                    state = listState,
                                                                    itemIndex = (filteredApps.size / 2).coerceAtLeast(0),
                                                                )
                                                            },
                                                            style = ButtonStyle.Accent,
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "END",
                                                            onPressed = { listController.jumpToEnd(listState) },
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 46,
                                            child = ListViewBuilder(
                                                itemCount = filteredApps.size.coerceAtLeast(1),
                                                state = listState,
                                                controller = listController,
                                                spacing = 2,
                                                itemBuilder = { index ->
                                                    val label = filteredApps.getOrNull(index)
                                                    SizedBox(
                                                        height = 13,
                                                        child = OutlinedButton(
                                                            text = label?.uppercase() ?: "NO RESULTS",
                                                            onPressed = label?.let { app ->
                                                                { selectedApp.value = app }
                                                            },
                                                            style = if (label == selected) {
                                                                ButtonStyle.Accent
                                                            } else {
                                                                ButtonStyle.Default
                                                            },
                                                            enabled = label != null,
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            },
        )
    }

    private fun drawerGateV2Scene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val textController = TextEditingController()
        val queryState = textController.create()
        val listController = ScrollController()
        val listState = listController.create()
        val selectedApp = ValueNotifier("NONE")
        val prefixes = listOf(
            "Alarm", "Browser", "Calendar", "Drive", "Editor", "Files",
            "Gallery", "Health", "Inbox", "Journal", "Keyboard", "Launcher",
            "Maps", "Notes", "Office", "Photos", "Reader", "Settings",
            "Tasks", "Weather", "Wallet", "YouTube", "Zen",
        )
        val mockApps = List(180) { index ->
            val letter = 'A' + (index % 26)
            "$letter ${prefixes[index % prefixes.size]} ${index + 1}"
        }.sorted()
        val indexLetters = listOf("A", "M", "Z")

        return DemoScene(
            initialProfile = ScreenProfile(
                logicalWidth = 96,
                logicalHeight = 128,
                dotSizePx = 8,
            ),
            initialPalette = PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR),
            initialTextRasterizer = textRasterizers.default,
            initialThemeData = ThemeData(
                tokens = PixelThemeTokens(
                    textTone = PixelTone.ON,
                    accentTone = PixelTone.ACCENT,
                    borderTone = PixelTone.ON,
                    selectedBorderTone = PixelTone.ACCENT,
                    focusedBorderTone = PixelTone.ACCENT,
                    disabledBorderTone = PixelTone.ON,
                    readOnlyBorderTone = PixelTone.ACCENT,
                ),
            ),
            content = {
                ListenableBuilder(textController) {
                    ListenableBuilder(listController) {
                        ValueListenableBuilder(selectedApp) { _, selected ->
                            val query = queryState.text.trim()
                            val filteredApps = mockApps.filter { app ->
                                query.isEmpty() || app.contains(query, ignoreCase = true)
                            }
                            Container(
                                padding = EdgeInsets.all(4),
                                child = Column(
                                    spacing = 3,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    children = listOf(
                                        sectionTitle("DRAWER GATE V2"),
                                        Row(
                                            spacing = 2,
                                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                            children = listOf(
                                                Expanded(
                                                    child = infoCard(
                                                        label = "RESULTS",
                                                        value = filteredApps.size.toString(),
                                                        accent = filteredApps.isEmpty(),
                                                    ),
                                                ),
                                                Expanded(
                                                    child = infoCard(
                                                        label = "SELECTED",
                                                        value = selected,
                                                        accent = selected != "NONE",
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 16,
                                            child = TextField(
                                                state = queryState,
                                                controller = textController,
                                                placeholder = "SEARCH APP",
                                                textInputAction = TextInputAction.SEARCH,
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = listOf(
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "TOP",
                                                            onPressed = { listController.jumpToStart(listState) },
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "MID",
                                                            onPressed = {
                                                                listController.showItem(
                                                                    state = listState,
                                                                    itemIndex = (filteredApps.size / 2).coerceAtLeast(0),
                                                                )
                                                            },
                                                            selected = true,
                                                        ),
                                                    ),
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = "END",
                                                            onPressed = { listController.jumpToEnd(listState) },
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        SizedBox(
                                            height = 14,
                                            child = Row(
                                                spacing = 2,
                                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                                children = indexLetters.map { letter ->
                                                    Expanded(
                                                        child = OutlinedButton(
                                                            text = letter,
                                                            onPressed = {
                                                                val index = filteredApps.indexOfFirst { app ->
                                                                    app.startsWith(letter)
                                                                }
                                                                if (index >= 0) {
                                                                    listController.showItem(listState, index)
                                                                }
                                                            },
                                                            pressed = selected.startsWith(letter),
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                        SizedBox(
                                            height = 42,
                                            child = ListViewBuilder(
                                                itemCount = filteredApps.size.coerceAtLeast(1),
                                                state = listState,
                                                controller = listController,
                                                itemExtent = 13,
                                                cacheExtent = 2,
                                                spacing = 2,
                                                itemBuilder = { index ->
                                                    val label = filteredApps.getOrNull(index)
                                                    SizedBox(
                                                        height = 13,
                                                        child = OutlinedButton(
                                                            text = label ?: "NO RESULTS",
                                                            onPressed = label?.let { app ->
                                                                { selectedApp.value = app }
                                                            },
                                                            selected = label == selected,
                                                            enabled = label != null,
                                                        ),
                                                    )
                                                },
                                            ),
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
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
            height = titleHeight + 6,
            fillTone = PixelTone.OFF,
            borderTone = PixelTone.ACCENT,
            padding = EdgeInsets.all(3),
            child = Center(
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

    private class ScopedCounterScope(
        notifier: ValueNotifier<Int>,
        override val child: Widget,
    ) : InheritedNotifier<ValueNotifier<Int>>(
        notifier = notifier,
        child = child,
    ) {
        companion object {
            fun of(context: BuildContext): Int {
                return context.dependOnInheritedWidgetOfExactType<ScopedCounterScope>()
                    ?.notifier
                    ?.value
                    ?: 0
            }
        }
    }

    private class ScopedCounterInfoWidget : StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            val count = ScopedCounterScope.of(context)
            return infoCard(
                label = "INHERITED NOTIFIER",
                value = "COUNT $count",
                accent = count > 0,
            )
        }
    }

    private fun infoCard(
        label: String,
        value: String,
        accent: Boolean = false,
        valueRasterizer: PixelTextRasterizer? = null,
    ): Widget {
        val labelRasterizer = PixelBitmapFont.Default
        val resolvedValueRasterizer = valueRasterizer ?: PixelBitmapFont.Default
        val cardHeight = labelRasterizer.measureHeight(label) +
            resolvedValueRasterizer.measureHeight(value) +
            8

        return Container(
            height = cardHeight,
            fillTone = PixelTone.OFF,
            borderTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
            padding = EdgeInsets.all(3),
            child = Column(
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    Text(
                        data = label,
                        style = TextStyle.Accent,
                        overflow = TextOverflow.ELLIPSIS,
                    ),
                    Text(
                        data = value,
                        style = TextStyle(
                            tone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                            textRasterizer = valueRasterizer,
                        ),
                        overflow = TextOverflow.ELLIPSIS,
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
        fillTone = PixelTone.OFF,
        borderTone = tone,
        padding = EdgeInsets.all(3),
        child = Column(
            spacing = 4,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            children = buildList {
                add(
                    SizedBox(
                        height = 20,
                        child = Center(
                            child = Text(
                                title,
                                style = TextStyle(tone = tone),
                            ),
                        ),
                    )
                )
                add(
                    SizedBox(
                        height = 14,
                        child = OutlinedButton(
                            text = primaryActionLabel,
                            onPressed = onPrimaryAction,
                        ),
                    ),
                )
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    add(
                        SizedBox(
                            height = 14,
                            child = OutlinedButton(
                                text = secondaryActionLabel,
                                onPressed = onSecondaryAction,
                            ),
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
            child = Container(
                padding = EdgeInsets.all(4),
                child = Column(
                    spacing = 4,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    children = children,
                ),
            ),
        )
    }

    private class EnvironmentInfoWidget(
        private val label: String,
    ) : StatelessWidget() {
        override fun build(context: BuildContext): Widget {
            val mediaQuery = MediaQuery.of(context)
            val direction = Directionality.of(context)
            val theme = Theme.of(context)
            val directionLabel = when (direction) {
                TextDirection.LTR -> "LTR"
                TextDirection.RTL -> "RTL"
            }
            val themeLabel = when (theme.textStyle.tone) {
                PixelTone.ACCENT -> "ACCENT"
                PixelTone.ON -> "ON"
                else -> "OFF"
            }
            return infoCard(
                label = label,
                value = "${mediaQuery.logicalWidth}x${mediaQuery.logicalHeight} $directionLabel $themeLabel",
                accent = direction == TextDirection.RTL,
            )
        }
    }

    private class PaletteSceneWidget(
        private val hostView: PixelHostView,
        private val applyPreferredProfile: (ScreenProfile) -> Unit,
        private val scrollController: ScrollController,
        private val scrollState: com.purride.pixelui.state.PixelListState,
    ) : StatefulWidget() {
        override fun createState(): State<out StatefulWidget> = PaletteSceneState()

        private class PaletteSceneState : State<PaletteSceneWidget>() {
            private val themes = PixelTheme.entries
            private val shapes = PixelShape.entries
            private var currentThemeIndex = 0
            private var currentShapeIndex = 0

            override fun initState() {
                super.initState()
                applyHostAppearance()
            }

            override fun build(context: BuildContext): Widget {
                return scrollableRoot(
                    state = widget.scrollState,
                    controller = widget.scrollController,
                    children = listOf(
                        sectionTitle("PALETTE AND SHAPE"),
                        infoCard("THEME", themes[currentThemeIndex].name),
                        infoCard("SHAPE", shapes[currentShapeIndex].name),
                        SizedBox(
                            height = 14,
                            child = OutlinedButton(
                                text = "NEXT THEME",
                                onPressed = {
                                    setState {
                                        currentThemeIndex = (currentThemeIndex + 1) % themes.size
                                    }
                                    applyHostAppearance()
                                },
                            ),
                        ),
                        SizedBox(
                            height = 14,
                            child = OutlinedButton(
                                text = "NEXT SHAPE",
                                onPressed = {
                                    setState {
                                        currentShapeIndex = (currentShapeIndex + 1) % shapes.size
                                    }
                                    applyHostAppearance()
                                },
                            ),
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
            }

            private fun applyHostAppearance() {
                widget.hostView.setPalette(PixelPalette.fromTheme(themes[currentThemeIndex]))
                widget.applyPreferredProfile(defaultProfile(pixelShape = shapes[currentShapeIndex]))
            }
        }
    }

    private class LayoutAndClickSceneWidget(
        private val scrollController: ScrollController,
        private val scrollState: com.purride.pixelui.state.PixelListState,
    ) : StatefulWidget() {
        override fun createState(): State<out StatefulWidget> = LayoutAndClickSceneState()

        private class LayoutAndClickSceneState : State<LayoutAndClickSceneWidget>() {
            private var count = 0
            private var accentMode = false

            private val themedCard = ThemeData(
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

            override fun build(context: BuildContext): Widget {
                return scrollableRoot(
                    state = widget.scrollState,
                    controller = widget.scrollController,
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
                                child = Text("MARGIN", style = TextStyle.Accent),
                            ),
                        ),
                        Theme(
                            data = themedCard,
                            child = Column(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "INCREASE",
                                            onPressed = {
                                                setState {
                                                    count += 1
                                                }
                                            },
                                        ),
                                    ),
                                    SizedBox(
                                        height = 14,
                                        child = OutlinedButton(
                                            text = "THEMED DISABLED",
                                            onPressed = null,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 14,
                            child = OutlinedButton(
                                text = "TOGGLE ACCENT",
                                onPressed = {
                                    setState {
                                        accentMode = !accentMode
                                    }
                                },
                            ),
                        ),
                        Row(
                            spacing = 4,
                            children = listOf(
                                demoSquare("LEFT"),
                                demoSquare("RIGHT"),
                            ),
                        ),
                        SizedBox(
                            height = 18,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Center(
                                                child = Text("1X"),
                                            ),
                                        ),
                                    ),
                                    Flexible(
                                        flex = 2,
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ACCENT,
                                            child = Center(
                                                child = Text("2X", style = TextStyle.Accent),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 18,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Center(
                                                child = Text("TIGHT"),
                                            ),
                                        ),
                                    ),
                                    Flexible(
                                        flex = 2,
                                        fit = FlexFit.LOOSE,
                                        child = SizedBox(
                                            width = 18,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ACCENT,
                                                child = Center(
                                                    child = Text("LOOSE", style = TextStyle.Accent),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 20,
                            child = Row(
                                spacing = 2,
                                mainAxisAlignment = MainAxisAlignment.CENTER,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                                children = listOf(
                                    SizedBox(
                                        width = 18,
                                        height = 8,
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Center(
                                                child = Text("MID"),
                                            ),
                                        ),
                                    ),
                                    SizedBox(
                                        width = 18,
                                        height = 14,
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ACCENT,
                                            child = Center(
                                                child = Text("TALL", style = TextStyle.Accent),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 18,
                            child = Row(
                                mainAxisSize = MainAxisSize.MAX,
                                mainAxisAlignment = MainAxisAlignment.SPACE_BETWEEN,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    SizedBox(
                                        width = 18,
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Center(
                                                child = Text("BETWEEN"),
                                            ),
                                        ),
                                    ),
                                    SizedBox(
                                        width = 18,
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ACCENT,
                                            child = Center(
                                                child = Text("EVEN", style = TextStyle.Accent),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = GestureDetector(
                                onTap = {
                                    setState {
                                        accentMode = !accentMode
                                    }
                                },
                                child = DecoratedBox(
                                    fillTone = PixelTone.OFF,
                                    borderTone = PixelTone.ON,
                                    padding = 2,
                                    child = Column(
                                        spacing = 2,
                                        mainAxisSize = MainAxisSize.MAX,
                                        mainAxisAlignment = MainAxisAlignment.END,
                                        crossAxisAlignment = CrossAxisAlignment.END,
                                        children = listOf(
                                            SizedBox(
                                                width = 16,
                                                height = 6,
                                                child = DecoratedBox(
                                                    fillTone = PixelTone.OFF,
                                                    borderTone = PixelTone.ON,
                                                    child = Center(
                                                        child = Text("END"),
                                                    ),
                                                ),
                                            ),
                                            SizedBox(
                                                width = 24,
                                                height = 6,
                                                child = DecoratedBox(
                                                    fillTone = PixelTone.OFF,
                                                    borderTone = PixelTone.ACCENT,
                                                    child = Align(
                                                        alignment = Alignment.CENTER,
                                                        child = Text("ALIGN", style = TextStyle.Accent),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 22,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Align(
                                                alignment = Alignment.TOP_CENTER,
                                                child = SizedBox(
                                                    width = 14,
                                                    height = 8,
                                                    child = DecoratedBox(
                                                        fillTone = PixelTone.OFF,
                                                        borderTone = PixelTone.ACCENT,
                                                        child = Center(
                                                            child = Text("TOP", style = TextStyle.Accent),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = DecoratedBox(
                                            fillTone = PixelTone.OFF,
                                            borderTone = PixelTone.ON,
                                            child = Align(
                                                alignment = Alignment.BOTTOM_END,
                                                child = SizedBox(
                                                    width = 14,
                                                    height = 8,
                                                    child = DecoratedBox(
                                                        fillTone = PixelTone.OFF,
                                                        borderTone = PixelTone.ACCENT,
                                                        child = Center(
                                                            child = Text("END", style = TextStyle.Accent),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 22,
                            child = DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                                child = Stack(
                                    children = listOf(
                                        Positioned(
                                            left = 2,
                                            top = 2,
                                            child = SizedBox(
                                                width = 14,
                                                height = 8,
                                                child = DecoratedBox(
                                                    fillTone = PixelTone.OFF,
                                                    borderTone = PixelTone.ACCENT,
                                                    child = Center(
                                                        child = Text("BADGE", style = TextStyle.Accent),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        Positioned(
                                            right = 2,
                                            bottom = 2,
                                            child = SizedBox(
                                                width = 12,
                                                height = 8,
                                                child = DecoratedBox(
                                                    fillTone = PixelTone.OFF,
                                                    borderTone = PixelTone.ON,
                                                    child = Center(
                                                        child = Text("PIN"),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 22,
                            child = DecoratedBox(
                                fillTone = PixelTone.OFF,
                                borderTone = PixelTone.ON,
                                child = Stack(
                                    children = listOf(
                                        PositionedFill(
                                            left = 2,
                                            top = 2,
                                            right = 2,
                                            bottom = 2,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ACCENT,
                                            ),
                                        ),
                                        Positioned(
                                            right = 3,
                                            top = 3,
                                            child = SizedBox(
                                                width = 10,
                                                height = 6,
                                                child = Center(
                                                    child = Text("FILL", style = TextStyle.Accent),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.LTR,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Stack(
                                                    children = listOf(
                                                        PositionedDirectional(
                                                            start = 2,
                                                            top = 2,
                                                            child = SizedBox(
                                                                width = 10,
                                                                height = 8,
                                                                child = DecoratedBox(
                                                                    fillTone = PixelTone.OFF,
                                                                    borderTone = PixelTone.ACCENT,
                                                                    child = Center(
                                                                        child = Text("LTR", style = TextStyle.Accent),
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.RTL,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Stack(
                                                    children = listOf(
                                                        PositionedDirectional(
                                                            start = 2,
                                                            top = 2,
                                                            child = SizedBox(
                                                                width = 10,
                                                                height = 8,
                                                                child = DecoratedBox(
                                                                    fillTone = PixelTone.OFF,
                                                                    borderTone = PixelTone.ACCENT,
                                                                    child = Center(
                                                                        child = Text("RTL", style = TextStyle.Accent),
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.LTR,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Row(
                                                    mainAxisSize = MainAxisSize.MAX,
                                                    mainAxisAlignment = MainAxisAlignment.START,
                                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                                    children = listOf(
                                                        SizedBox(
                                                            width = 10,
                                                            height = 8,
                                                            child = DecoratedBox(
                                                                fillTone = PixelTone.OFF,
                                                                borderTone = PixelTone.ACCENT,
                                                                child = Center(
                                                                    child = Text("LTR", style = TextStyle.Accent),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.RTL,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Row(
                                                    mainAxisSize = MainAxisSize.MAX,
                                                    mainAxisAlignment = MainAxisAlignment.START,
                                                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                                                    children = listOf(
                                                        SizedBox(
                                                            width = 10,
                                                            height = 8,
                                                            child = DecoratedBox(
                                                                fillTone = PixelTone.OFF,
                                                                borderTone = PixelTone.ACCENT,
                                                                child = Center(
                                                                    child = Text("RTL", style = TextStyle.Accent),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.LTR,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = AlignDirectional(
                                                    alignment = AlignmentDirectional.BOTTOM_START,
                                                    child = SizedBox(
                                                        width = 12,
                                                        height = 8,
                                                        child = DecoratedBox(
                                                            fillTone = PixelTone.OFF,
                                                            borderTone = PixelTone.ACCENT,
                                                            child = Center(
                                                                child = Text("LTR", style = TextStyle.Accent),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.RTL,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = ContainerDirectional(
                                                    alignment = AlignmentDirectional.BOTTOM_START,
                                                    child = SizedBox(
                                                        width = 12,
                                                        height = 8,
                                                        child = DecoratedBox(
                                                            fillTone = PixelTone.OFF,
                                                            borderTone = PixelTone.ACCENT,
                                                            child = Center(
                                                                child = Text("RTL", style = TextStyle.Accent),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.LTR,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Column(
                                                    crossAxisAlignment = CrossAxisAlignment.START,
                                                    children = listOf(
                                                        SizedBox(
                                                            width = 10,
                                                            height = 8,
                                                            child = DecoratedBox(
                                                                fillTone = PixelTone.OFF,
                                                                borderTone = PixelTone.ACCENT,
                                                                child = Center(
                                                                    child = Text("LTR", style = TextStyle.Accent),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.RTL,
                                            child = DecoratedBox(
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = Column(
                                                    crossAxisAlignment = CrossAxisAlignment.START,
                                                    children = listOf(
                                                        SizedBox(
                                                            width = 10,
                                                            height = 8,
                                                            child = DecoratedBox(
                                                                fillTone = PixelTone.OFF,
                                                                borderTone = PixelTone.ACCENT,
                                                                child = Center(
                                                                    child = Text("RTL", style = TextStyle.Accent),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(
                            height = 24,
                            child = Row(
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                children = listOf(
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.LTR,
                                            child = ContainerDirectional(
                                                marginDirectional = EdgeInsetsDirectional.only(start = 2),
                                                paddingDirectional = EdgeInsetsDirectional.only(start = 2, top = 2),
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = SizedBox(
                                                    width = 12,
                                                    height = 8,
                                                    child = DecoratedBox(
                                                        fillTone = PixelTone.OFF,
                                                        borderTone = PixelTone.ACCENT,
                                                        child = Center(
                                                            child = Text("LTR", style = TextStyle.Accent),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                    Expanded(
                                        child = Directionality(
                                            textDirection = TextDirection.RTL,
                                            child = ContainerDirectional(
                                                marginDirectional = EdgeInsetsDirectional.only(start = 2),
                                                paddingDirectional = EdgeInsetsDirectional.only(start = 2, top = 2),
                                                fillTone = PixelTone.OFF,
                                                borderTone = PixelTone.ON,
                                                child = SizedBox(
                                                    width = 12,
                                                    height = 8,
                                                    child = DecoratedBox(
                                                        fillTone = PixelTone.OFF,
                                                        borderTone = PixelTone.ACCENT,
                                                        child = Center(
                                                            child = Text("RTL", style = TextStyle.Accent),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
    }

    /**
     * 自定义 RenderObject 扩展点演示。
     *
     * 用 [SpinningSquareWidget] 展示 `pixelui.advanced` 公开 API 的端到端链路：
     * - 两个按钮步进 side 大小，验证 `markNeedsLayout` 触发增量重绘
     * - 两个按钮切换方框颜色（ON/ACCENT），验证 `markNeedsPaint` 触发增量重绘
     *
     * 以上链路均通过 Widget → Element → RenderObject 的标准 rebuild 路径完成，
     * 没有任何绕过 retained tree 的直接操作。
     */
    private fun customRenderObjectScene(
        textRasterizers: DemoTextRasterizers,
    ): DemoScene {
        val side = ValueNotifier(16)
        val tone = ValueNotifier(PixelTone.ON)

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            initialTextRasterizer = textRasterizers.default,
            content = {
                Column(
                    children = listOf(
                        // 标题
                        Padding(
                            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 4),
                            child = Text(
                                "自定义 RenderObject",
                                style = TextStyle.Default,
                            ),
                        ),
                        // side 控制行
                        Padding(
                            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 2),
                            child = Row(
                                children = listOf(
                                    Text("side: ", style = TextStyle.Default),
                                    ValueListenableBuilder(
                                        listenable = side,
                                        builder = { _, value ->
                                            Text("$value px", style = TextStyle.Accent)
                                        },
                                    ),
                                    SizedBox(width = 4),
                                    OutlinedButton(
                                        text = "-",
                                        onPressed = {
                                            if (side.value > 4) side.value -= 4
                                        },
                                    ),
                                    SizedBox(width = 2),
                                    OutlinedButton(
                                        text = "+",
                                        onPressed = {
                                            if (side.value < 48) side.value += 4
                                        },
                                    ),
                                ),
                            ),
                        ),
                        // tone 控制行
                        Padding(
                            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 2),
                            child = Row(
                                children = listOf(
                                    Text("tone: ", style = TextStyle.Default),
                                    OutlinedButton(
                                        text = "ON",
                                        onPressed = { tone.value = PixelTone.ON },
                                    ),
                                    SizedBox(width = 2),
                                    OutlinedButton(
                                        text = "ACC",
                                        onPressed = { tone.value = PixelTone.ACCENT },
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(height = 8),
                        // 方框预览
                        Center(
                            child = ValueListenableBuilder(
                                listenable = side,
                                builder = { _, sideValue ->
                                    ListenableBuilder(
                                        listenable = tone,
                                        builder = { _ ->
                                            SpinningSquareWidget(
                                                side = sideValue,
                                                tone = tone.value,
                                            )
                                        },
                                    )
                                },
                            ),
                        ),
                        SizedBox(height = 8),
                        // 说明文字
                        Padding(
                            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 2),
                            child = Text(
                                "按 +/− 改变边长触发 markNeedsLayout；",
                                style = TextStyle.Default,
                            ),
                        ),
                        Padding(
                            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 2),
                            child = Text(
                                "切换颜色触发 markNeedsPaint。",
                                style = TextStyle.Default,
                            ),
                        ),
                    ),
                )
            },
        )
    }
}
