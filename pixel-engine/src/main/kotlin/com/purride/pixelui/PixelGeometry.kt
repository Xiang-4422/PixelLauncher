package com.purride.pixelui

import com.purride.pixelcore.PixelColor

public data class PixelPoint(
    val x: Int,
    val y: Int,
)

public data class PixelGradientStop(
    val offset: Float,
    val color: PixelColor,
) {
    init {
        require(offset.isFinite()) { "Gradient stop offset must be finite" }
        require(offset in 0f..1f) { "Gradient stop offset must be in 0f..1f" }
    }
}

public sealed class PixelGradient {
    public abstract val stops: List<PixelGradientStop>

    public data class Linear(
        val start: PixelPoint,
        val end: PixelPoint,
        override val stops: List<PixelGradientStop>,
    ) : PixelGradient() {
        internal val sortedStops: List<PixelGradientStop> = normalizeGradientStops(stops)
    }

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

public sealed class PixelPathCommand {
    public data class MoveTo(val point: PixelPoint) : PixelPathCommand()
    public data class LineTo(val point: PixelPoint) : PixelPathCommand()
    public data object Close : PixelPathCommand()
}

public data class PixelPath(
    val commands: List<PixelPathCommand>,
) {
    public companion object {
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
