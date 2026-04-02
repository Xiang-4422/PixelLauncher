package com.purride.pixeldemo.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.createPixelHostSetup

/**
 * 单个 demo scene 的宿主 Activity。
 */
class DemoSceneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sceneKind = DemoSceneKind.valueOf(
            intent.getStringExtra(EXTRA_SCENE_KIND) ?: DemoSceneKind.TEXT.name,
        )
        title = sceneKind.menuLabel

        val hostView = PixelHostView(this)
        val textRasterizers = DemoTextRasterizers(this)

        val scene = DemoScenes.create(
            sceneKind = sceneKind,
            hostView = hostView,
            textRasterizers = textRasterizers,
            applyPreferredProfile = { profile ->
                hostView.profilePreference = PixelHostProfilePreference(
                    dotSizePx = profile.dotSizePx,
                    pixelShape = profile.pixelShape,
                )
            },
        )
        val hostSetup = createPixelHostSetup(
            context = this,
            hostView = hostView,
            config = PixelHostSetupConfig(
                profilePreference = PixelHostProfilePreference(
                    dotSizePx = scene.initialProfile.dotSizePx,
                    pixelShape = scene.initialProfile.pixelShape,
                ),
                palette = scene.initialPalette,
                textRasterizer = scene.initialTextRasterizer,
                themeData = scene.initialThemeData,
                content = scene.content,
            ),
        )
        setContentView(hostSetup.rootView)
    }

    companion object {
        const val EXTRA_SCENE_KIND = "scene_kind"
    }
}
