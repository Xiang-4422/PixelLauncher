package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
public data class PixelPoint(
    val x: Int,
    val y: Int,
)

/** 表示 `PixelGeometry` 的颜色或渐变模型，插值和采样结果保持确定性。 */
public data class PixelGradientStop(
    val offset: Float,
    val color: PixelColor,
) {
    init {
        require(offset.isFinite()) { "Gradient stop offset must be finite" }
        require(offset in 0f..1f) { "Gradient stop offset must be in 0f..1f" }
    }
}

/** 表示 `PixelGeometry` 的颜色或渐变模型，插值和采样结果保持确定性。 */
public sealed class PixelGradient {
    /** 保存 `PixelGeometry` 当前的 `stops` 集合；元素顺序和所有权遵守所属类型契约。 */
    public abstract val stops: List<PixelGradientStop>

    /** 定义 `Linear` 在 `PixelGeometry` 中承担的数据或执行职责，并保持公开不变量稳定。 */
    public data class Linear(
        val start: PixelPoint,
        val end: PixelPoint,
        override val stops: List<PixelGradientStop>,
    ) : PixelGradient() {
        internal val sortedStops: List<PixelGradientStop> = normalizeGradientStops(stops)
    }

    /** 定义 `Radial` 在 `PixelGeometry` 中承担的数据或执行职责，并保持公开不变量稳定。 */
    public data class Radial(
        val center: PixelPoint,
        val radius: Int,
        override val stops: List<PixelGradientStop>,
    ) : PixelGradient() {
        internal val sortedStops: List<PixelGradientStop> = normalizeGradientStops(stops)

        init {
            require(radius >= 0) { "Radial gradient radius must be >= 0" }
        }
    }
}

internal fun normalizeGradientStops(stops: List<PixelGradientStop>): List<PixelGradientStop> {
    require(stops.isNotEmpty()) { "Gradient requires at least one stop" }
    return stops.sortedBy { it.offset }
}

/** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
public sealed class PixelPathCommand {
    /** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
    public data class MoveTo(val point: PixelPoint) : PixelPathCommand()
    /** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
    public data class LineTo(val point: PixelPoint) : PixelPathCommand()
    /** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
    public data class QuadraticTo(
        val control: PixelPoint,
        val end: PixelPoint,
    ) : PixelPathCommand()

    /** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
    public data class CubicTo(
        val control1: PixelPoint,
        val control2: PixelPoint,
        val end: PixelPoint,
    ) : PixelPathCommand()

    /** 集中提供 `PixelGeometry` 共享的工厂、常量或无状态辅助入口。 */
    public data object Close : PixelPathCommand()
}

/** 表示 `PixelGeometry` 使用的不可变几何数据，并以逻辑像素参与布局或绘制。 */
public data class PixelPath(
    val commands: List<PixelPathCommand>,
) {
    /** 集中提供 `PixelGeometry` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 按 `rect` 参数创建 `PixelGeometry` 的规范化几何或曲线值。 */
        public fun rect(
            left: Int,
            top: Int,
            width: Int,
            height: Int,
        ): PixelPath {
            if (width <= 0 || height <= 0) return PixelPath(emptyList())
            val right = left + width - 1
            val bottom = top + height - 1
            return PixelPath(
                listOf(
                    PixelPathCommand.MoveTo(PixelPoint(left, top)),
                    PixelPathCommand.LineTo(PixelPoint(right, top)),
                    PixelPathCommand.LineTo(PixelPoint(right, bottom)),
                    PixelPathCommand.LineTo(PixelPoint(left, bottom)),
                    PixelPathCommand.Close,
                ),
            )
        }

        /** 按 `circle` 参数创建 `PixelGeometry` 的规范化几何或曲线值。 */
        public fun circle(
            centerX: Int,
            centerY: Int,
            radius: Int,
        ): PixelPath {
            if (radius < 0) return PixelPath(emptyList())
            val points = mutableListOf<PixelPoint>()
            var x = radius
            var y = 0
            var err = 0
            while (x >= y) {
                points += PixelPoint(centerX + x, centerY + y)
                points += PixelPoint(centerX + y, centerY + x)
                points += PixelPoint(centerX - y, centerY + x)
                points += PixelPoint(centerX - x, centerY + y)
                points += PixelPoint(centerX - x, centerY - y)
                points += PixelPoint(centerX - y, centerY - x)
                points += PixelPoint(centerX + y, centerY - x)
                points += PixelPoint(centerX + x, centerY - y)
                y += 1
                if (err <= 0) {
                    err += 2 * y + 1
                } else {
                    x -= 1
                    err += 2 * (y - x) + 1
                }
            }
            val ordered = points.distinct().sortedWith(compareBy<PixelPoint> {
                kotlin.math.atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble())
            })
            if (ordered.isEmpty()) return PixelPath(emptyList())
            return PixelPath(
                buildList {
                    add(PixelPathCommand.MoveTo(ordered.first()))
                    ordered.drop(1).forEach { add(PixelPathCommand.LineTo(it)) }
                    add(PixelPathCommand.Close)
                },
            )
        }
    }
}
