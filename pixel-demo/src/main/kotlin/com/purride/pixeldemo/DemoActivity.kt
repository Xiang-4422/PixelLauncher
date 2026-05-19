package com.purride.pixeldemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixeldemo.app.DemoTextRasterizers
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.menu.DemoMenuScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoNavigator
import com.purride.pixeldemo.scaffold.DemoScaffold

class DemoActivity : AppCompatActivity() {

    private lateinit var hostView: PixelHostView
    private lateinit var nav: NavigatorImpl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setup = createPixelHostSetup(context = this)
        hostView = setup.hostView

        val rasterizers = DemoTextRasterizers(this)
        nav = NavigatorImpl()
        val env = DemoEnv(
            hostView = hostView,
            rasterizers = rasterizers,
            applyPreferredProfile = ::applyProfile,
            navigator = nav,
        )
        nav.env = env

        val savedId = savedInstanceState?.getString(KEY_SCENE_ID)
        val startScene = savedId?.let { DemoCatalog.findById(it) } ?: DemoMenuScene
        nav.push(startScene)

        setContentView(setup.rootView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCENE_ID, nav.currentScene.id)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (nav.currentScene !== DemoMenuScene) {
            nav.popToMenu()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun applyProfile(profile: ScreenProfile) {
        hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = profile.dotSizePx,
            pixelShape = profile.pixelShape,
        )
    }

    private inner class NavigatorImpl : DemoNavigator {
        lateinit var env: DemoEnv
        var currentScene: DemoScene = DemoMenuScene

        override fun push(scene: DemoScene) {
            currentScene = scene
            scene.initialProfile?.let { applyProfile(it) }
            scene.initialPalette?.let { hostView.setPalette(it) }
            scene.initialTheme?.let { hostView.themeData = it }
            scene.pagerGesturePolicy?.let { hostView.pagerGesturePolicy = it }
            hostView.setContent {
                DemoScaffold(
                    title = scene.title,
                    description = scene.description,
                    body = scene.build(env),
                )
            }
        }

        override fun popToMenu() = push(DemoMenuScene)
    }

    companion object {
        private const val KEY_SCENE_ID = "scene_id"
    }
}
