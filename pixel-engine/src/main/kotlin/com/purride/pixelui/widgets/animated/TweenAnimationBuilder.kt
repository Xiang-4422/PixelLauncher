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

/** 创建 `TweenAnimationBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
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
    /** Normalized clock shared by the currently owned tween segment. */
    private lateinit var controller: PixelAnimationController

    /** Curve projection applied to the normalized controller value. */
    private lateinit var curved: CurvedAnimation

    /** Tween segment owned and, on retarget, rebased by this State. */
    private lateinit var activeTween: Tween<T>

    /** Target used to detect changes even when callers reuse a mutable Tween instance. */
    private var activeTarget: Any? = null

    /** Last value actually built, used as the next segment's visually continuous origin. */
    private var renderedValue: Any? = null

    /** Creates the first segment and starts it from its declared begin value. */
    override fun initState() {
        activeTween = widget.tween
        activeTarget = activeTween.end
        renderedValue = activeTween.begin
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.addListener {
            if (controller.status == PixelAnimationStatus.Completed) {
                widget.onEnd?.invoke()
            }
        }
        controller.forward()
    }

    /** Rebases a changed target to the last rendered value before restarting normalized progress. */
    @Suppress("UNCHECKED_CAST")
    override fun didUpdateWidget(oldWidget: TweenAnimationBuilderWidget<T>) {
        if (widget.tween.end != activeTarget) {
            val visualOrigin = renderedValue as T
            activeTween = widget.tween
            activeTween.begin = visualOrigin
            activeTarget = activeTween.end
            controller.forward(from = 0f)
        }
    }

    /** Releases the owned normalized clock and its ticker. */
    override fun dispose() {
        controller.dispose()
    }

    /** Builds the current segment and records the exact value used for future retargeting. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val value = activeTween.lerp(curved.value)
        renderedValue = value
        return widget.builderFn(context, value)
    }
}
