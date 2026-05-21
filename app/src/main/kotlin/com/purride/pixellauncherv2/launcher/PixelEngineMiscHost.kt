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
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixellauncherv2.ui.screen.DiagnosticsScreen
import com.purride.pixellauncherv2.ui.screen.IdleScreen
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 持有 DIAGNOSTICS 和 IDLE 屏幕的 pixel-engine 宿主。
 *
 * Phase 7 实现：当 [LauncherMode.DIAGNOSTICS] 或 [LauncherMode.IDLE] 激活时显示；
 * 此后 PixelRenderer 所有渲染路径均被 engine host 覆盖（不再执行任何旧渲染）。
 */
internal class PixelEngineMiscHost(
    context: Context,
) {
    // ── Mutable model fields (read by content lambda on every invalidate) ──────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.GREEN_PHOSPHOR
    private var chargeTick: Int = 0
    private var screenProfile: ScreenProfile = ScreenProfile(
        logicalWidth = 1, logicalHeight = 1,
        dotSizePx = 1,
    )

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildContent() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每次 MainActivity 渲染帧时调用。
     *
     * @param state         最新 UI 状态
     * @param theme         当前颜色主题
     * @param screenProfile 当前屏幕分辨率配置
     * @param chargeTick    充电动画帧计数
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
        chargeTick: Int,
    ) {
        val active = state.mode in MISC_MODES
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick
        this.screenProfile = screenProfile

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

    private fun buildContent(): Widget = when (uiState.mode) {
        LauncherMode.DIAGNOSTICS -> DiagnosticsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
        )
        LauncherMode.IDLE -> IdleScreen(
            uiState = uiState,
            theme = theme,
        )
        else -> com.purride.pixelui.Center(
            child = com.purride.pixelui.Text(""),
        )
    }

    companion object {
        val MISC_MODES = setOf(
            LauncherMode.DIAGNOSTICS,
            LauncherMode.IDLE,
        )

        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
