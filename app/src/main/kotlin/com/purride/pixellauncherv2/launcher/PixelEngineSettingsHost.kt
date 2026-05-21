package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixellauncherv2.ui.screen.SettingsScreen
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 持有 SETTINGS 屏幕的 pixel-engine 宿主。
 *
 * Phase 3 实现：当 [LauncherMode.SETTINGS] 激活时显示，替换旧 PixelFrameView 上的 SETTINGS 渲染；
 * 其余模式隐藏（View.GONE），旧渲染正常工作。
 *
 * Phase 5（LauncherRoot 整合）后，此类会被合并进统一的 LauncherRoot HostSetup。
 */
internal class PixelEngineSettingsHost(
    context: Context,
    private val callbacks: Callbacks,
) {
    // ── Mutable model fields (read by content lambda on every invalidate) ──────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.GREEN_PHOSPHOR

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildSettings() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每次 MainActivity 渲染帧时调用。
     *
     * @param state         最新 UI 状态
     * @param theme         当前颜色主题
     * @param screenProfile 当前屏幕分辨率配置（用于 profilePreference）
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
    ) {
        val active = state.mode == LauncherMode.SETTINGS
        uiState = state
        this.theme = theme

        rootView.visibility = if (active) View.VISIBLE else View.GONE

        if (!active) return

        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.backgroundColor = theme.backgroundColor
        setup.hostView.pixelGridColor  = theme.pixelGridColor
        setup.hostView.invalidate()
    }

    private fun buildSettings(): com.purride.pixelui.Widget =
        SettingsScreen(
            uiState = uiState,
            theme = theme,
            onItemAction = callbacks.onItemAction,
        )

    // ─────────────────────────────────────────────────────────────────────────

    data class Callbacks(
        /** 用户点击设置行：(item, direction +1/-1) → 更新 ViewModel */
        val onItemAction: (SettingsMenuItem, Int) -> Unit,
    )

    companion object {
        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
