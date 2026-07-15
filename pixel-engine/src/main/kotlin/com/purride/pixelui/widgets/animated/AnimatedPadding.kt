package com.purride.pixelui.widgets.animated

import com.purride.pixelui.Alignment
import com.purride.pixelui.Align
import com.purride.pixelui.BuildContext
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Padding
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.EdgeInsetsTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/** 创建 `AnimatedPadding` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun AnimatedPadding(
    padding: EdgeInsets,
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    key: Any? = null,
    child: Widget,
): Widget = AnimatedPaddingWidget(
    padding = padding,
    duration = duration,
    vsync = vsync,
    curve = curve,
    child = child,
    key = key,
)

private class AnimatedPaddingWidget(
    val padding: EdgeInsets,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedPaddingState()
}

private class AnimatedPaddingState : State<AnimatedPaddingWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private lateinit var tween: EdgeInsetsTween

    override fun initState() {
        tween = EdgeInsetsTween(widget.padding, widget.padding)
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.setValue(1f)
    }

    override fun didUpdateWidget(oldWidget: AnimatedPaddingWidget) {
        if (widget.padding != oldWidget.padding) {
            tween = EdgeInsetsTween(tween.lerp(curved.value), widget.padding)
            controller.forward(from = 0f)
        }
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return Padding(padding = tween.lerp(curved.value), child = widget.child)
    }
}

/** 创建 `AnimatedAlign` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun AnimatedAlign(
    alignment: Alignment,
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    key: Any? = null,
    child: Widget,
): Widget = AnimatedAlignWidget(
    alignment = alignment,
    duration = duration,
    vsync = vsync,
    curve = curve,
    child = child,
    key = key,
)

private class AnimatedAlignWidget(
    val alignment: Alignment,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedAlignState()
}

private class AnimatedAlignState : State<AnimatedAlignWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var fromAlignment: Alignment = Alignment.TOP_START
    private var toAlignment: Alignment = Alignment.TOP_START

    override fun initState() {
        fromAlignment = widget.alignment
        toAlignment = widget.alignment
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.setValue(1f)
    }

    override fun didUpdateWidget(oldWidget: AnimatedAlignWidget) {
        if (widget.alignment != oldWidget.alignment) {
            fromAlignment = currentAlignment()
            toAlignment = widget.alignment
            controller.forward(from = 0f)
        }
    }

    private fun currentAlignment(): Alignment =
        if (curved.value < 0.5f) fromAlignment else toAlignment

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val alignment = currentAlignment()
        return Align(alignment = alignment, child = widget.child)
    }
}
