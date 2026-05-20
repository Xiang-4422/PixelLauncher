package com.purride.pixelui.animation

import kotlin.math.floor

public fun interface Curve {
    public fun transform(t: Float): Float
}

public object Curves {
    public val Linear: Curve = Curve { it }

    public val EaseIn: Curve = Curve { t -> t * t }

    public val EaseOut: Curve = Curve { t -> 1f - (1f - t) * (1f - t) }

    public val EaseInOut: Curve = Curve { t ->
        if (t < 0.5f) 2f * t * t
        else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
    }

    public fun Step(n: Int): Curve = Curve { t ->
        if (t >= 1f) 1f else floor(t * n) / n
    }
}

public class Interval(
    public val begin: Float,
    public val end: Float,
    public val child: Curve = Curves.Linear,
) : Curve {
    override fun transform(t: Float): Float = when {
        t <= begin -> 0f
        t >= end -> 1f
        else -> child.transform((t - begin) / (end - begin))
    }
}
