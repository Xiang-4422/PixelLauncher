package com.purride.pixeldemo.app

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelTextInputBridge

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
        val textInputBridge = PixelTextInputBridge(
            context = this,
            hostView = hostView,
        )
        hostView.hostBridge = textInputBridge
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
        hostView.setPalette(scene.initialPalette)
        hostView.textRasterizer = scene.initialTextRasterizer
        hostView.themeData = scene.initialThemeData
        hostView.setContent(scene.content)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    hostView,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
                )
                addView(
                    textInputBridge.inputView,
                    FrameLayout.LayoutParams(1, WRAP_CONTENT),
                )
            },
        )
        hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = scene.initialProfile.dotSizePx,
            pixelShape = scene.initialProfile.pixelShape,
        )
    }

    companion object {
        const val EXTRA_SCENE_KIND = "scene_kind"
    }
}
