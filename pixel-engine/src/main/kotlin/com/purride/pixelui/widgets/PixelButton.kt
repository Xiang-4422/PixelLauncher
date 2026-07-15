package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelColorTween
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration

/**
 * 像素按钮样式。
 *
 * 填充色、边框色和文本样式直接用 [PixelColor] 指定；引擎只做像素渲染，
 * 不再区分 tone / colorMode。
 */
public data class PixelButtonStyle(
    val fillColor: PixelColor? = null,
    val borderColor: PixelColor? = PixelColor.fromRgb(255, 255, 255),
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val alignment: Alignment = Alignment.CENTER,
) {
    /** 集中提供 `PixelButton` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelButton` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelButtonStyle = PixelButtonStyle()
    }
}

/**
 * 无边框文字按钮样式。
 *
 * 默认 padding 为零，按钮尺寸由文字自然决定；需要扩大点击区域时由调用方显式设置。
 */
public data class PixelTextButtonStyle(
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val alignment: Alignment = Alignment.CENTER,
    val padding: EdgeInsets = EdgeInsets.all(0),
) {
    /** 集中提供 `PixelButton` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelButton` 的 `Default` 稳定默认值或常量。 */
        public val Default: PixelTextButtonStyle = PixelTextButtonStyle()
    }
}

/**
 * 标准控件共享的单值运动驱动器。
 *
 * 该驱动器只使用 [PixelMotionScope] 提供的时钟；缺少 scope、系统关闭动画或解析后的
 * 时长为零时会同步落到目标值，因此标准控件不会偷偷创建独立时钟。每次反向或重定向
 * 都先捕获当前视觉值，再创建新的归一化片段，保证快速交互没有跳帧。
 */
internal class PixelControlMotionValue(initialValue: Float) {
    /** 当前片段的视觉起点。 */
    private var segmentStart: Float = initialValue.coerceIn(0f, 1f)

    /** 当前片段的逻辑终点。 */
    private var segmentTarget: Float = segmentStart

    /** 当前片段使用的曲线。 */
    private var curve: Curve = Curves.Linear

    /** 最近一次从 Motion scope 解析出的动画时长。 */
    private var duration: Duration = Duration.ZERO

    /** 最近一次从 Motion scope 解析出的动画前置等待时长。 */
    private var delay: Duration = Duration.ZERO

    /** 最近一次从 Motion scope 取得的统一 ticker provider。 */
    private var vsync: PixelTickerProvider? = null

    /** 当前环境是否要求同步呈现终态。 */
    private var immediate: Boolean = true

    /** 当前片段拥有的归一化控制器；同步模式下始终为 null。 */
    private var controller: PixelAnimationController? = null

    /** 当前视觉值，包含曲线映射并限制在控件约定的 `0f..1f` 范围内。 */
    internal val value: Float
        get() = currentVisualValue()

    /** 当前逻辑目标，用于避免 build 阶段重复启动相同片段。 */
    internal val target: Float
        get() = segmentTarget

    /**
     * 应用当前 Motion 环境；运行中配置变化时从现有视觉值连续重建剩余片段。
     */
    internal fun configure(
        nextVsync: PixelTickerProvider?,
        nextDuration: Duration,
        nextDelay: Duration,
        nextCurve: Curve,
        nextImmediate: Boolean,
    ) {
        val environmentChanged = vsync !== nextVsync ||
            duration != nextDuration ||
            delay != nextDelay ||
            curve !== nextCurve ||
            immediate != nextImmediate
        if (!environmentChanged) return

        val visualValue = currentVisualValue()
        val retainedTarget = segmentTarget
        disposeController()
        segmentStart = visualValue
        segmentTarget = visualValue
        vsync = nextVsync
        duration = nextDuration
        delay = nextDelay
        curve = nextCurve
        immediate = nextImmediate
        animateTo(retainedTarget)
    }

    /** 让 [context] 订阅当前归一化控制器的帧通知。 */
    internal fun watch(context: BuildContext) {
        context.watch(controller)
    }

    /**
     * 从当前视觉值运动到 [targetValue]；相同目标不会重启片段。
     */
    internal fun animateTo(targetValue: Float) {
        val clampedTarget = targetValue.coerceIn(0f, 1f)
        if (clampedTarget == segmentTarget) return
        val visualValue = currentVisualValue()
        disposeController()
        segmentStart = visualValue
        segmentTarget = clampedTarget

        val provider = vsync
        val totalDuration = delay + duration
        if (immediate || provider == null || totalDuration <= Duration.ZERO || visualValue == clampedTarget) {
            segmentStart = clampedTarget
            return
        }
        controller = PixelAnimationController(
            duration = totalDuration,
            vsync = provider,
        ).also { ownedController -> ownedController.forward() }
    }

