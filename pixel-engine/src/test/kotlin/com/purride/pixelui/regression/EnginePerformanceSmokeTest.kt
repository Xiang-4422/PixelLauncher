package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.Alignment
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.PixelPoint
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.Polygon
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Sprite
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixelui.widgets.animated.AnimatedSprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.system.measureNanoTime

class EnginePerformanceSmokeTest {
    @Test
    fun renderPerformanceSmokeWritesReadOnlyReport() {
        val samples = scenes.map { scene -> measureScene(scene) }
        samples.forEach { sample ->
            assertEquals(SAMPLE_FRAMES, sample.frames)
            assertEquals(sample.width, sample.lastWidth)
            assertEquals(sample.height, sample.lastHeight)
            assertTrue("${sample.name} total nanos should be positive", sample.totalNanos > 0L)
            assertTrue("${sample.name} avg nanos should be positive", sample.averageNanos > 0L)
        }

        val report = writeReport(samples)
        assertTrue(report.exists())
        assertTrue(report.readText().contains("pixel-engine render perf smoke"))
    }

    private fun measureScene(scene: PerfScene): PerfSample {
        val runtime = PixelUiRuntime()
        var lastWidth = 0
        var lastHeight = 0
        try {
            repeat(WARMUP_FRAMES) { frame ->
                scene.beforeFrame(frame)
                runtime.render(scene.build(), scene.width, scene.height)
            }
            val totalNanos = measureNanoTime {
                repeat(SAMPLE_FRAMES) { frame ->
                    scene.beforeFrame(WARMUP_FRAMES + frame)
                    val result = runtime.render(scene.build(), scene.width, scene.height)
                    lastWidth = result.buffer.width
                    lastHeight = result.buffer.height
                }
            }
            return PerfSample(
                name = scene.name,
                frames = SAMPLE_FRAMES,
                width = scene.width,
                height = scene.height,
                lastWidth = lastWidth,
                lastHeight = lastHeight,
                totalNanos = totalNanos,
            )
        } finally {
            runtime.dispose()
        }
    }

    private fun writeReport(samples: List<PerfSample>): File {
        val report = File("build/reports/perf/pixel-engine-render-smoke.txt")
        report.parentFile?.mkdirs()
        report.writeText(
            buildString {
                appendLine("pixel-engine render perf smoke")
                appendLine("warmupFrames=$WARMUP_FRAMES")
                appendLine("sampleFrames=$SAMPLE_FRAMES")
                samples.forEach { sample ->
                    appendLine(
                        String.format(
                            Locale.US,
                            "%s frames=%d size=%dx%d total=%.3fms avg=%.3fms",
                            sample.name,
                            sample.frames,
                            sample.width,
                            sample.height,
                            sample.totalNanos / 1_000_000.0,
                            sample.averageNanos / 1_000_000.0,
                        ),
                    )
                }
            },
        )
        return report
    }

    private data class PerfScene(
        val name: String,
        val width: Int,
        val height: Int,
        val beforeFrame: (Int) -> Unit = { },
        val build: () -> Widget,
    )

    private data class PerfSample(
        val name: String,
        val frames: Int,
        val width: Int,
        val height: Int,
        val lastWidth: Int,
        val lastHeight: Int,
        val totalNanos: Long,
    ) {
        val averageNanos: Long
            get() = totalNanos / frames.coerceAtLeast(1)
    }

    private val scenes: List<PerfScene> = listOf(
        PerfScene(name = "list_scroll", width = 96, height = 64) {
            val controller = PixelListController()
            val state = controller.create(initialScrollOffsetPx = 24f)
            Scrollbar(
                state = state,
                width = 2,
                child = GridViewBuilder(
                    itemCount = 120,
                    itemBuilder = { index ->
                        Container(
                            child = Text("${index % 10}"),
                            borderColor = PixelColor.White,
                            alignment = Alignment.CENTER,
                        )
                    },
                    cellWidth = 12,
                    cellHeight = 7,
                    spacing = 1,
                    runSpacing = 1,
                    state = state,
                    controller = controller,
                ),
            )
        },
        PerfScene(name = "text_input", width = 96, height = 32) {
            val controller = TextEditingController()
            val state = PixelTextFieldState(
                initialText = "ALPHA\nBRAVO CHARLIE\nDELTA",
                selectionStart = 6,
                selectionEnd = 19,
            )
            controller.updateComposition(state, compositionStart = 20, compositionEnd = 25)
            controller.focus(state)
            TextField(
                state = state,
                controller = controller,
                minLines = 3,
                maxLines = 3,
            )
        },
        run {
            val scheduler = ManualFrameScheduler()
            val vsync = PixelTickerProvider(scheduler)
            PerfScene(
                name = "animation",
                width = 24,
                height = 8,
                beforeFrame = { frame -> scheduler.advanceFrame((frame.toLong() + 1L) * 83_333_333L) },
            ) {
                AnimatedSprite(sheet = sampleSpriteSheet(), fps = 12, vsync = vsync)
            }
        },
        PerfScene(name = "graphics_primitives", width = 96, height = 24) {
            Row(
                children = listOf(
                    Sprite(sheet = sampleSpriteSheet(), frameIndex = 1),
                    Polygon(
                        points = listOf(PixelPoint(0, 12), PixelPoint(12, 0), PixelPoint(24, 12)),
                        color = PixelColor.White,
                    ),
                    ProgressBar(progress = 0.67f, width = 36),
                ),
                spacing = 4,
            )
        },
        PerfScene(name = "page_transition", width = 96, height = 40) {
            PixelNavigator(
                initialRoute = PixelRoute(
                    name = "home",
                    builder = {
                        AppScaffold(
                            title = Text("PERF"),
                            body = Column(
                                children = listOf(
                                    Text("HOME"),
                                    SizedBox(height = 2),
                                    OutlinedButton("OPEN", onPressed = {}),
                                ),
                                spacing = 1,
                            ),
                            bottomBar = Text("READY"),
                        )
                    },
                ),
                vsync = PixelTickerProvider(ManualFrameScheduler()),
            )
        },
        run {
            val overlay = PixelOverlayController()
            overlay.showSnackbar(
                message = "QUEUED",
                action = OutlinedButton("UNDO", onPressed = {}),
            )
            PerfScene(name = "overlay", width = 108, height = 46) {
                PixelOverlayHost(
                    controller = overlay,
                    child = Text("HOME"),
                )
            }
        },
    )

    private fun sampleSpriteSheet(): PixelSpriteSheet {
        val red = PixelColor.fromRgb(220, 60, 60).argb
        val yellow = PixelColor.fromRgb(230, 200, 60).argb
        val blue = PixelColor.fromRgb(60, 100, 220).argb
        val pixels = IntArray(8 * 4)
        for (y in 0 until 4) {
            for (x in 0 until 4) pixels[y * 8 + x] = if ((x + y) % 2 == 0) red else yellow
            for (x in 4 until 8) pixels[y * 8 + x] = if (x == 4 || y == 0 || x == 7 || y == 3) {
                blue
            } else {
                PixelColor.White.argb
            }
        }
        return PixelSpriteSheet(
            bitmap = PixelBitmap(width = 8, height = 4, pixels = pixels),
            frames = listOf(
                PixelBitmapRegion(left = 0, top = 0, width = 4, height = 4),
                PixelBitmapRegion(left = 4, top = 0, width = 4, height = 4),
            ),
        )
    }

    private companion object {
        const val WARMUP_FRAMES = 3
        const val SAMPLE_FRAMES = 20
    }
}
