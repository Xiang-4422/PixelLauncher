package com.purride.pixelshowcase.desktop

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelshowcase.DemoScene
import com.purride.pixelshowcase.scenes.CubeScene
import com.purride.pixelshowcase.scenes.FireScene
import com.purride.pixelshowcase.scenes.LifeScene
import com.purride.pixelshowcase.scenes.PlasmaScene
import com.purride.pixelshowcase.scenes.RainScene
import com.purride.pixelshowcase.scenes.RippleScene
import com.purride.pixelshowcase.scenes.StarfieldScene
import com.purride.pixelshowcase.scenes.TitleScene
import com.purride.pixelshowcase.scenes.TunnelScene
import com.purride.pixelshowcase.scenes.WaveScene
import java.awt.Dimension
import java.awt.Graphics
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * pixel-engine 桌面宿主：同一批 demo 场景源码不改一行跑在 Mac 窗口里。
 *
 * 引擎核心（PixelBuffer / 内置字体 / 场景数学）是纯 Kotlin，这个入口
 * 只补了平台差异的三件事：窗口 blit、鼠标事件、帧循环。
 *
 * 运行：./gradlew :showcase-desktop:run
 * 冒烟：./gradlew :showcase-desktop:run --args="--smoke <输出.png>"
 */
fun main(args: Array<String>) {
    val smokeIndex = args.indexOf("--smoke")
    if (smokeIndex >= 0) {
        val output = args.getOrElse(smokeIndex + 1) { "desktop-smoke.png" }
        smokeRender(File(output))
        return
    }
    SwingUtilities.invokeLater { DesktopShowcaseWindow().isVisible = true }
}

private fun buildScenes(): List<DemoScene> = listOf(
    TitleScene(),
    PlasmaScene(),
    FireScene(),
    RippleScene(),
    TunnelScene(),
    RainScene(),
    CubeScene(),
    WaveScene(),
    LifeScene(),
    StarfieldScene(),
)

/** 无窗冒烟：把每个场景推进 90 帧后的画面拼成一张网格图落盘。 */
private fun smokeRender(output: File) {
    val scenes = buildScenes()
    val columns = 5
    val rows = (scenes.size + columns - 1) / columns
    val sheet = BufferedImage(
        LOGICAL_WIDTH * columns,
        LOGICAL_HEIGHT * rows,
        BufferedImage.TYPE_INT_RGB,
    )
    scenes.forEachIndexed { index, scene ->
        val buffer = PixelBuffer(LOGICAL_WIDTH, LOGICAL_HEIGHT)
        scene.reset(LOGICAL_WIDTH, LOGICAL_HEIGHT)
        var elapsed = 0f
        repeat(90) {
            elapsed += FRAME_SECONDS
            scene.update(FRAME_SECONDS, elapsed)
        }
        buffer.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT, BACKGROUND)
        scene.render(buffer)
        PixelBitmapFont.Default.drawText(buffer, scene.title, 2, 2, CHROME)
        val originX = (index % columns) * LOGICAL_WIDTH
        val originY = (index / columns) * LOGICAL_HEIGHT
        sheet.setRGB(originX, originY, LOGICAL_WIDTH, LOGICAL_HEIGHT, buffer.pixels, 0, LOGICAL_WIDTH)
    }
    ImageIO.write(sheet, "png", output)
    println("冒烟渲染完成：${scenes.size} 个场景 → ${output.absolutePath}")
}

private class DesktopShowcaseWindow : JFrame() {
    private val scenes = buildScenes()
    private var sceneIndex = 0
    private var sceneElapsed = 0f
    private val buffer = PixelBuffer(LOGICAL_WIDTH, LOGICAL_HEIGHT)
    private val image = BufferedImage(LOGICAL_WIDTH, LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB)
    private var lastFrameNanos = System.nanoTime()

    private val canvas = object : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as java.awt.Graphics2D
            // 最近邻放大：像素必须是方的，这是全部意义所在。
            g2.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR,
            )
            g2.drawImage(image, 0, 0, width, height, null)
        }
    }

    init {
        title = titleFor()
        defaultCloseOperation = EXIT_ON_CLOSE
        canvas.preferredSize = Dimension(LOGICAL_WIDTH * SCALE, LOGICAL_HEIGHT * SCALE)
        contentPane = canvas
        pack()
        setLocationRelativeTo(null)
        isResizable = true

        currentScene().reset(LOGICAL_WIDTH, LOGICAL_HEIGHT)
        canvas.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                val step = if (SwingUtilities.isRightMouseButton(event)) -1 else 1
                sceneIndex = ((sceneIndex + step) % scenes.size + scenes.size) % scenes.size
                sceneElapsed = 0f
                currentScene().reset(LOGICAL_WIDTH, LOGICAL_HEIGHT)
                title = titleFor()
            }
        })
        Timer(FRAME_MILLIS) { tick() }.start()
    }

    private fun currentScene(): DemoScene = scenes[sceneIndex]

    private fun titleFor(): String =
        "PIXEL ENGINE DESKTOP — ${currentScene().title} (${sceneIndex + 1}/${scenes.size}, CLICK TO SWITCH)"

    private fun tick() {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.1f)
        lastFrameNanos = now
        sceneElapsed += dt
        val scene = currentScene()
        // 与手机端 DemoDirector 同规则：到时重置当前场景循环演出。
        if (sceneElapsed >= scene.durationSeconds) {
            sceneElapsed = 0f
            scene.reset(LOGICAL_WIDTH, LOGICAL_HEIGHT)
        }
        scene.update(dt, sceneElapsed)
        buffer.fillRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT, BACKGROUND)
        scene.render(buffer)
        PixelBitmapFont.Default.drawText(buffer, scene.title, 2, 2, CHROME)
        image.setRGB(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT, buffer.pixels, 0, LOGICAL_WIDTH)
        canvas.repaint()
    }
}

private const val LOGICAL_WIDTH = 240
private const val LOGICAL_HEIGHT = 150
private const val SCALE = 5
private const val FRAME_MILLIS = 16
private const val FRAME_SECONDS = 0.016f
private val BACKGROUND = PixelColor.fromRgb(10, 14, 26)
private val CHROME = PixelColor.fromRgb(110, 130, 160)
