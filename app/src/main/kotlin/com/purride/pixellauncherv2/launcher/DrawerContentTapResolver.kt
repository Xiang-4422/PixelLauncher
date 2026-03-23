package com.purride.pixellauncherv2.launcher

enum class DrawerContentTapAction {
    NONE,
    EXIT_SEARCH,
    SELECT_INDEX,
    LAUNCH_SELECTED,
}

data class DrawerContentTapDecision(
    val action: DrawerContentTapAction,
    val targetIndex: Int? = null,
)

object DrawerContentTapResolver {

    fun resolve(
        state: LauncherState,
        tappedAppIndex: Int?,
    ): DrawerContentTapDecision {
        val resultsVisible = true
        if (resultsVisible && tappedAppIndex != null) {
            return DrawerContentTapDecision(
                action = DrawerContentTapAction.LAUNCH_SELECTED,
                targetIndex = tappedAppIndex,
            )
        }
        return if (state.isDrawerSearchFocused || state.drawerQuery.isNotBlank()) {
            DrawerContentTapDecision(action = DrawerContentTapAction.EXIT_SEARCH)
        } else {
            DrawerContentTapDecision(action = DrawerContentTapAction.NONE)
        }
    }
}
