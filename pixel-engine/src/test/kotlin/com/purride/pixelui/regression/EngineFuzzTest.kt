package com.purride.pixelui.regression

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBitmapRegion
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.Alignment
import com.purride.pixelui.AspectRatio
import com.purride.pixelui.Builder
import com.purride.pixelui.Center
import com.purride.pixelui.ClipRect
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.ConstrainedBox
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FittedBox
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.GridViewBuilder
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.Opacity
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelBoxConstraints
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextDirection
import com.purride.pixelui.TextField
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.internal.HostRootWidget
import com.purride.pixelui.internal.PixelUiRuntime
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListRestorationPolicy
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class EngineFuzzTest {
    @Test
    fun randomBoundsAndHitTargetsDoNotCrash() {
        val seed = 20260526L
        val random = Random(seed)
        repeat(80) {
            val tester = PixelTester()
            val width = 8 + random.nextInt(72)
            val height = 8 + random.nextInt(48)
            try {
                runFuzzCase(seed, it, tester) {
                    tester.pumpWidget(randomWidget(random, depth = 0, onTap = { }), width, height)
                    assertTargetGeometryIsFinite(tester)
                }
                assertEquals(width, tester.renderResult!!.buffer.width)
                assertEquals(height, tester.renderResult!!.buffer.height)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun randomEffectConstraintAndGridTargetsDoNotCrash() {
        val seed = 20260608L
        val random = Random(seed)
        repeat(60) { iteration ->
            val tester = PixelTester()
            val width = 10 + random.nextInt(54)
            val height = 10 + random.nextInt(42)
            try {
                runFuzzCase(seed, iteration, tester) {
                    tester.pumpWidget(randomViewportWidget(random), width, height)
                    assertTargetGeometryIsFinite(tester)
                    if (tester.renderResult!!.clickTargets.isNotEmpty() && random.nextBoolean()) {
                        tester.tap(find.byText("TAP"))
                    }
                    if (tester.renderResult!!.listTargets.isNotEmpty() && random.nextBoolean()) {
                        tester.drag(find.byKey("grid"), dx = random.nextInt(9) - 4, dy = -12 - random.nextInt(24))
                    }
                }
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun hitTargetFuzzClickDoesNotCrash() {
        repeat(20) { index ->
            var taps = 0
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    Center(
                        child = OutlinedButton(text = "TAP", onPressed = { taps += 1 }),
                    ),
                    logicalWidth = 24 + index,
                    logicalHeight = 12 + index,
                )
                tester.tap(com.purride.pixelui.testing.find.byText("TAP"))
                assertEquals(1, taps)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun textSamplesRenderAcrossNarrowWidths() {
        val samples = listOf(
            "",
            "ASCII",
            "中文像素",
            "emoji🙂🙂",
            "LONGWORDWITHOUTBREAKSLONGWORDWITHOUTBREAKS",
            "LINE1\nLINE2\nLINE3",
        )
        samples.forEachIndexed { index, sample ->
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    Text(data = sample, softWrap = true, maxLines = 4),
                    logicalWidth = 1 + index * 3,
                    logicalHeight = 32,
                )
                assertEquals(32, tester.renderResult!!.buffer.height)
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun textFieldSelectionAndCompositionFuzzClampToValidRanges() {
        val seed = 20260609L
        val random = Random(seed)
        val samples = listOf(
            "",
            "alpha beta_gamma",
            "中文像素\n第二行",
            "emoji🙂mix\nline",
            "LONGWORDWITHOUTBREAKS",
            "punctuation !?,.;",
        )
        repeat(72) { iteration ->
            val controller = PixelTextFieldController()
            val sample = samples[iteration % samples.size]
            val state = controller.create(initialText = sample)
            val tester = PixelTester()
            try {
                runFuzzCase(seed, iteration, tester) {
                    tester.pumpWidget(
                        TextField(
                            state = state,
                            controller = controller,
                            minLines = 1,
                            maxLines = 3,
                            key = "field",
                        ),
                        logicalWidth = 2 + random.nextInt(36),
                        logicalHeight = 6 + random.nextInt(24),
                    )
                    tester.tap(find.byKey("field"))
                    controller.setSelection(
                        state = state,
                        selectionStart = random.nextInt(sample.length + 7) - 3,
                        selectionEnd = random.nextInt(sample.length + 7) - 3,
                    )
                    controller.updateComposition(
                        state = state,
                        compositionStart = random.nextInt(sample.length + 9) - 4,
                        compositionEnd = random.nextInt(sample.length + 9) - 4,
                    )
                    tester.pumpFrame(16)

                    assertTrue(state.selectionStart in 0..state.text.length)
                    assertTrue(state.selectionEnd in state.selectionStart..state.text.length)
                    if (state.compositionStart >= 0) {
                        assertTrue(state.compositionStart in 0..state.text.length)
                        assertTrue(state.compositionEnd in state.compositionStart..state.text.length)
                    }
                    assertTargetGeometryIsFinite(tester)
                }
            } finally {
                tester.dispose()
            }
        }
    }

    @Test
    fun resourceRegionsRejectInvalidInputs() {
        val bitmap = PixelBitmap(width = 4, height = 4, pixels = IntArray(16) { PixelColor.White.argb })

        assertThrowsIllegalArgument { PixelBitmapRegion(left = -1, top = 0, width = 1, height = 1) }
        assertThrowsIllegalArgument { PixelBitmapRegion(left = 0, top = 0, width = 0, height = 1) }
        assertThrowsIllegalArgument { PixelSpriteSheet(bitmap = bitmap, frames = emptyList()) }
        assertThrowsIllegalArgument {
            PixelSpriteSheet(
                bitmap = bitmap,
                frames = listOf(PixelBitmapRegion(left = 3, top = 0, width = 2, height = 1)),
            )
        }
    }

    @Test
    fun transparentSourcePixelsKeepDestinationColor() {
        val destination = PixelBuffer(width = 2, height = 1)
        val source = PixelBuffer(width = 2, height = 1)
        destination.fillRect(0, 0, 2, 1, PixelColor.White)
        source.setPixel(0, 0, PixelColor.Transparent)
        source.setPixel(1, 0, PixelColor.fromArgb(128, 255, 0, 0))

        destination.blit(source, 0, 0)

        assertEquals(PixelColor.White, destination.getPixel(0, 0))
        assertTrue(destination.getPixel(1, 0).argb != PixelColor.White.argb)
    }

    @Test
    fun randomSpriteRegionsEitherBuildOrFailWithIllegalArgument() {
        val seed = 20260610L
        val random = Random(seed)
        val bitmap = PixelBitmap(
            width = 8,
            height = 8,
            pixels = IntArray(64) { index ->
                PixelColor.fromArgb(
                    a = if (index % 5 == 0) 0 else 255,
                    r = (index * 31) and 0xFF,
                    g = (index * 17) and 0xFF,
                    b = (index * 7) and 0xFF,
                ).argb
            },
        )

        repeat(80) { iteration ->
            try {
                val frames = List(1 + random.nextInt(4)) {
                    PixelBitmapRegion(
                        left = random.nextInt(11) - 2,
                        top = random.nextInt(11) - 2,
                        width = random.nextInt(6),
                        height = random.nextInt(6),
                    )
                }
                PixelSpriteSheet(bitmap = bitmap, frames = frames)
                frames.forEach { frame ->
                    assertTrue("seed=$seed iteration=$iteration frame=$frame", frame.left >= 0)
                    assertTrue("seed=$seed iteration=$iteration frame=$frame", frame.top >= 0)
                    assertTrue("seed=$seed iteration=$iteration frame=$frame", frame.left + frame.width <= bitmap.width)
                    assertTrue("seed=$seed iteration=$iteration frame=$frame", frame.top + frame.height <= bitmap.height)
                }
            } catch (_: IllegalArgumentException) {
                // Expected for generated negative, empty, or out-of-bounds regions.
            }
        }
    }

    @Test
    fun scrollAnchorRestorationFuzzPreservesVisibleItemAcrossGeometryChanges() {
        val seed = 20260619L
        val random = Random(seed)
        repeat(100) { iteration ->
            runFuzzCase(seed, iteration, null) {
                val itemCount = 2 + random.nextInt(40)
                val oldHeights = IntArray(itemCount) { 2 + random.nextInt(12) }
                val newHeights = IntArray(itemCount) { 2 + random.nextInt(16) }
                val oldSpacing = random.nextInt(4)
                val newSpacing = random.nextInt(4)
                val oldTopOffsets = topOffsets(oldHeights, oldSpacing)
                val newTopOffsets = topOffsets(newHeights, newSpacing)
                val anchorIndex = random.nextInt(itemCount)
                val anchorOffset = random.nextInt(oldHeights[anchorIndex]).toFloat()
                val oldContentHeight = contentHeight(oldHeights, oldSpacing)
                val newContentHeight = contentHeight(newHeights, newSpacing)
                val oldViewportHeight = 5 + random.nextInt(32)
                val newViewportHeight = 5 + random.nextInt(32)
                val controller = PixelListController()

                val source = controller.create()
                controller.sync(source, oldViewportHeight, oldContentHeight)
                source.itemTopOffsetsPx = oldTopOffsets
                source.itemHeightsPx = oldHeights
                controller.scrollTo(
                    state = source,
                    targetOffsetPx = oldTopOffsets[anchorIndex] + anchorOffset,
                    viewportHeightPx = oldViewportHeight,
                    contentHeightPx = oldContentHeight,
                )
                source.itemTopOffsetsPx = oldTopOffsets
                source.itemHeightsPx = oldHeights
                val saved = controller.saveState(source)

                val restored = controller.create()
                controller.sync(restored, newViewportHeight, newContentHeight)
                restored.itemTopOffsetsPx = newTopOffsets
                restored.itemHeightsPx = newHeights
                controller.restoreState(
                    state = restored,
                    savedState = saved,
                    viewportHeightPx = newViewportHeight,
                    contentHeightPx = newContentHeight,
                    policy = PixelListRestorationPolicy.AnchorItem,
                )

                val savedAnchor = saved.anchor!!
                val expectedOffset = (newTopOffsets[savedAnchor.itemIndex] + savedAnchor.itemOffsetPx)
                    .coerceIn(0f, (newContentHeight - newViewportHeight).coerceAtLeast(0).toFloat())
                assertEquals(expectedOffset, restored.scrollOffsetPx, 0.001f)
            }
        }
    }

    @Test
    fun hostRootInsetsFuzzKeepsPaddingSeparatedFromImeInsets() {
        val seed = 20260620L
        val random = Random(seed)
        repeat(80) { iteration ->
            runFuzzCase(seed, iteration, null) {
                val windowInsets = randomInsets(random)
                val viewInsets = randomInsets(random)
                var capturedPadding: PixelWindowInsets? = null
                var capturedViewPadding: PixelWindowInsets? = null
                var capturedViewInsets: PixelWindowInsets? = null
                val runtime = PixelUiRuntime()
                try {
                    runtime.render(
                        root = HostRootWidget(
                            screenProfile = ScreenProfile(logicalWidth = 12, logicalHeight = 8, dotSizePx = 4),
                            textRasterizer = PixelBitmapFont.Default,
                            windowInsets = windowInsets,
                            viewInsets = viewInsets,
                            child = Builder {
                                val media = MediaQuery.of(it)
                                capturedPadding = media.padding
                                capturedViewPadding = media.viewPadding
                                capturedViewInsets = media.viewInsets
                                Text("INSETS")
                            },
                        ),
                        logicalWidth = 12,
                        logicalHeight = 8,
                    )
                } finally {
                    runtime.dispose()
                }

                assertEquals(windowInsets, capturedViewPadding)
                assertEquals(viewInsets, capturedViewInsets)
                assertEquals(
                    PixelWindowInsets(
                        left = (windowInsets.left - viewInsets.left).coerceAtLeast(0),
                        top = (windowInsets.top - viewInsets.top).coerceAtLeast(0),
                        right = (windowInsets.right - viewInsets.right).coerceAtLeast(0),
                        bottom = (windowInsets.bottom - viewInsets.bottom).coerceAtLeast(0),
                    ),
                    capturedPadding,
                )
            }
        }
    }

    private fun randomWidget(random: Random, depth: Int, onTap: () -> Unit): Widget {
        if (depth >= 3) {
            return when (random.nextInt(3)) {
                0 -> Text("TXT")
                1 -> SizedBox(width = random.nextInt(12), height = random.nextInt(12))
                else -> GestureDetector(
                    child = Container(child = Text("TAP"), padding = EdgeInsets.all(1), borderColor = PixelColor.White),
                    onTap = onTap,
                )
            }
        }
        return when (random.nextInt(12)) {
            0 -> Row(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
                spacing = random.nextInt(3),
            )
            1 -> Column(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
                spacing = random.nextInt(3),
            )
            2 -> Stack(
                children = List(1 + random.nextInt(3)) { randomWidget(random, depth + 1, onTap) },
            )
            3 -> Container(
                child = randomWidget(random, depth + 1, onTap),
                width = random.nextInt(28),
                height = random.nextInt(20),
                padding = EdgeInsets.all(random.nextInt(3)),
                borderColor = if (random.nextBoolean()) PixelColor.White else null,
                alignment = Alignment.CENTER,
            )
            4 -> Center(child = randomWidget(random, depth + 1, onTap))
            5 -> Opacity(opacity = random.nextFloat(), child = randomWidget(random, depth + 1, onTap))
            6 -> ClipRect(child = randomWidget(random, depth + 1, onTap))
            7 -> Transform.translate(
                offset = IntOffset(x = random.nextInt(9) - 4, y = random.nextInt(9) - 4),
                child = randomWidget(random, depth + 1, onTap),
            )
            8 -> Wrap(
                children = List(1 + random.nextInt(4)) { randomWidget(random, depth + 1, onTap) },
                spacing = random.nextInt(3),
                runSpacing = random.nextInt(3),
            )
            9 -> ConstrainedBox(
                constraints = randomConstraints(random),
                child = randomWidget(random, depth + 1, onTap),
            )
            10 -> AspectRatio(
                aspectRatio = 0.5f + random.nextFloat() * 3f,
                child = randomWidget(random, depth + 1, onTap),
            )
            else -> FittedBox(child = randomWidget(random, depth + 1, onTap))
        }
    }

    private fun randomViewportWidget(random: Random): Widget {
        val controller = PixelListController()
        val state = controller.create(initialScrollOffsetPx = random.nextInt(24).toFloat())
        val grid = GridViewBuilder(
            itemCount = 12 + random.nextInt(36),
            itemBuilder = { index ->
                Container(
                    child = Text(if (index % 5 == 0) "TAP" else index.toString()),
                    fillColor = PixelColor.fromRgb((index * 31) and 0xFF, 64, 180),
                    borderColor = if (index % 3 == 0) PixelColor.White else null,
                    alignment = Alignment.CENTER,
                )
            },
            cellWidth = 3 + random.nextInt(8),
            cellHeight = 2 + random.nextInt(7),
            state = state,
            controller = controller,
            spacing = random.nextInt(3),
            runSpacing = random.nextInt(3),
            cacheExtent = random.nextInt(4),
            key = "grid",
        )
        return when (random.nextInt(4)) {
            0 -> ClipRect(child = grid)
            1 -> Opacity(opacity = random.nextFloat(), child = grid)
            2 -> Transform.translate(
                offset = IntOffset(x = random.nextInt(7) - 3, y = random.nextInt(7) - 3),
                child = grid,
            )
            else -> ConstrainedBox(constraints = randomConstraints(random), child = grid)
        }
    }

    private fun randomConstraints(random: Random): PixelBoxConstraints {
        val minWidth = random.nextInt(8)
        val minHeight = random.nextInt(8)
        return PixelBoxConstraints(
            minWidth = minWidth,
            maxWidth = minWidth + random.nextInt(24),
            minHeight = minHeight,
            maxHeight = minHeight + random.nextInt(18),
        )
    }

    private fun assertTargetGeometryIsFinite(tester: PixelTester) {
        val result = tester.renderResult ?: return
        val allBounds = buildList {
            addAll(result.clickTargets.map { it.bounds })
            addAll(result.pagerTargets.map { it.bounds })
            addAll(result.listTargets.map { it.bounds })
            addAll(result.scrollbarTargets.map { it.bounds })
            addAll(result.textInputTargets.map { it.bounds })
            addAll(result.sliderTargets.map { it.bounds })
        }
        allBounds.forEach { bounds ->
            assertTrue("target width must be >= 0: $bounds", bounds.width >= 0)
            assertTrue("target height must be >= 0: $bounds", bounds.height >= 0)
            assertTrue("target left overflow: $bounds", bounds.left > Int.MIN_VALUE / 2)
            assertTrue("target top overflow: $bounds", bounds.top > Int.MIN_VALUE / 2)
            assertTrue("target right overflow: $bounds", bounds.left + bounds.width < Int.MAX_VALUE / 2)
            assertTrue("target bottom overflow: $bounds", bounds.top + bounds.height < Int.MAX_VALUE / 2)
        }
    }

    private fun runFuzzCase(seed: Long, iteration: Int, tester: PixelTester?, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            throw AssertionError(
                buildString {
                    appendLine("Fuzz failure seed=$seed iteration=$iteration")
                    if (tester != null) {
                        appendLine("Element tree:")
                        appendLine(tester.dumpElementTree())
                        appendLine("Render tree:")
                        appendLine(tester.dumpRenderTree())
                    }
                },
                error,
            )
        }
    }

    private fun topOffsets(heights: IntArray, spacing: Int): IntArray {
        var cursor = 0
        return IntArray(heights.size) { index ->
            val top = cursor
            cursor += heights[index]
            if (index < heights.lastIndex) {
                cursor += spacing
            }
            top
        }
    }

    private fun contentHeight(heights: IntArray, spacing: Int): Int {
        if (heights.isEmpty()) return 0
        return heights.sum() + spacing * (heights.size - 1)
    }

    private fun randomInsets(random: Random): PixelWindowInsets {
        return PixelWindowInsets(
            left = random.nextInt(8),
            top = random.nextInt(8),
            right = random.nextInt(8),
            bottom = random.nextInt(8),
        )
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Expected IllegalArgumentException")
    }
}
