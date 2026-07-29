package com.purride.pixelshowcase

import android.os.Bundle
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
import com.purride.pixelshowcase.scenes.LifeScene
import com.purride.pixelshowcase.scenes.PlasmaScene
import com.purride.pixelshowcase.scenes.RainScene
import com.purride.pixelshowcase.scenes.StarfieldScene
import com.purride.pixelshowcase.scenes.TitleScene
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hostView = PixelHostView(this)
        setup = createPixelHostSetup(
            context = this,
            hostView = hostView,
            config = PixelHostSetupConfig(
                // 一个逻辑像素 = 8 物理像素：颗粒感是这个 demo 的主角。
                profilePolicy = PixelHostProfilePolicy.AdaptivePixels(dotSizePx = DOT_SIZE_PX),
                content = { DemoCanvas(director) },
            ),
        )
        director = DemoDirector(
            vsync = setup.hostView.tickerProvider,
            onFrame = { setup.hostView.postInvalidateOnAnimation() },
            scenes = listOf(
                TitleScene(),
                PlasmaScene(),
                RainScene(),
                CubeScene(),
                WaveScene(),
                LifeScene(),
                StarfieldScene(),
            ),
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
        director.dispose()
        setup.dispose()
        super.onDestroy()
    }

    private companion object {
        const val DOT_SIZE_PX = 8
    }
}
