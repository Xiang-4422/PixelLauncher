package com.purride.pixelui.widgets.animated

import com.purride.pixelui.BuildContext
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

public fun AnimatedOpacity(
    opacity: Float,
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    key: Any? = null,
    child: Widget,
): Widget = AnimatedOpacityWidget(
    opacity = opacity,
    duration = duration,
    vsync = vsync,
    curve = curve,
    child = child,
    key = key,
)

/**
 * Maps a 0..1 opacity value to 3 discrete tiers matching the pixel aesthetic:
 * 0 (invisible), 0.5 (semi-visible), 1 (fully visible).
 */
public fun quantizeOpacity(t: Float): Float = when {
    t < 0.25f -> 0f
    t > 0.75f -> 1f
    else -> 0.5f
}

private class AnimatedOpacityWidget(
    val opacity: Float,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedOpacityState()
}

private class AnimatedOpacityState : State<AnimatedOpacityWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var fromOpacity: Float = 1f
    private var toOpacity: Float = 1f

    override fun initState() {
        fromOpacity = widget.opacity
        toOpacity = widget.opacity
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.setValue(1f)
    }

    override fun didUpdateWidget(oldWidget: AnimatedOpacityWidget) {
        if (widget.opacity != oldWidget.opacity) {
            fromOpacity = lerpOpacity(curved.value)
            toOpacity = widget.opacity
            controller.forward(from = 0f)
        }
    }

    private fun lerpOpacity(t: Float): Float = fromOpacity + (toOpacity - fromOpacity) * t

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val rawOpacity = lerpOpacity(curved.value)
        val quantized = quantizeOpacity(rawOpacity)
        return when {
            quantized <= 0f -> SizedBox(child = widget.child)
            else -> widget.child
        }
    }
}
