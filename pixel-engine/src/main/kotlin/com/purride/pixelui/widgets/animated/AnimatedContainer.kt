package com.purride.pixelui.widgets.animated

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.EdgeInsetsTween
import com.purride.pixelui.animation.IntTween
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.animation.PixelToneTween
import kotlin.time.Duration

public fun AnimatedContainer(
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    borderTone: PixelTone? = null,
    onEnd: (() -> Unit)? = null,
    key: Any? = null,
    child: Widget? = null,
): Widget = AnimatedContainerWidget(
    duration = duration,
    vsync = vsync,
    curve = curve,
    width = width,
    height = height,
    padding = padding,
    margin = margin,
    borderTone = borderTone,
    onEnd = onEnd,
    child = child,
    key = key,
)

private class AnimatedContainerWidget(
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val width: Int?,
    val height: Int?,
    val padding: EdgeInsets?,
    val margin: EdgeInsets?,
    val borderTone: PixelTone?,
    val onEnd: (() -> Unit)?,
    val child: Widget?,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedContainerState()
}

private class AnimatedContainerState : State<AnimatedContainerWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation

    private var widthTween: IntTween? = null
    private var heightTween: IntTween? = null
    private var paddingTween: EdgeInsetsTween? = null
    private var marginTween: EdgeInsetsTween? = null
    private var borderToneTween: PixelToneTween? = null

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.addListener {
            if (controller.status == PixelAnimationStatus.Completed) widget.onEnd?.invoke()
        }
        widget.width?.let { widthTween = IntTween(it, it) }
        widget.height?.let { heightTween = IntTween(it, it) }
        widget.padding?.let { paddingTween = EdgeInsetsTween(it, it) }
        widget.margin?.let { marginTween = EdgeInsetsTween(it, it) }
        widget.borderTone?.let { borderToneTween = PixelToneTween(it, it) }
    }

    override fun didUpdateWidget(oldWidget: AnimatedContainerWidget) {
        var needsAnimation = false

        if (widget.width != oldWidget.width) {
            val from = widthTween?.lerp(curved.value) ?: (oldWidget.width ?: widget.width ?: 0)
            widthTween = widget.width?.let { IntTween(from, it) }
            needsAnimation = true
        }
        if (widget.height != oldWidget.height) {
            val from = heightTween?.lerp(curved.value) ?: (oldWidget.height ?: widget.height ?: 0)
            heightTween = widget.height?.let { IntTween(from, it) }
            needsAnimation = true
        }
        if (widget.padding != oldWidget.padding) {
            val newPadding = widget.padding
            val from = paddingTween?.lerp(curved.value) ?: oldWidget.padding ?: newPadding
            paddingTween = if (newPadding != null && from != null) EdgeInsetsTween(from, newPadding) else null
            needsAnimation = true
        }
        if (widget.margin != oldWidget.margin) {
            val newMargin = widget.margin
            val from = marginTween?.lerp(curved.value) ?: oldWidget.margin ?: newMargin
            marginTween = if (newMargin != null && from != null) EdgeInsetsTween(from, newMargin) else null
            needsAnimation = true
        }
        if (widget.borderTone != oldWidget.borderTone) {
            val newTone = widget.borderTone
            val from = borderToneTween?.lerp(curved.value) ?: oldWidget.borderTone ?: newTone
            borderToneTween = if (newTone != null && from != null) PixelToneTween(from, newTone) else null
            needsAnimation = true
        }
        if (needsAnimation) {
            controller.forward(from = 0f)
        }
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val t = curved.value
        return Container(
            width = widthTween?.lerp(t) ?: widget.width,
            height = heightTween?.lerp(t) ?: widget.height,
            padding = paddingTween?.lerp(t) ?: widget.padding,
            margin = marginTween?.lerp(t) ?: widget.margin,
            borderTone = borderToneTween?.lerp(t) ?: widget.borderTone,
            child = widget.child,
        )
    }
}