    /** 同步设置视觉值，并释放任何仍在运行的片段。 */
    internal fun snapTo(targetValue: Float) {
        val clampedTarget = targetValue.coerceIn(0f, 1f)
        disposeController()
        segmentStart = clampedTarget
        segmentTarget = clampedTarget
    }

    /** 释放驱动器拥有的 ticker 资源。 */
    internal fun dispose() {
        disposeController()
    }

    /** 根据当前片段、控制器和曲线计算这一帧真正应绘制的值。 */
    private fun currentVisualValue(): Float {
        val progress = controller?.value ?: return segmentStart.coerceIn(0f, 1f)
        val activeProgress = delayedControlProgress(
            progress = progress.coerceIn(0f, 1f),
            delay = delay,
            duration = duration,
        )
        val transformed = curve.transform(activeProgress)
            .takeIf(Float::isFinite)
            ?.coerceIn(0f, 1f)
            ?: 0f
        return (segmentStart + (segmentTarget - segmentStart) * transformed).coerceIn(0f, 1f)
    }

    /** 终止并释放当前片段拥有的控制器。 */
    private fun disposeController() {
        controller?.dispose()
        controller = null
    }
}

/**
 * Retained color transition driven by [PixelControlMotionValue].
 *
 * Component state maps resolve semantic target colors first; this object then preserves the
 * currently painted color across rapid state changes and Motion-policy retargets.
 */
internal class PixelControlColorMotion(initialValue: PixelColor) {
    /** Normalized progress for the current color segment. */
    private val progress: PixelControlMotionValue = PixelControlMotionValue(1f)

    /** Concrete color painted at progress zero. */
    private var segmentStart: PixelColor = initialValue

    /** Concrete color painted at progress one. */
    private var segmentTarget: PixelColor = initialValue

    /** Current interpolated color. */
    internal val value: PixelColor
        get() = PixelColorTween(segmentStart, segmentTarget).lerp(progress.value)

    /** Latest resolved semantic target color. */
    internal val target: PixelColor
        get() = segmentTarget

    /** Applies the current shared Motion environment without discarding visual progress. */
    internal fun configure(
        nextVsync: PixelTickerProvider?,
        nextDuration: Duration,
        nextDelay: Duration,
        nextCurve: Curve,
        nextImmediate: Boolean,
    ) {
        progress.configure(
            nextVsync = nextVsync,
            nextDuration = nextDuration,
            nextDelay = nextDelay,
            nextCurve = nextCurve,
            nextImmediate = nextImmediate,
        )
    }

    /** Makes [context] rebuild for frames produced by the active shared controller. */
    internal fun watch(context: BuildContext) {
        progress.watch(context)
    }

    /** Retargets from the currently painted color to [targetColor] without a discontinuity. */
    internal fun animateTo(targetColor: PixelColor) {
        if (targetColor == segmentTarget) return
        /** Exact current frame captured before the normalized segment is reset. */
        val visualColor = value
        progress.snapTo(0f)
        segmentStart = visualColor
        segmentTarget = targetColor
        progress.animateTo(1f)
    }

    /** Applies [targetColor] synchronously and releases any active ticker. */
    internal fun snapTo(targetColor: PixelColor) {
        progress.snapTo(1f)
        segmentStart = targetColor
        segmentTarget = targetColor
    }

    /** Releases the normalized transition and its ticker. */
    internal fun dispose() {
        progress.dispose()
    }
}

/** Maps total controller progress onto the active segment after its theme delay. */
private fun delayedControlProgress(
    progress: Float,
    delay: Duration,
    duration: Duration,
): Float {
    if (delay == Duration.ZERO) return progress
    if (duration == Duration.ZERO) return if (progress >= 1f) 1f else 0f
    val total = delay + duration
    val delayFraction = when {
        delay.isInfinite() -> 1f
        total.isInfinite() && duration.isInfinite() -> 0f
        total.isInfinite() -> 1f
        else -> (delay.inWholeNanoseconds.toDouble() / total.inWholeNanoseconds.toDouble()).toFloat()
    }.coerceIn(0f, 1f)
    if (progress <= delayFraction) return 0f
    if (delayFraction >= 1f) return 0f
    return ((progress - delayFraction) / (1f - delayFraction)).coerceIn(0f, 1f)
}
