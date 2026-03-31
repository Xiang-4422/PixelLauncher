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
import com.purride.pixelui.PixelBox
import com.purride.pixelui.PixelColumn
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelModifier
import com.purride.pixelui.PixelPager
import com.purride.pixelui.PixelRow
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelText
import com.purride.pixelui.clickable
import com.purride.pixelui.fillMaxSize
import com.purride.pixelui.fillMaxWidth
import com.purride.pixelui.height
import com.purride.pixelui.padding
import com.purride.pixelui.size
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
            DemoSceneKind.LAYOUT_AND_CLICK -> layoutAndClickScene(hostView)
        }
    }

    private fun textScene(): DemoScene {
        return DemoScene(
            initialProfile = defaultProfile(),
            initialPalette = PixelPalette.terminalGreen(),
            initialTextRasterizer = PixelBitmapFont(
                glyphWidth = 4,
                glyphHeight = 5,
                letterSpacing = 1,
                lineSpacing = 1,
            ),
            content = {
                PixelColumn(
                    modifier = PixelModifier.Empty.fillMaxSize().padding(4),
                    spacing = 4,
                    children = listOf(
                        sectionTitle("PIXEL UI DEMO"),
                        infoCard("TEXT", "COMPACT RASTERIZER"),
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
                        demoButton("NEXT THEME") {
                            currentThemeIndex = (currentThemeIndex + 1) % themes.size
                            applyHostAppearance()
                            hostView.requestRender()
                        },
                        demoButton("NEXT SHAPE") {
                            currentShapeIndex = (currentShapeIndex + 1) % shapes.size
                            applyHostAppearance()
                            hostView.requestRender()
                        },
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
                        demoButton("INCREASE") {
                            count += 1
                            hostView.requestRender()
                        },
                        demoButton("TOGGLE ACCENT") {
                            accentMode = !accentMode
                            hostView.requestRender()
                        },
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

    private fun sectionTitle(text: String) = PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(14),
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = PixelAlignment.CENTER,
            children = listOf(
                PixelText(text, tone = PixelTone.ACCENT),
            ),
        ),
    )

    private fun infoCard(
        label: String,
        value: String,
        accent: Boolean = false,
    ) = PixelSurface(
        modifier = PixelModifier.Empty.fillMaxWidth().height(18),
        fillTone = PixelTone.OFF,
        borderTone = if (accent) PixelTone.ACCENT else PixelTone.ON,
        child = PixelColumn(
            modifier = PixelModifier.Empty.fillMaxSize().padding(2),
            spacing = 2,
            children = listOf(
                PixelText(label, tone = PixelTone.ACCENT),
                PixelText(value, tone = if (accent) PixelTone.ACCENT else PixelTone.ON),
            ),
        ),
    )

    private fun demoButton(
        text: String,
        onClick: () -> Unit,
    ) = PixelSurface(
        modifier = PixelModifier.Empty
            .fillMaxWidth()
            .height(14)
            .clickable(onClick),
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ON,
        child = PixelBox(
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = PixelAlignment.CENTER,
            children = listOf(
                PixelText(text, tone = PixelTone.ON),
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
                PixelText(label, tone = if (tone == PixelTone.OFF) PixelTone.ON else PixelTone.ACCENT),
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
                PixelText(label, tone = PixelTone.ON),
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
                            PixelText(title, tone = tone),
                        ),
                    ),
                )
                add(
                    demoButton(primaryActionLabel, onPrimaryAction),
                )
                if (onSecondaryAction != null && secondaryActionLabel != null) {
                    add(
                        demoButton(secondaryActionLabel, onSecondaryAction),
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
}
