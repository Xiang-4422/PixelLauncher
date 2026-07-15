package com.purride.pixelui.widgets.animated

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Opacity
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/**
 * 在当前视觉透明度与目标 [opacity] 之间执行补间。
 *
 * 目标会规范化到 `0f..1f`，非有限值按 `0f` 处理。动画始终保留同一个 opacity render
 * wrapper，因此快速 retarget 从屏幕上已经显示的值继续，不会重挂 child 或跳回旧起点。
 * 当视觉透明度为零时 child 仍参与 layout 并保留 State，但不 paint、不命中且不暴露
 * semantics；大于零时三者均正常参与。
 */
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
 * 执行 `AnimatedOpacity` 的 `quantizeOpacity` 公开行为；具体参数、返回和副作用见下文。
 *
 * Maps a 0..1 opacity value to 3 discrete tiers used by pixel-style transition widgets.
 *
 * [AnimatedOpacity] itself preserves the curve's continuous value; this helper remains the
 * explicit quantization policy for callers such as `AnimatedSwitcher`.
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
    /** Drives normalized `0f..1f` progress for the current retarget segment. */
    private lateinit var controller: PixelAnimationController

    /** Applies the caller-selected curve to [controller]. */
    private lateinit var curved: CurvedAnimation

    /** Effective visual opacity captured when the current segment began. */
    private var fromOpacity: Float = 1f

    /** Normalized target opacity of the current segment. */
    private var toOpacity: Float = 1f

    /** Initializes a settled segment without allocating an active ticker frame. */
    override fun initState() {
        val initialOpacity = normalizeOpacity(widget.opacity)
        fromOpacity = initialOpacity
        toOpacity = initialOpacity
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.setValue(1f)
    }

    /** Retargets from the current visual value and restarts only the segment progress. */
    override fun didUpdateWidget(oldWidget: AnimatedOpacityWidget) {
        val nextOpacity = normalizeOpacity(widget.opacity)
        if (nextOpacity == toOpacity) return
        fromOpacity = currentVisualOpacity()
        toOpacity = nextOpacity
        controller.forward(from = 0f)
    }

    /** Resolves the clamped value that is actually visible at the current curved progress. */
    private fun currentVisualOpacity(): Float {
        val interpolated = fromOpacity + (toOpacity - fromOpacity) * curved.value
        return normalizeOpacity(interpolated)
    }

    /** Releases the controller and its provider-owned ticker exactly once with State disposal. */
    override fun dispose() {
        controller.dispose()
    }

    /** Keeps one stable [Opacity] wrapper across zero, intermediate and fully opaque frames. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return Opacity(
            opacity = currentVisualOpacity(),
            child = widget.child,
        )
    }
}

/** Converts arbitrary caller input into the opacity value used for paint and retargeting. */
private fun normalizeOpacity(value: Float): Float {
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}
