package com.purride.pixellauncherv2.launcher

/**
 * Shell 与跨页 Flow 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载顶层路由（mode/returnMode）与全局状态栏瞬态的写入，以及跨切片的页面打开/关闭流程。
 * 对外入口仍是 [LauncherStateTransitions] facade；行为与拆分前逐字节等价。
 */
object LauncherShellTransitions {

    /** 切回 Home 模式，不改动其他派生字段。 */
    fun showHome(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.HOME,
            isDrawerSearchFocused = false,
            isAppActionMenuVisible = false,
        )
    }

    /**
     * 打开设置页，并记录关闭设置后应该回到哪个页面。
     */
    fun showSettings(state: LauncherState, visibleRows: Int): LauncherState {
        val returnMode = when (state.mode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE,
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            LauncherMode.DIALER,
            LauncherMode.CONTACT_DETAIL,
            LauncherMode.CONTACT_EDITOR -> state.mode

            LauncherMode.SETTINGS,
            LauncherMode.APP_MANAGEMENT,
            LauncherMode.DATA_HEALTH,
            LauncherMode.NOTIFICATION_SETTINGS,
            LauncherMode.LOADING_PREVIEW,
            LauncherMode.DIAGNOSTICS,
            LauncherMode.SNAKE -> state.returnMode
        }
        val maxIndex = SettingsMenuModel.rows(state).lastIndex.coerceAtLeast(0)
        return LauncherSettingsTransitions.syncSettingsWindow(
            state = state.copy(
                mode = LauncherMode.SETTINGS,
                returnMode = returnMode,
                settingsSelectedIndex = state.settingsSelectedIndex.coerceIn(0, maxIndex),
                isDrawerSearchFocused = false,
                isAppActionMenuVisible = false,
            ),
            visibleRows = visibleRows,
        )
    }

    /**
     * 关闭设置页。
     *
     * 如果记录的返回模式已经失效，会回退到最后一个合法的 pager 页面。
     */
    fun hideSettings(state: LauncherState): LauncherState {
        val fallbackMode = when (state.returnMode) {
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.IDLE,
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_THREAD_DETAIL,
            LauncherMode.DIALER,
            LauncherMode.CONTACT_DETAIL,
            LauncherMode.CONTACT_EDITOR -> state.returnMode

            LauncherMode.SETTINGS,
            LauncherMode.APP_MANAGEMENT,
            LauncherMode.DATA_HEALTH,
            LauncherMode.NOTIFICATION_SETTINGS,
            LauncherMode.LOADING_PREVIEW,
            LauncherMode.DIAGNOSTICS,
            LauncherMode.SNAKE -> LauncherMode.HOME
        }
        return state.copy(
            mode = fallbackMode,
            returnMode = fallbackMode,
            isAppActionMenuVisible = false,
        )
    }

    /** 从设置页进入贪吃蛇。 */
    fun showSnake(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SNAKE)
    }

    /** 关闭贪吃蛇，返回设置页。 */
    fun hideSnake(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    /** 从设置页进入轻量 diagnostics 页面。 */
    fun showDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.DIAGNOSTICS)
    }

    /** 关闭 diagnostics，并返回设置页。 */
    fun hideDiagnostics(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    /** 从设置页进入数据健康页。 */
    fun showDataHealth(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.DATA_HEALTH)
    }

    /** 关闭数据健康页，并返回设置页。 */
    fun hideDataHealth(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    /** 从设置页进入通知摘要设置页。 */
    fun showNotificationSettings(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.NOTIFICATION_SETTINGS)
    }

    /** 关闭通知摘要设置页，并返回设置页。 */
    fun hideNotificationSettings(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    /** 从设置页进入加载动画预览页。 */
    fun showLoadingPreview(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.LOADING_PREVIEW)
    }

    /** 关闭加载动画预览页，并返回设置页。 */
    fun hideLoadingPreview(state: LauncherState): LauncherState {
        return state.copy(mode = LauncherMode.SETTINGS)
    }

    /**
     * 进入 Idle。
     *
     * 只有在 Home / Drawer 中且功能开关开启时，才允许进入待机页。
     */
    fun showIdle(state: LauncherState): LauncherState {
        if (!state.isIdlePageEnabled) {
            return state
        }
        if (state.mode != LauncherMode.HOME && state.mode != LauncherMode.APP_DRAWER) {
            return state
        }
        return state.copy(
            mode = LauncherMode.IDLE,
            returnMode = state.mode,
        )
    }

    /** 从 Idle 返回到进入前的页面模式。 */
    fun hideIdle(state: LauncherState): LauncherState {
        return state.copy(mode = state.returnMode)
    }

    /** 写入全局状态栏瞬态消息，并清空互斥的 action 原子组。 */
    fun updateStatusBarMessage(
        state: LauncherState,
        message: String,
    ): LauncherState {
        return state.copy(
            statusBarMessageText = message.trim(),
            statusBarActionLeadingText = "",
            statusBarActionLabel = "",
            isStatusBarActionDanger = false,
        )
    }

    /** 写入全局状态栏 action 原子组，并清空互斥的瞬态消息。 */
    fun updateStatusBarAction(
        state: LauncherState,
        leadingText: String,
        actionLabel: String,
        isDanger: Boolean,
    ): LauncherState {
        return state.copy(
            statusBarMessageText = "",
            statusBarActionLeadingText = leadingText.trim(),
            statusBarActionLabel = actionLabel.trim(),
            isStatusBarActionDanger = isDanger,
        )
    }
}
