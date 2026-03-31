package com.purride.pixeldemo.app

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelShape
import com.purride.pixelcore.PixelTheme
import com.purride.pixelcore.PixelTone
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.PixelStyledTextRasterizer
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import com.purride.pixelui.PixelAlignment
import com.purride.pixelui.PixelButton
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelList
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelRow
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelText
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.fillMaxWidth
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.size
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController

/**
 * Demo scene 定义集合。
 *
 * 每个 scene 都通过 `pixel-ui` 的组件树搭建，避免再退回手写 renderer。
 */
object DemoScenes {

    data class DemoScene(
        val initialProfile: ScreenProfile,
        val initialPalette: PixelPalette,
        val initialTextRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
        val content: () -> com.purride.pixelui.PixelNode,
    )

    fun create(
        sceneKind: DemoSceneKind,
        hostView: PixelHostView,
    ): DemoScene {
        return when (sceneKind) {
            DemoSceneKind.TEXT -> textScene()
            DemoSceneKind.PALETTE -> paletteScene(hostView)
            DemoSceneKind.HORIZONTAL_PAGER -> horizontalPagerScene(hostView)
            DemoSceneKind.VERTICAL_PAGER -> verticalPagerScene(hostView)
            DemoSceneKind.LIST -> listScene(hostView)
            DemoSceneKind.LAYOUT_AND_CLICK -> layoutAndClickScene(hostView)
        }
    }

    private fun textScene(): DemoScene {
        val compactRasterizer = PixelBitmapFont(
            glyphWidth = 4,
            glyphHeight = 5,
            letterSpacing = 1,
            lineSpacing = 1,
        )
        val wideRasterizer = PixelStyledTextRasterizer(
            engine = PixelFontEngine(
                glyphProvider = builtInDemoGlyphProvider(),
            ),
            style = GlyphStyle(
                cellHeight = 7,
                narrowAdvanceWidth = 6,
                wideAdvanceWidth = 6,
                oversampleFactor = 1,
                narrowMinimumSampleRatio = 1f,
                wideMinimumSampleRatio = 1f,
                narrowTextSizeRatio = 1f,
                wideTextSizeRatio = 1f,
                narrowFontWeight = PixelFontWeight.NORMAL,
                wideFontWeight = PixelFontWeight.NORMAL,
                narrowFontFamily = PixelFontFamily.MONOSPACE,
                wideFontFamily = PixelFontFamily.MONOSPACE,
                baseLetterSpacing = 2,
            ),
            lineSpacing = 1,
        )
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.terminalGreen(),
            initialTextRasterizer = compactRasterizer,
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("PIXEL UI DEMO", textRasterizer = wideRasterizer),
                        infoCard("TEXT", "COMPACT RASTERIZER"),
                        infoCard("STYLE", "NODE OVERRIDE", valueRasterizer = wideRasterizer),
                        infoCard("LAYOUT", "COLUMN ROW BOX SURFACE"),
                        infoCard("GOAL", "FRAMEWORK FIRST APP LATER"),
                    ),
                )
            },
        )
    }

    private fun paletteScene(hostView: PixelHostView): DemoScene {
        val themes = PixelTheme.entries
        val shapes = PixelShape.entries
        var currentThemeIndex = 0
        var currentShapeIndex = 0

        fun applyHostAppearance() {
            hostView.setPalette(PixelPalette.fromTheme(themes[currentThemeIndex]))
            hostView.screenProfile = defaultProfile(pixelShape = shapes[currentShapeIndex])
        }

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(themes[currentThemeIndex]),
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
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

    private fun horizontalPagerScene(hostView: PixelHostView): DemoScene {
        val controller = PixelPagerController()
        val state = controller.create(
            pageCount = 3,
            currentPage = 0,
            axis = PixelAxis.HORIZONTAL,
        )
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),
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

    private fun verticalPagerScene(hostView: PixelHostView): DemoScene {
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

    private fun layoutAndClickScene(hostView: PixelHostView): DemoScene {
        var count = 0
        var accentMode = false

        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.fromTheme(PixelTheme.NIGHT_MONO),
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
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
                    ),
                )
            },
        )
    }

    private fun listScene(hostView: PixelHostView): DemoScene {
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
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("VERTICAL LIST"),
                        infoCard("OFFSET", state.scrollOffsetPx.toInt().toString(), accent = state.scrollOffsetPx > 0f),
                        infoCard("TAPS", tapCount.toString()),
                        PixelList(
                            state = state,
                            controller = controller,
                            modifier = PixelModifier.Empty.fillMaxWidth().height(58),
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

    private fun sectionTitle(
        text: String,
        textRasterizer: PixelTextRasterizer? = null,
    ) = PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
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

    private fun infoCard(
        label: String,
        value: String,
        accent: Boolean = false,
        valueRasterizer: PixelTextRasterizer? = null,
    ) = PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(18),
        fillTone = PixelTone.OFF,
        borderTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
        child = PixelColumn(
            modifier = PixelModifier.Empty.fillMaxSize().padding(2),
            spacing = 2,
            children = listOf(
                PixelText(label, style = PixelTextStyle.Accent),
                PixelText(
                    value,
                    style = PixelTextStyle(
                        tone = if (accent) PixelTone.ACCENT else PixelTone.ON,
                        textRasterizer = valueRasterizer,
                    ),
                ),
            ),
        ),
    )

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
     * Demo 内置字形源。
     *
     * 这里直接复用 `PixelBitmapFont` 的默认字模宽高，让文本 demo 可以验证
     * “同页混用不同文本栅格器”，而不需要额外引入资产文件。
     */
    private fun builtInDemoGlyphProvider(): com.purride.pixelcore.GlyphProvider {
        return object : com.purride.pixelcore.GlyphProvider {
            private val backingFont = PixelBitmapFont.Default
            private val tempBuffer = PixelBuffer(width = 5, height = 7)

            override fun rasterizeGlyph(
                character: Char,
                style: GlyphStyle,
            ): com.purride.pixelcore.GlyphBitmap {
                tempBuffer.clear()
                backingFont.drawText(
                    buffer = tempBuffer,
                    text = character.toString(),
                    x = 0,
                    y = 0,
                    value = PixelTone.ON.value,
                )
                val pixels = tempBuffer.pixels.copyOf()
                return com.purride.pixelcore.GlyphBitmap(
                    width = tempBuffer.width,
                    height = tempBuffer.height,
                    pixels = pixels,
                    metrics = com.purride.pixelcore.GlyphMetrics(
                        advanceWidth = style.narrowAdvanceWidth,
                        baselineOffset = tempBuffer.height - 1,
                        isWideGlyph = false,
                        inkLeft = firstInkColumn(pixels, tempBuffer.width, tempBuffer.height),
                        inkRight = lastInkColumn(pixels, tempBuffer.width, tempBuffer.height),
                    ),
                )
            }
        }
    }

    private fun firstInkColumn(pixels: ByteArray, width: Int, height: Int): Int {
        var left = width
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[(y * width) + x] != PixelTone.OFF.value && x < left) {
                    left = x
                }
            }
        }
        return if (left == width) width else left
    }

    private fun lastInkColumn(pixels: ByteArray, width: Int, height: Int): Int {
        var right = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[(y * width) + x] != PixelTone.OFF.value && x > right) {
                    right = x
                }
            }
        }
        return right
    }
}
