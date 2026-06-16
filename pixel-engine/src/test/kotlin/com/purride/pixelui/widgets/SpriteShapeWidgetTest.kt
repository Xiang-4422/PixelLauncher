package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelBlendMode
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.Path
import com.purride.pixelui.PixelGradient
import com.purride.pixelui.PixelGradientStop
import com.purride.pixelui.PixelPath
import com.purride.pixelui.PixelPathCommand
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.Polygon
import com.purride.pixelui.Sprite
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.widgets.animated.AnimatedSprite
import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteShapeWidgetTest {
    @Test
    fun spriteBlitsSelectedFrame() {
        val sheet = twoFrameSheet()
        val tester = PixelTester()
        tester.pumpWidget(Sprite(sheet, frameIndex = 1), 2, 2)

        assertEquals(PixelColor.fromRgb(200, 100, 0), tester.renderResult!!.buffer.getPixel(0, 0))
        tester.dispose()
    }

    @Test
    fun animatedSpriteAdvancesByFps() {
        val sheet = twoFrameSheet()
        val tester = PixelTester()
        tester.pumpWidget(AnimatedSprite(sheet, fps = 10, vsync = tester.vsync), 2, 2)

        tester.pumpFrame(0)
        tester.pumpFrame(100)

        assertEquals(PixelColor.fromRgb(200, 100, 0), tester.renderResult!!.buffer.getPixel(0, 0))
        tester.dispose()
    }

    @Test(expected = IllegalArgumentException::class)
    fun animatedSpriteRejectsInvalidFps() {
        AnimatedSprite(twoFrameSheet(), fps = 0, vsync = PixelTester().vsync)
    }

    @Test
    fun polygonFilledPaintsInterior() {
        val tester = PixelTester()
        tester.pumpWidget(
            Polygon(
                points = listOf(PixelPoint(0, 0), PixelPoint(4, 0), PixelPoint(4, 4), PixelPoint(0, 4)),
                color = PixelColor.White,
                filled = true,
            ),
            5,
            5,
        )

        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(2, 2))
        tester.dispose()
    }

    @Test
    fun polygonFilledKeepsConcaveNotchTransparentAndDrawsEdges() {
        val tester = PixelTester()
        tester.pumpWidget(
            Polygon(
                points = listOf(
                    PixelPoint(0, 0),
                    PixelPoint(6, 0),
                    PixelPoint(6, 6),
                    PixelPoint(4, 6),
                    PixelPoint(4, 2),
                    PixelPoint(2, 2),
                    PixelPoint(2, 6),
                    PixelPoint(0, 6),
                ),
                color = PixelColor.White,
                filled = true,
            ),
            7,
            7,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(1, 4))
        assertEquals(PixelColor.Transparent, buffer.getPixel(3, 4))
        assertEquals(PixelColor.White, buffer.getPixel(5, 4))
        assertEquals(PixelColor.White, buffer.getPixel(3, 0))
        assertEquals(PixelColor.White, buffer.getPixel(1, 6))
        assertEquals(PixelColor.White, buffer.getPixel(5, 6))
        tester.dispose()
    }

    @Test
    fun polygonFilledToleratesRepeatedVerticesAndHorizontalBoundary() {
        val tester = PixelTester()
        tester.pumpWidget(
            Polygon(
                points = listOf(
                    PixelPoint(0, 0),
                    PixelPoint(4, 0),
                    PixelPoint(4, 0),
                    PixelPoint(4, 4),
                    PixelPoint(0, 4),
                    PixelPoint(0, 0),
                ),
                color = PixelColor.White,
                filled = true,
            ),
            5,
            5,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(2, 0))
        assertEquals(PixelColor.White, buffer.getPixel(2, 2))
        assertEquals(PixelColor.White, buffer.getPixel(2, 4))
        tester.dispose()
    }

    @Test
    fun polygonFilledPaintsAlphaBoundaryOnce() {
        val tester = PixelTester()
        val halfRed = PixelColor.fromArgb(128, 255, 0, 0)
        tester.pumpWidget(
            Polygon(
                points = listOf(PixelPoint(0, 0), PixelPoint(4, 0), PixelPoint(4, 4), PixelPoint(0, 4)),
                color = halfRed,
                filled = true,
            ),
            5,
            5,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(128, buffer.getPixel(2, 0).alpha)
        assertEquals(128, buffer.getPixel(2, 2).alpha)
        assertEquals(128, buffer.getPixel(2, 4).alpha)
        tester.dispose()
    }

    @Test
    fun pathDrawsLineAndCloseSegments() {
        val tester = PixelTester()
        tester.pumpWidget(
            Path(
                path = PixelPath(
                    listOf(
                        PixelPathCommand.MoveTo(PixelPoint(0, 0)),
                        PixelPathCommand.LineTo(PixelPoint(3, 0)),
                        PixelPathCommand.LineTo(PixelPoint(3, 3)),
                        PixelPathCommand.Close,
                    ),
                ),
                color = PixelColor.White,
            ),
            4,
            4,
        )

        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(3, 3))
        tester.dispose()
    }

    @Test
    fun pathStrokeWidthPaintsThickerLine() {
        val tester = PixelTester()
        tester.pumpWidget(
            Path(
                path = PixelPath(
                    listOf(
                        PixelPathCommand.MoveTo(PixelPoint(1, 1)),
                        PixelPathCommand.LineTo(PixelPoint(5, 1)),
                    ),
                ),
                color = PixelColor.White,
                strokeWidth = 3,
            ),
            7,
            4,
        )

        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(3, 0))
        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(3, 1))
        assertEquals(PixelColor.White, tester.renderResult!!.buffer.getPixel(3, 2))
        tester.dispose()
    }

    @Test
    fun pathDrawsQuadraticCurveThroughFlattenedSegments() {
        val tester = PixelTester()
        tester.pumpWidget(
            Path(
                path = PixelPath(
                    listOf(
                        PixelPathCommand.MoveTo(PixelPoint(0, 4)),
                        PixelPathCommand.QuadraticTo(
                            control = PixelPoint(4, 0),
                            end = PixelPoint(8, 4),
                        ),
                    ),
                ),
                color = PixelColor.White,
            ),
            9,
            5,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(0, 4))
        assertEquals(PixelColor.White, buffer.getPixel(4, 2))
        assertEquals(PixelColor.White, buffer.getPixel(8, 4))
        tester.dispose()
    }

    @Test
    fun customPaintDrawsCubicCurveWithSharedPathFlattener() {
        val tester = PixelTester()
        tester.pumpWidget(
            CustomPaint(width = 9, height = 5) {
                drawPath(
                    path = PixelPath(
                        listOf(
                            PixelPathCommand.MoveTo(PixelPoint(0, 4)),
                            PixelPathCommand.CubicTo(
                                control1 = PixelPoint(0, 0),
                                control2 = PixelPoint(8, 0),
                                end = PixelPoint(8, 4),
                            ),
                        ),
                    ),
                    color = PixelColor.White,
                )
            },
            9,
            5,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(0, 4))
        assertEquals(PixelColor.White, buffer.getPixel(4, 1))
        assertEquals(PixelColor.White, buffer.getPixel(8, 4))
        tester.dispose()
    }

    @Test
    fun pathHelpersBuildRectAndCircleCommands() {
        val rect = PixelPath.rect(left = 1, top = 2, width = 4, height = 3)
        val circle = PixelPath.circle(centerX = 4, centerY = 4, radius = 2)

        assertEquals(PixelPathCommand.MoveTo(PixelPoint(1, 2)), rect.commands.first())
        assertEquals(PixelPathCommand.Close, rect.commands.last())
        assertEquals(PixelPathCommand.Close, circle.commands.last())
    }

    @Test
    fun customPaintBatchesCanvasCommands() {
        val tester = PixelTester()
        val orange = PixelColor.fromRgb(200, 100, 0)
        tester.pumpWidget(
            CustomPaint(width = 8, height = 8) {
                fillRect(0, 0, 8, 8, PixelColor.fromRgb(20, 20, 20))
                drawLine(0, 0, 7, 7, PixelColor.White)
                drawCircle(4, 4, 2, orange, filled = false)
                drawPolygon(
                    points = listOf(PixelPoint(1, 6), PixelPoint(3, 4), PixelPoint(5, 6)),
                    color = PixelColor.fromRgb(80, 180, 110),
                    filled = true,
                )
                drawPath(
                    path = PixelPath(
                        listOf(
                            PixelPathCommand.MoveTo(PixelPoint(0, 7)),
                            PixelPathCommand.LineTo(PixelPoint(7, 7)),
                        ),
                    ),
                    color = PixelColor.White,
                    strokeWidth = 2,
                )
            },
            8,
            8,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, buffer.getPixel(7, 7))
        assertEquals(orange, buffer.getPixel(6, 4))
        tester.dispose()
    }

    @Test
    fun customPaintPolygonUsesSameConcaveBoundaryRules() {
        val tester = PixelTester()
        tester.pumpWidget(
            CustomPaint(width = 7, height = 7) {
                drawPolygon(
                    points = listOf(
                        PixelPoint(0, 0),
                        PixelPoint(6, 0),
                        PixelPoint(6, 6),
                        PixelPoint(4, 6),
                        PixelPoint(4, 2),
                        PixelPoint(2, 2),
                        PixelPoint(2, 6),
                        PixelPoint(0, 6),
                    ),
                    color = PixelColor.White,
                    filled = true,
                )
            },
            7,
            7,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(1, 4))
        assertEquals(PixelColor.Transparent, buffer.getPixel(3, 4))
        assertEquals(PixelColor.White, buffer.getPixel(5, 4))
        tester.dispose()
    }

    @Test
    fun customPaintPolygonBlendsBoundaryOnce() {
        val tester = PixelTester()
        val blue = PixelColor.fromRgb(0, 0, 255)
        val halfRed = PixelColor.fromArgb(128, 255, 0, 0)
        val expected = PixelBuffer(width = 1, height = 1).also {
            it.setPixel(0, 0, blue)
            it.setPixel(0, 0, halfRed)
        }.getPixel(0, 0)

        tester.pumpWidget(
            CustomPaint(width = 5, height = 5) {
                fillRect(0, 0, 5, 5, blue)
                drawPolygon(
                    points = listOf(PixelPoint(0, 0), PixelPoint(4, 0), PixelPoint(4, 4), PixelPoint(0, 4)),
                    color = halfRed,
                    filled = true,
                )
            },
            5,
            5,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(expected, buffer.getPixel(2, 0))
        assertEquals(expected, buffer.getPixel(2, 2))
        assertEquals(expected, buffer.getPixel(2, 4))
        tester.dispose()
    }

    @Test
    fun customPaintFillsLinearGradientRect() {
        val tester = PixelTester()
        tester.pumpWidget(
            CustomPaint(width = 3, height = 1) {
                fillGradientRect(
                    left = 0,
                    top = 0,
                    width = 3,
                    height = 1,
                    gradient = PixelGradient.Linear(
                        start = PixelPoint(0, 0),
                        end = PixelPoint(2, 0),
                        stops = listOf(
                            PixelGradientStop(0f, PixelColor.Black),
                            PixelGradientStop(1f, PixelColor.White),
                        ),
                    ),
                )
            },
            3,
            1,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.Black, buffer.getPixel(0, 0))
        assertEquals(PixelColor.fromRgb(127, 127, 127), buffer.getPixel(1, 0))
        assertEquals(PixelColor.White, buffer.getPixel(2, 0))
        tester.dispose()
    }

    @Test
    fun customPaintFillsRadialGradientRect() {
        val tester = PixelTester()
        val edge = PixelColor.fromRgb(200, 100, 0)
        tester.pumpWidget(
            CustomPaint(width = 3, height = 1) {
                fillGradientRect(
                    left = 0,
                    top = 0,
                    width = 3,
                    height = 1,
                    gradient = PixelGradient.Radial(
                        center = PixelPoint(1, 0),
                        radius = 1,
                        stops = listOf(
                            PixelGradientStop(0f, PixelColor.White),
                            PixelGradientStop(1f, edge),
                        ),
                    ),
                )
            },
            3,
            1,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(edge, buffer.getPixel(0, 0))
        assertEquals(PixelColor.White, buffer.getPixel(1, 0))
        assertEquals(edge, buffer.getPixel(2, 0))
        tester.dispose()
    }

    @Test
    fun customPaintAppliesBlendMode() {
        val tester = PixelTester()
        tester.pumpWidget(
            CustomPaint(width = 3, height = 1) {
                fillRect(0, 0, 3, 1, PixelColor.White)
                setPixel(0, 0, PixelColor.Transparent)
                setPixel(1, 0, PixelColor.Transparent, blendMode = PixelBlendMode.Src)
                fillRect(2, 0, 1, 1, PixelColor.White, blendMode = PixelBlendMode.Clear)
            },
            3,
            1,
        )

        val buffer = tester.renderResult!!.buffer
        assertEquals(PixelColor.White, buffer.getPixel(0, 0))
        assertEquals(PixelColor.Transparent, buffer.getPixel(1, 0))
        assertEquals(PixelColor.Transparent, buffer.getPixel(2, 0))
        tester.dispose()
    }

    private fun twoFrameSheet(): PixelSpriteSheet {
        val pixels = intArrayOf(
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.fromRgb(200, 100, 0).argb,
            PixelColor.fromRgb(200, 100, 0).argb,
        )
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 4, height = 1, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(0, 0, 2, 1),
                PixelBitmapRegion(2, 0, 2, 1),
            ),
        )
    }
}
