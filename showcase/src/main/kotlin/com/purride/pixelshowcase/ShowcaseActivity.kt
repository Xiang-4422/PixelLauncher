package com.purride.pixelshowcase

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.PixelHostProfilePolicy
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
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

/**
 * Pixel Engine 演示：一个只依赖 :pixel-engine 的独立应用。
 *
 * 全部装配就在眼前这几行——引擎给一块逻辑像素画布（AdaptivePixels 决定颗粒度）
 * 和一个帧循环，七个场景全是纯数学。点按屏幕切换场景，也会自动轮播。
 */
class ShowcaseActivity : AppCompatActivity() {

    private lateinit var setup: PixelHostSetup
    private lateinit var director: DemoDirector
    private lateinit var appHost: ShowcaseAppHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hostView = PixelHostView(this)
        setup = createPixelHostSetup(
            context = this,
            hostView = hostView,
            config = PixelHostSetupConfig(
                // 一个逻辑像素 = 8 物理像素：颗粒感是这个 demo 的主角。
                profilePolicy = PixelHostProfilePolicy.AdaptivePixels(dotSizePx = DOT_SIZE_PX),
                content = { appHost.buildRoot() },
            ),
        )
        director = DemoDirector(
            vsync = setup.hostView.tickerProvider,
            onFrame = { setup.hostView.postInvalidateOnAnimation() },
            scenes = listOf(
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
            ),
        )
        director.onRecordingFinished = { width, height, frames -> saveRecording(width, height, frames) }
        appHost = ShowcaseAppHost(hostView = setup.hostView, director = director)
        // 首页不跑演示帧循环；进入 DEMOS 页时由 appHost 恢复。
        director.pause()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!appHost.handleBack()) {
                        finish()
                    }
                }
            },
        )
        setContentView(setup.rootView)
        // 沉浸式：demo 的画布就是全部，系统栏只会打断颗粒感。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, setup.rootView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        appHost.dispose()
        director.dispose()
        setup.dispose()
        super.onDestroy()
    }

    /** 录制完成：后台线程编码 GIF 并落到相册，回主线程报结果。 */
    private fun saveRecording(width: Int, height: Int, frames: List<IntArray>) {
        Thread {
            val message = try {
                val bytes = GifEncoder.encode(width, height, frames, delayCentis = GIF_DELAY_CENTIS)
                val name = "pixel-demo-${System.currentTimeMillis()}.gif"
                writeGif(bytes, name)
                "GIF SAVED: $name"
            } catch (error: Exception) {
                "GIF SAVE FAILED: ${error.message}"
            }
            runOnUiThread {
                if (!isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun writeGif(bytes: ByteArray, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/PixelShowcase",
                )
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore insert 返回 null")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法打开输出流")
        } else {
            // Q 之前写公共相册需要存储权限：降级到应用外部私有 Pictures 目录。
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: throw IllegalStateException("外部存储不可用")
            File(dir, name).writeBytes(bytes)
        }
    }

    private companion object {
        const val DOT_SIZE_PX = 8

        /** GIF 帧间隔 8/100 秒 ≈ 12.5fps，与抽帧节奏一致。 */
        const val GIF_DELAY_CENTIS = 8
    }
}
