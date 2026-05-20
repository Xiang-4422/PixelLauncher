package com.purride.pixelui.animation

import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback

public class CurvedAnimation(
    private val parent: Animation<Float>,
    public val curve: Curve,
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
