package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.PixelSystemIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 系统像素图标必须在所有规格下保持确定尺寸、颜色与核心轮廓。 */
class PixelSystemIconsTest {
    /** 每个语义与规格组合都应生成非空、无插值颜色的正方形位图。 */
    @Test
    fun everyIconRendersOpaqueMaskAtEveryDeclaredSize() {
        PixelSystemIconSize.entries.forEach { size ->
            PixelSystemIcon.entries.forEach { icon ->
                /** 当前语义与规格生成的标准白色遮罩。 */
                val bitmap = PixelSystemIcons.mask(icon = icon, size = size).bitmap
                /** 当前位图实际包含的非透明墨迹像素。 */
                val inkCount = bitmap.pixels.count { pixel -> pixel == PixelColor.White.argb }

                assertEquals(size.pixels, bitmap.width)
                assertEquals(size.pixels, bitmap.height)
                assertTrue("$icon/$size must contain visible ink", inkCount > 0)
                assertTrue(
                    "$icon/$size must contain only transparent and opaque-white pixels",
                    bitmap.pixels.all { pixel ->
                        pixel == PixelColor.Transparent.argb || pixel == PixelColor.White.argb
                    },
                )
            }
        }
    }

    /** 同一图标请求复用缓存，不同主题色则生成独立的着色位图。 */
    @Test
    fun iconCacheIncludesResolvedColor() {
        /** 用于验证主题着色通道的非默认颜色。 */
        val amber = PixelColor.fromRgb(255, 190, 60)
        /** 首次生成的白色标准遮罩。 */
        val firstMask = PixelSystemIcons.mask(PixelSystemIcon.SEARCH, PixelSystemIconSize.MEDIUM)
        /** 相同请求应返回同一个不可变缓存实例。 */
        val secondMask = PixelSystemIcons.mask(PixelSystemIcon.SEARCH, PixelSystemIconSize.MEDIUM)
        /** 使用主题颜色生成的独立图标。 */
        val tinted = PixelSystemIcons.tinted(
            icon = PixelSystemIcon.SEARCH,
            size = PixelSystemIconSize.MEDIUM,
            color = amber,
        )

        assertSame(firstMask, secondMask)
        assertNotEquals(firstMask, tinted)
        assertTrue(tinted.bitmap.pixels.any { pixel -> pixel == amber.argb })
        assertTrue(
            tinted.bitmap.pixels.all { pixel -> pixel == PixelColor.Transparent.argb || pixel == amber.argb },
        )
    }

    /** 小规格 STYLE 图标使用经过评审的稳定像素轮廓。 */
    @Test
    fun smallStyleIconsMatchReviewedMasks() {
        assertEquals(
            listOf(
                ".......",
                ".#####.",
                ".#...#.",
                ".#...#.",
                ".#...#.",
                ".#####.",
                ".......",
            ),
            maskRows(PixelSystemIcon.SQUARE),
        )
        assertEquals(
            listOf(
                ".......",
                "..###..",
                ".#...#.",
                ".#...#.",
                ".#...#.",
                "..###..",
                ".......",
            ),
            maskRows(PixelSystemIcon.CIRCLE),
        )
        assertEquals(
            listOf(
                ".......",
                "...#...",
                "..#.#..",
                ".#...#.",
                "..#.#..",
                "...#...",
                ".......",
            ),
            maskRows(PixelSystemIcon.DIAMOND),
        )
    }

    /** 小规格 ALIGN 图标必须通过短横线锚点明确区分三种方向。 */
    @Test
    fun smallAlignmentIconsKeepDistinctAnchors() {
        assertEquals(
            listOf(
                ".......",
                ".#####.",
                ".......",
                ".###...",
                ".......",
                ".#####.",
                ".......",
            ),
            maskRows(PixelSystemIcon.ALIGN_LEFT),
        )
        assertEquals(
            listOf(
                ".......",
                ".#####.",
                ".......",
                "..###..",
                ".......",
                ".#####.",
                ".......",
            ),
            maskRows(PixelSystemIcon.ALIGN_CENTER),
        )
        assertEquals(
            listOf(
                ".......",
                ".#####.",
                ".......",
                "...###.",
                ".......",
                ".#####.",
                ".......",
            ),
            maskRows(PixelSystemIcon.ALIGN_RIGHT),
        )
    }

