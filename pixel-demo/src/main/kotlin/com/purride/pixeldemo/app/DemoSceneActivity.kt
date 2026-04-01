package com.purride.pixeldemo.app

import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelcore.ScreenProfileFactory
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelTextInputBridge

/**
 * 单个 demo scene 的宿主 Activity。
 */
class DemoSceneActivity : AppCompatActivity() {

    /**
     * 场景只负责声明偏好的点阵尺寸和像素形状。
     *
     * 真正的逻辑分辨率由宿主根据当前 View 可用尺寸推导，
     * 这样 demo 页面才能稳定吃满全屏。
     */
    private var preferredProfile: ScreenProfile? = null

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
        fun applyPreferredProfile(profile: ScreenProfile) {
            preferredProfile = profile
            if (hostView.width > 0 && hostView.height > 0) {
                hostView.screenProfile = resolveFullscreenProfile(profile, hostView.width, hostView.height)
            }
        }

        val scene = DemoScenes.create(
            sceneKind = sceneKind,
            hostView = hostView,
            textRasterizers = textRasterizers,
            applyPreferredProfile = ::applyPreferredProfile,
        )
        hostView.setPalette(scene.initialPalette)
        hostView.textRasterizer = scene.initialTextRasterizer
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

        hostView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) {
                return@addOnLayoutChangeListener
            }
            val profile = preferredProfile ?: return@addOnLayoutChangeListener
            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)
            hostView.screenProfile = resolveFullscreenProfile(profile, width, height)
        }

        applyPreferredProfile(scene.initialProfile)
    }

    private fun resolveFullscreenProfile(
        preferredProfile: ScreenProfile,
        widthPx: Int,
        heightPx: Int,
    ): ScreenProfile {
        return ScreenProfileFactory.create(
            widthPx = widthPx,
            heightPx = heightPx,
            dotSizePx = preferredProfile.dotSizePx,
            pixelShape = preferredProfile.pixelShape,
        )
    }

    companion object {
        const val EXTRA_SCENE_KIND = "scene_kind"
    }
}
