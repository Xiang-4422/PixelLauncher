package com.purride.pixelui

public data class PixelPoint(
    val x: Int,
    val y: Int,
)

public sealed class PixelPathCommand {
    public data class MoveTo(val point: PixelPoint) : PixelPathCommand()
    public data class LineTo(val point: PixelPoint) : PixelPathCommand()
    public data object Close : PixelPathCommand()
}

public data class PixelPath(
    val commands: List<PixelPathCommand>,
)
