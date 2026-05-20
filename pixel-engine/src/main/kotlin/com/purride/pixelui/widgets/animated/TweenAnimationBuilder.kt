package com.purride.pixelui.widgets.animated

import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.Tween
import kotlin.time.Duration

public fun <T> TweenAnimationBuilder(
    tween: Tween<T>,
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    onEnd: (() -> Unit)? = null,
    key: Any? = null,
    builder: (BuildContext, T) -> Widget,
): Widget = TweenAnimationBuilderWidget(
    tween = tween,
    duration = duration,
    vsync = vsync,
    curve = curve,
    onEnd = onEnd,
    builderFn = builder,
    key = key,
)

private class TweenAnimationBuilderWidget<T>(
    val tween: Tween<T>,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val onEnd: (() -> Unit)?,
    val builderFn: (BuildContext, T) -> Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TweenAnimationBuilderState<T>()
}

private class TweenAnimationBuilderState<T> : State<TweenAnimationBuilderWidget<T>>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.addListener {
            if (controller.status == PixelAnimationStatus.Completed) {
                widget.onEnd?.invoke()
            }
        }
        controller.forward()
    }

    override fun didUpdateWidget(oldWidget: TweenAnimationBuilderWidget<T>) {
        if (widget.tween.end != oldWidget.tween.end) {
            controller.forward(from = controller.value)
        }
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return widget.builderFn(context, widget.tween.lerp(curved.value))
    }
}