    /** 小规格 MODE 图标保持太阳、循环箭头与满月三种可区分轮廓。 */
    @Test
    fun smallModeIconsMatchReviewedMasks() {
        assertEquals(
            listOf(
                "...#...",
                ".#...#.",
                "...#...",
                "#.###.#",
                "...#...",
                ".#...#.",
                "...#...",
            ),
            maskRows(PixelSystemIcon.DAY),
        )
        assertEquals(
            listOf(
                ".......",
                "....#..",
                ".#####.",
                "..#.#..",
                ".#####.",
                "..#....",
                ".......",
            ),
            maskRows(PixelSystemIcon.AUTO),
        )
        assertEquals(
            listOf(
                "..###..",
                ".#####.",
                "####.##",
                "#######",
                "##.####",
                ".#####.",
                "..###..",
            ),
            maskRows(PixelSystemIcon.NIGHT),
        )
    }

    /** 满月四档均使用独立审校轮廓，防止缩放或圆盘相减破坏小尺寸可读性。 */
    @Test
    fun nightIconMatchesReviewedMaskAtEveryDeclaredSize() {
        /** 每种尺寸对应的稳定满月像素快照。 */
        val reviewedMasks = mapOf(
            PixelSystemIconSize.SMALL to listOf(
                "..###..",
                ".#####.",
                "####.##",
                "#######",
                "##.####",
                ".#####.",
                "..###..",
            ),
            PixelSystemIconSize.MEDIUM to listOf(
                "...###...",
                ".#######.",
                "#####..##",
                "#####.###",
                "##.######",
                "#########",
                "#####.###",
                ".#######.",
                "...###...",
            ),
            PixelSystemIconSize.LARGE to listOf(
                "....###....",
                "..#######..",
                ".######..#.",
                "#######.###",
                "###########",
                "##..#######",
                "###.#######",
                "###########",
                ".######..#.",
                "..#######..",
                "....###....",
            ),
            PixelSystemIconSize.EXTRA_LARGE to listOf(
                "......###......",
                "....#######....",
                "..#######..##..",
                ".########..###.",
                "###############",
                "###############",
                "###..##########",
                "###..##########",
                "###############",
                "#########.#####",
                "#########..####",
                "###############",
                "..###########..",
                "....#######....",
                "......###......",
            ),
        )

        reviewedMasks.forEach { (size, expectedRows) ->
            assertEquals(expectedRows, maskRows(icon = PixelSystemIcon.NIGHT, size = size))
        }
    }

    /** 小规格方向图标必须保持镜像关系，并与语音信箱符号明确区分。 */
    @Test
    fun smallNavigationIconsMatchReviewedMasks() {
        assertEquals(
            listOf(
                ".......",
                "...#...",
                "..#....",
                ".#####.",
                "..#....",
                "...#...",
                ".......",
            ),
            maskRows(PixelSystemIcon.BACK),
        )
        assertEquals(
            listOf(
                ".......",
                "...#...",
                "....#..",
                ".#####.",
                "....#..",
                "...#...",
                ".......",
            ),
            maskRows(PixelSystemIcon.FORWARD),
        )
        assertEquals(
            listOf(
                ".......",
                "...#...",
                "..###..",
                ".#.#.#.",
                "...#...",
                "...#...",
                ".......",
            ),
            maskRows(PixelSystemIcon.ARROW_UP),
        )
        assertEquals(
            listOf(
                ".......",
                "...#...",
                "...#...",
                ".#.#.#.",
                "..###..",
                "...#...",
                ".......",
            ),
            maskRows(PixelSystemIcon.ARROW_DOWN),
        )
        assertEquals(
            listOf(
                ".......",
                ".......",
                ".#...#.",
                "#.#.#.#",
                ".#####.",
                ".......",
                ".......",
            ),
            maskRows(PixelSystemIcon.VOICEMAIL),
        )
    }

    /** 把指定规格的标准遮罩转换为便于评审的 ASCII 行。 */
    private fun maskRows(
        icon: PixelSystemIcon,
        size: PixelSystemIconSize = PixelSystemIconSize.SMALL,
    ): List<String> {
        /** 当前图标的白色遮罩位图。 */
        val bitmap = PixelSystemIcons.mask(icon = icon, size = size).bitmap
        return List(bitmap.height) { y ->
            buildString(bitmap.width) {
                repeat(bitmap.width) { x ->
                    append(if (bitmap.pixelAt(x, y) == PixelColor.Transparent.argb) '.' else '#')
                }
            }
        }
    }
}
