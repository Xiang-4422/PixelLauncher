package com.purride.pixelui

import kotlin.time.Duration

/**
 * 使用当前主题 token 和可选 Motion scope 配置一个标准控件驱动器。
 *
 * 该模块内辅助方法集中承载 Switch、Tab 等控件共享的运动解析逻辑；
 * [JvmSynthetic] 防止它成为 Java 调用方可见的承诺 API。
 *
 * @param motion 待配置的 retained 动画值。
 * @param scope 可选统一时钟与运动偏好来源。
 * @param spec 当前状态通道使用的主题运动规格。
 */
@JvmSynthetic
internal fun configureControlMotion(
    motion: PixelControlMotionValue,
    scope: PixelMotionScope?,
    spec: PixelMotionSpec,
) {
    /** 已应用 scope 设置的运行时规格；无 scope 时走即时更新。 */
    val resolved = scope?.let { availableScope -> spec.resolve(availableScope.settings) }
    motion.configure(
        nextVsync = scope?.vsync,
        nextDuration = resolved?.duration ?: Duration.ZERO,
        nextDelay = resolved?.delay ?: Duration.ZERO,
        nextCurve = resolved?.curve ?: spec.curve,
        nextImmediate = resolved?.let { resolvedMotion ->
            resolvedMotion.isImmediate || resolvedMotion.transition == PixelMotionTransitionPreset.None
        } ?: true,
    )
}

/**
 * 把可并存的 Switch/Tab 交互状态压缩为单一反馈目标。
 *
 * @param pressed 指针当前是否按下。
 * @param hovered 指针当前是否悬停。
 * @param focused 控件当前是否获得键盘焦点。
 * @return 标准化到 0 到 1 的视觉反馈强度。
 */
@JvmSynthetic
internal fun controlFeedbackTarget(
    pressed: Boolean,
    hovered: Boolean,
    focused: Boolean,
): Float = when {
    pressed -> 1f
    focused -> 1f
    hovered -> 0.5f
    else -> 0f
}
