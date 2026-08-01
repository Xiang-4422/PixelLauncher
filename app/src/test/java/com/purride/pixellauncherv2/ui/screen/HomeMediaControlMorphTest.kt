package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.PixelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOME 媒体播放/暂停图标必须保持确定端点、像素对齐与连续中间帧。 */
class HomeMediaControlMorphTest {
    /** 播放端点保持原三角形，暂停端点准确落成两条等高竖线。 */
    @Test
    fun morphEndpointsMatchPlayAndPauseGeometry() {
        /** 播放状态对应的起始几何。 */
        val play = mediaPlayPauseMorphGeometry(step = 0)
        /** 暂停状态对应的终止几何。 */
        val pause = mediaPlayPauseMorphGeometry(step = 4)

        assertEquals(
            listOf(PixelPoint(3, 1), PixelPoint(3, 9), PixelPoint(10, 5), PixelPoint(10, 5)),
            play.leftShape,
        )
        assertEquals(10, play.rightLeft)
        assertEquals(5, play.rightTop)
        assertEquals(1, play.rightWidth)
        assertEquals(1, play.rightHeight)
        assertEquals(
            listOf(PixelPoint(3, 1), PixelPoint(3, 9), PixelPoint(4, 9), PixelPoint(4, 1)),
            pause.leftShape,
        )
        assertEquals(8, pause.rightLeft)
        assertEquals(1, pause.rightTop)
        assertEquals(2, pause.rightWidth)
        assertEquals(9, pause.rightHeight)
    }

    /** 中间帧同时收拢左主体并展开右竖线，不允许先消失再突然出现。 */
    @Test
    fun middleFrameKeepsBothMorphingShapesVisible() {
        /** 五帧序列中央的离散形变几何。 */
        val middle = mediaPlayPauseMorphGeometry(step = 2)

        assertEquals(PixelPoint(7, 7), middle.leftShape[2])
        assertEquals(PixelPoint(7, 3), middle.leftShape[3])
        assertEquals(9, middle.rightLeft)
        assertEquals(3, middle.rightTop)
        assertTrue(middle.rightWidth > 0)
        assertTrue(middle.rightHeight > 1)
    }

    /** 越界动画帧必须限制到合法端点，避免异常状态画出画布。 */
    @Test
    fun morphStepClampsToReviewedEndpoints() {
        assertEquals(mediaPlayPauseMorphGeometry(0), mediaPlayPauseMorphGeometry(-10))
        assertEquals(mediaPlayPauseMorphGeometry(4), mediaPlayPauseMorphGeometry(10))
    }

    /** 跳曲反馈必须沿指定方向移出后回弹，并在动画首尾保持原位。 */
    @Test
    fun skipFeedbackMovesInDirectionAndReturnsToOrigin() {
        assertEquals(0, mediaSkipFeedbackOffset(progress = 0f, direction = -1))
        assertEquals(-2, mediaSkipFeedbackOffset(progress = 0.5f, direction = -1))
        assertEquals(2, mediaSkipFeedbackOffset(progress = 0.5f, direction = 1))
        assertEquals(0, mediaSkipFeedbackOffset(progress = 1f, direction = 1))
    }

    /** 跳曲反馈限制异常输入和方向倍率，避免图标移出按钮。 */
    @Test
    fun skipFeedbackSanitizesProgressAndDirection() {
        assertEquals(0, mediaSkipFeedbackOffset(progress = Float.NaN, direction = 1))
        assertEquals(0, mediaSkipFeedbackOffset(progress = -1f, direction = -1))
        assertEquals(0, mediaSkipFeedbackOffset(progress = 2f, direction = 1))
        assertEquals(2, mediaSkipFeedbackOffset(progress = 0.5f, direction = 99))
        assertEquals(0, mediaSkipFeedbackOffset(progress = 0.5f, direction = 0))
    }
}
