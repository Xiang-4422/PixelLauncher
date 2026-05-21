package com.purride.pixeldemo

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.purride.pixelcore.PixelColorMode
import com.purride.pixelcore.PixelPalette
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelThemeData
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelHostBridge
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSystemAction
import com.purride.pixelui.PixelTextInputRequest
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixeldemo.app.DemoTextRasterizers
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.home.DemoHomeScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoNavigator
import com.purride.pixeldemo.scaffold.DemoScaffold
import com.purride.pixeldemo.settings.DemoAppSettings
import com.purride.pixeldemo.settings.DemoColorTheme

class DemoActivity : AppCompatActivity() {

    private lateinit var hostView: PixelHostView
    private lateinit var rasterizers: DemoTextRasterizers
    private lateinit var nav: NavigatorImpl
    private var currentSettings = DemoAppSettings()

    private val backCallback = object : OnBackPressedCallback(enabled = false) {
        override fun handleOnBackPressed() {
            nav.pop()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)

        rasterizers = DemoTextRasterizers(this)
        val initSettings = DemoAppSettings()
        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                profilePreference = PixelHostProfilePreference(
                    dotSizePx = initSettings.dotSizePx,
                    pixelShape = initSettings.pixelShape,
                ),
                textRasterizer = rasterizers.getRasterizer(initSettings.fontSizePx, initSettings.fontStyle),
            ),
        )
        hostView = setup.hostView
        applySettingsToView(initSettings)

        val originalBridge = hostView.hostBridge
        hostView.hostBridge = object : PixelHostBridge {
            override fun showTextInput(request: PixelTextInputRequest) {
                originalBridge?.showTextInput(request)
            }
            override fun hideTextInput() {
                originalBridge?.hideTextInput()
            }
            override fun performHapticFeedback(type: PixelHapticType) {
                val constant = when (type) {
                    PixelHapticType.TAP -> HapticFeedbackConstants.VIRTUAL_KEY
                    PixelHapticType.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
                }
                hostView.performHapticFeedback(constant)
                originalBridge?.performHapticFeedback(type)
            }
            override fun requestFrame() {
                originalBridge?.requestFrame()
            }
            override fun dispatchSystemAction(action: PixelSystemAction) {
                val payload = action.payload?.let { " payload=$it" }.orEmpty()
                Toast.makeText(
                    this@DemoActivity,
                    "SystemAction: ${action.type}$payload",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        nav = NavigatorImpl()
        val env = DemoEnv(
            hostView = hostView,
            rasterizers = rasterizers,
            applyPreferredProfile = ::applyProfile,
            navigator = nav,
            currentSettings = currentSettings,
            applySettings = ::applySettings,
        )
        nav.env = env

        val savedId = savedInstanceState?.getString(KEY_SCENE_ID)
        val startScene = savedId?.let { DemoCatalog.findById(it) } ?: DemoHomeScene
        nav.push(startScene)

        setContentView(setup.rootView)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCENE_ID, nav.currentScene.id)
    }

    private fun applyProfile(pref: PixelHostProfilePreference) {
        hostView.profilePreference = pref
    }

    private fun applySettings(settings: DemoAppSettings) {
        currentSettings = settings
        nav.env.currentSettings = settings
        // applySettingsToView 只改 hostView 的属性，但 HostRootWidget 是在 setContent
        // 调用时捕获 themeData 的，直接改属性对已渲染的树无效。
        // 必须重新调用 setContent（通过 refreshCurrentScene）来让新的 themeData 生效。
        nav.refreshCurrentScene()
    }

    private fun applySettingsToView(settings: DemoAppSettings) {
        hostView.colorMode = settings.colorMode
        if (settings.colorMode == PixelColorMode.Mono) {
            hostView.setPalette(PixelPalette.fromTheme(settings.monoTheme))
            hostView.themeData = null
        } else {
            val tokens = when (settings.colorTheme) {
                DemoColorTheme.DARK -> PixelThemeTokens.Dark
                DemoColorTheme.LIGHT -> PixelThemeTokens.Light
                DemoColorTheme.OCEAN -> PixelThemeTokens.Ocean
                DemoColorTheme.AMBER -> PixelThemeTokens.Amber
            }
            hostView.themeData = PixelThemeData(tokens = tokens)
        }
        hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = settings.dotSizePx,
            pixelShape = settings.pixelShape,
        )
        hostView.setPixelGapEnabled(settings.pixelGapEnabled)
        hostView.textRasterizer = rasterizers.getRasterizer(settings.fontSizePx, settings.fontStyle)
    }

    private inner class NavigatorImpl : DemoNavigator {
        lateinit var env: DemoEnv
        private val stack = ArrayDeque<DemoScene>()

        val currentScene: DemoScene get() = stack.lastOrNull() ?: DemoHomeScene

        override fun push(scene: DemoScene) {
            stack.addLast(scene)
            renderScene(scene)
            updateBackCallback()
        }

        override fun pop(): Boolean {
            if (stack.size <= 1) return false
            stack.removeLast()
            renderScene(stack.last())
            updateBackCallback()
            return true
        }

        override fun popToMenu() {
            stack.clear()
            stack.addLast(DemoHomeScene)
            renderScene(DemoHomeScene)
            updateBackCallback()
        }

        fun refreshCurrentScene() {
            stack.lastOrNull()?.let { renderScene(it) }
        }

        private fun renderScene(scene: DemoScene) {
            // 先用全局设置铺底
            applySettingsToView(currentSettings)

            // scene 级别覆盖（colorMode override 的场景需要单独处理主题）
            scene.colorMode?.let { hostView.colorMode = it }
            scene.initialProfile?.let { applyProfile(it) }
            scene.initialPalette?.let { hostView.setPalette(it) }
            scene.initialTheme?.let { hostView.themeData = it }
            scene.pagerGesturePolicy?.let { hostView.pagerGesturePolicy = it }

            if (scene.isFullScreen) {
                hostView.setContent { scene.build(env) }
            } else {
                hostView.setContent {
                    DemoScaffold(
                        title = scene.title,
                        description = scene.description,
                        body = scene.build(env),
                    )
                }
            }
        }

        private fun updateBackCallback() {
            backCallback.isEnabled = stack.size > 1
        }
    }

    companion object {
        private const val KEY_SCENE_ID = "scene_id"
    }
}
