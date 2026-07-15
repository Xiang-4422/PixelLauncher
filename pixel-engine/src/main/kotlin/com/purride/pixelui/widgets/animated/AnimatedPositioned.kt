package com.purride.pixelui.widgets.animated

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Positioned
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/** 创建 `AnimatedPositioned` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun AnimatedPositioned(
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    left: Int? = null,
    top: Int? = null,
    right: Int? = null,
    bottom: Int? = null,
    key: Any? = null,
    child: Widget,
): Widget = AnimatedPositionedWidget(
    duration = duration,
    vsync = vsync,
    curve = curve,
    left = left,
    top = top,
    right = right,
    bottom = bottom,
    child = child,
    key = key,
)

private class AnimatedPositionedWidget(
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val left: Int?,
    val top: Int?,
    val right: Int?,
    val bottom: Int?,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedPositionedState()
}

private class AnimatedPositionedState : State<AnimatedPositionedWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var leftTween: IntTween? = null
    private var topTween: IntTween? = null
    private var rightTween: IntTween? = null
    private var bottomTween: IntTween? = null

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        widget.left?.let { leftTween = IntTween(it, it) }
        widget.top?.let { topTween = IntTween(it, it) }
        widget.right?.let { rightTween = IntTween(it, it) }
        widget.bottom?.let { bottomTween = IntTween(it, it) }
        controller.setValue(1f)
    }

    override fun didUpdateWidget(oldWidget: AnimatedPositionedWidget) {
        var changed = false
        if (widget.left != oldWidget.left) {
            val from = leftTween?.lerp(curved.value) ?: oldWidget.left ?: widget.left ?: 0
            leftTween = widget.left?.let { IntTween(from, it) }
            changed = true
        }
        if (widget.top != oldWidget.top) {
            val from = topTween?.lerp(curved.value) ?: oldWidget.top ?: widget.top ?: 0
            topTween = widget.top?.let { IntTween(from, it) }
            changed = true
        }
        if (widget.right != oldWidget.right) {
            val from = rightTween?.lerp(curved.value) ?: oldWidget.right ?: widget.right ?: 0
            rightTween = widget.right?.let { IntTween(from, it) }
            changed = true
        }
        if (widget.bottom != oldWidget.bottom) {
            val from = bottomTween?.lerp(curved.value) ?: oldWidget.bottom ?: widget.bottom ?: 0
            bottomTween = widget.bottom?.let { IntTween(from, it) }
            changed = true
        }
        if (changed) controller.forward(from = 0f)
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val t = curved.value
        return Positioned(
            left = leftTween?.lerp(t) ?: widget.left,
            top = topTween?.lerp(t) ?: widget.top,
            right = rightTween?.lerp(t) ?: widget.right,
            bottom = bottomTween?.lerp(t) ?: widget.bottom,
            child = widget.child,
        )
    }
}
