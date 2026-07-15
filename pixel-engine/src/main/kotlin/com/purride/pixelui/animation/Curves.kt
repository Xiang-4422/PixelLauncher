package com.purride.pixelui.animation

import kotlin.math.floor

/** 定义 `Curve` 的调用契约，使 `Curves` 实现可以在不泄漏具体类型的情况下替换。 */
public fun interface Curve {
    /** 依据 `Curves` 的公开契约执行 `transform`，并返回或提交经过边界校验的结果。 */
    public fun transform(t: Float): Float
}

/** 集中提供 `Curves` 共享的工厂、常量或无状态辅助入口。 */
public object Curves {
    /** 提供 `Curves` 的 `Linear` 稳定默认值或常量。 */
    public val Linear: Curve = Curve { it }

    /** 提供 `Curves` 的 `EaseIn` 稳定默认值或常量。 */
    public val EaseIn: Curve = Curve { t -> t * t }

    /** 提供 `Curves` 的 `EaseOut` 稳定默认值或常量。 */
    public val EaseOut: Curve = Curve { t -> 1f - (1f - t) * (1f - t) }

    /** 提供 `Curves` 的 `EaseInOut` 稳定默认值或常量。 */
    public val EaseInOut: Curve = Curve { t ->
        if (t < 0.5f) 2f * t * t
        else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
    }

    /** 创建 `Step` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
    public fun Step(n: Int): Curve = Curve { t ->
        if (t >= 1f) 1f else floor(t * n) / n
    }
}

/** 定义 `Curves` 的确定性插值过程；相同进度和端点必须得到相同结果。 */
public class Interval(
    /** 记录 `Curves` 的 `begin` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val begin: Float,
    /** 记录 `Curves` 的 `end` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val end: Float,
    /** 提供 `Curves` 当前管理的 `child` 内容。 */
    public val child: Curve = Curves.Linear,
) : Curve {
    override fun transform(t: Float): Float = when {
        t <= begin -> 0f
        t >= end -> 1f
        else -> child.transform((t - begin) / (end - begin))
    }
}
