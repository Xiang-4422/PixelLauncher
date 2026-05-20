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
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

public fun AnimatedSwitcher(
    duration: Duration,
    vsync: PixelTickerProvider,
    curve: Curve = Curves.Step(8),
    key: Any? = null,
    child: Widget,
): Widget = AnimatedSwitcherWidget(
    duration = duration,
    vsync = vsync,
    curve = curve,
    child = child,
    key = key,
)

private class AnimatedSwitcherWidget(
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val curve: Curve,
    val child: Widget,
    override val key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = AnimatedSwitcherState()
}

private enum class SwitchPhase { Idle, FadingOut, FadingIn }

private class AnimatedSwitcherState : State<AnimatedSwitcherWidget>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var phase: SwitchPhase = SwitchPhase.Idle
    private var displayChild: Widget? = null

    override fun initState() {
        displayChild = widget.child
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = widget.curve)
        controller.addListener { onControllerTick() }
        controller.setValue(1f)
    }

    private fun childIdentity(w: Widget): Any = w.key ?: w::class

    private fun onControllerTick() {
        if (controller.status == PixelAnimationStatus.Completed) {
            when (phase) {
                SwitchPhase.FadingOut -> {
                    displayChild = widget.child
                    phase = SwitchPhase.FadingIn
                    controller.forward(from = 0f)
                }
                SwitchPhase.FadingIn -> {
                    phase = SwitchPhase.Idle
                }
                else -> Unit
            }
        }
    }

    override fun didUpdateWidget(oldWidget: AnimatedSwitcherWidget) {
        val newIdentity = childIdentity(widget.child)
        val oldIdentity = childIdentity(oldWidget.child)
        if (newIdentity != oldIdentity) {
            when (phase) {
                SwitchPhase.Idle -> {
                    phase = SwitchPhase.FadingOut
                    controller.forward(from = 0f)
                }
                SwitchPhase.FadingIn -> {
                    phase = SwitchPhase.FadingOut
                    val currentOpacity = 1f - curved.value
                    controller.forward(from = 1f - currentOpacity)
                }
                SwitchPhase.FadingOut -> Unit
            }
        }
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val t = curved.value
        val quantized = quantizeOpacity(t)
        val child = displayChild ?: widget.child
        return when (phase) {
            SwitchPhase.FadingOut -> {
                val fadeOutOpacity = 1f - t
                val q = quantizeOpacity(fadeOutOpacity)
                if (q <= 0f) SizedBox(child = child) else child
            }
            SwitchPhase.FadingIn -> {
                if (quantized <= 0f) SizedBox(child = child) else child
            }
            SwitchPhase.Idle -> child
        }
    }
}
