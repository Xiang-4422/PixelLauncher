package com.purride.pixelshowcase.scenes

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 旋转 3D 线框立方体：透视投影 + Bresenham 直线，一根线一根线画进逻辑像素。
 * 展示点：引擎的画布就是一块裸帧缓冲，传统软件光栅化技巧原封不动可用。
 */
class CubeScene : DemoScene {
    override val title = "WIREFRAME"

    private var width = 0
    private var height = 0
    private var angleX = 0f
    private var angleY = 0f

    private val vertices = arrayOf(
        floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
        floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
        floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
        floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f),
    )
    private val edges = arrayOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 0,
        4 to 5, 5 to 6, 6 to 7, 7 to 4,
        0 to 4, 1 to 5, 2 to 6, 3 to 7,
    )
    private val projected = Array(8) { IntArray(3) }

    override fun reset(width: Int, height: Int) {
        this.width = width
        this.height = height
        angleX = 0.6f
        angleY = 0f
    }

    override fun update(dt: Float, elapsed: Float) {
        angleX += dt * 0.9f
        angleY += dt * 1.3f
    }

    override fun render(buffer: PixelBuffer) {
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) * 0.42f
        val sinX = sin(angleX)
        val cosX = cos(angleX)
        val sinY = sin(angleY)
        val cosY = cos(angleY)
        vertices.forEachIndexed { index, v ->
            // 绕 Y 再绕 X
            val x1 = v[0] * cosY + v[2] * sinY
            val z1 = -v[0] * sinY + v[2] * cosY
            val y2 = v[1] * cosX - z1 * sinX
            val z2 = v[1] * sinX + z1 * cosX
            val depth = z2 + CAMERA_DISTANCE
            projected[index][0] = (cx + x1 / depth * scale).toInt()
            projected[index][1] = (cy + y2 / depth * scale).toInt()
            // 存深度千分位，画边时按远近调亮度。
            projected[index][2] = (depth * 1000).toInt()
        }
        edges.forEach { (from, to) ->
            val nearDepth = minOf(projected[from][2], projected[to][2])
            val color = if (nearDepth < CAMERA_DISTANCE * 1000) EDGE_NEAR else EDGE_FAR
            drawLine(
                buffer,
                projected[from][0], projected[from][1],
                projected[to][0], projected[to][1],
                color,
            )
        }
        // 顶点强调：单像素高亮，不用多像素块。
        projected.forEach { p -> buffer.setPixel(p[0], p[1], VERTEX) }
    }

    /** Bresenham：整数误差步进，逐逻辑像素落点。 */
    private fun drawLine(buffer: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, color: PixelColor) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val stepX = if (x0 < x1) 1 else -1
        val stepY = if (y0 < y1) 1 else -1
        var error = dx + dy
        while (true) {
            buffer.setPixel(x, y, color)
            if (x == x1 && y == y1) break
            val doubled = 2 * error
            if (doubled >= dy) {
                error += dy
                x += stepX
            }
            if (doubled <= dx) {
                error += dx
                y += stepY
            }
        }
    }

    private companion object {
        const val CAMERA_DISTANCE = 3.2f
        val EDGE_NEAR = PixelColor.fromRgb(220, 236, 255)
        val EDGE_FAR = PixelColor.fromRgb(80, 120, 170)
        val VERTEX = PixelColor.fromRgb(255, 255, 255)
    }
}
