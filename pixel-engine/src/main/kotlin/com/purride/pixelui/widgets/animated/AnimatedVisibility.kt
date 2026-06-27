package com.purride.pixelui.widgets.animated

import com.purride.pixelui.BuildContext
import com.purride.pixelui.SizedBox
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/**
 * 在显示内容和隐藏占位之间做像素风切换动画。
 *
 * 组件复用 [AnimatedSwitcher] 的相位逻辑；隐藏后渲染 [replacement]，不额外保留 child 状态。
 */
public fun AnimatedVisibility(
    visible: Boolean,
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    replacement: Widget = SizedBox(width = 0, height = 0),
    key: Any? = null,
    child: Widget,
): Widget {
    return AnimatedSwitcher(
        duration = duration,
        vsync = vsync,
        curve = curve,
        key = key,
        child = AnimatedVisibilitySlot(
            child = if (visible) child else replacement,
            key = if (visible) AnimatedVisibilitySlotKey.VISIBLE else AnimatedVisibilitySlotKey.HIDDEN,
        ),
    )
}

private enum class AnimatedVisibilitySlotKey { VISIBLE, HIDDEN }

private class AnimatedVisibilitySlot(
    private val child: Widget,
    key: Any,
) : StatelessWidget(key = key) {
    override fun build(context: BuildContext): Widget = child
}
