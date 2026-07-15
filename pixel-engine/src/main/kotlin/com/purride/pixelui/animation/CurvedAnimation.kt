package com.purride.pixelui.animation

import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback

/** 定义 `CurvedAnimation` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class CurvedAnimation(
    private val parent: Animation<Float>,
    /** 记录 `CurvedAnimation` 的 `curve` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val curve: Curve,
    /** 记录 `CurvedAnimation` 的 `reverseCurve` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val reverseCurve: Curve? = null,
) : Animation<Float> {

    private val activeCurve: Curve
        get(): Curve = if (parent.status == PixelAnimationStatus.Reverse) reverseCurve ?: curve else curve

    override val value: Float
        get(): Float = activeCurve.transform(parent.value)

    override val status: PixelAnimationStatus
        get(): PixelAnimationStatus = parent.status

    override fun addListener(listener: VoidCallback): Unit = parent.addListener(listener)

    override fun removeListener(listener: VoidCallback): Unit = parent.removeListener(listener)
}
