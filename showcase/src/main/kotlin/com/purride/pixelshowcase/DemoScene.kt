package com.purride.pixelshowcase

import com.purride.pixelcore.PixelBuffer

/**
 * 一个自包含的演示场景：拿到画布尺寸、按时间推进、把当帧画进 [PixelBuffer]。
 *
 * 场景之间互不相识，全部纯数学 + 引擎画布——这正是要展示的点：
 * 引擎给你一块逻辑像素画布和一个帧循环，剩下的只是想象力。
 */
interface DemoScene {
    /** 场景短名，画在屏幕左上角。 */
    val title: String

    /** 自动轮播时长（秒）。 */
    val durationSeconds: Float
        get() = 8f

    /** 进入场景（或画布尺寸变化）时重置内部状态。 */
    fun reset(width: Int, height: Int)

    /** 推进一帧。[elapsed] 为进入本场景以来的秒数。 */
    fun update(dt: Float, elapsed: Float)

    /** 绘制当帧。buffer 已清空为背景色。 */
    fun render(buffer: PixelBuffer)
}
