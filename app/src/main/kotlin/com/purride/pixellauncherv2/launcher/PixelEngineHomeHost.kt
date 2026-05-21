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
import com.purride.pixellauncherv2.ui.screen.HomeScreen
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 持有 HOME 屏幕的 pixel-engine 宿主。
 *
 * Phase 4 实现：当 [LauncherMode.HOME] 激活时显示，替换旧 PixelFrameView 上的 HOME 渲染；
 * 其余模式隐藏（View.GONE），旧渲染（SMS / IDLE / DIAGNOSTICS）正常工作。
 *
 * Phase 8（LauncherRoot 整合）后，此类会被合并进统一的 LauncherRoot HostSetup。
 */
internal class PixelEngineHomeHost(
    context: Context,
    private val callbacks: Callbacks,
) {
    // ── Mutable model fields (read by content lambda on every invalidate) ──────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.GREEN_PHOSPHOR
    private var chargeTick: Int = 0

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildHome() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每次 MainActivity 渲染帧时调用。
     *
     * @param state       最新 UI 状态
     * @param theme       当前颜色主题
     * @param screenProfile 当前屏幕分辨率配置
     * @param chargeTick  充电动画帧计数（来自 LauncherAnimationState.headerChargeTick）
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
        chargeTick: Int,
    ) {
        val active = state.mode == LauncherMode.HOME
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick

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

    private fun buildHome(): com.purride.pixelui.Widget =
        HomeScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            onOpenContacts = callbacks.onOpenContacts,
            onOpenSms = callbacks.onOpenSms,
        )

    // ─────────────────────────────────────────────────────────────────────────

    data class Callbacks(
        /** 用户点击 CONTACT 按钮 → 打开通讯录 */
        val onOpenContacts: () -> Unit,
        /** 用户点击 SMS 按钮 → 进入短信模块 */
        val onOpenSms: () -> Unit,
    )

    companion object {
        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
