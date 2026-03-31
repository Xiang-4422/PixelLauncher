package com.purride.pixeldemo.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelui.PixelHostView

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
        val scene = DemoScenes.create(sceneKind, hostView)
        hostView.screenProfile = scene.initialProfile
        hostView.setPalette(scene.initialPalette)
        hostView.setContent(scene.content)
        setContentView(hostView)
    }

    companion object {
        const val EXTRA_SCENE_KIND = "scene_kind"
    }
}
