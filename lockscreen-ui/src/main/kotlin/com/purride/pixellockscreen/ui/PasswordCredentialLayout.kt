package com.purride.pixellockscreen.ui

/** 密码页面公开且不携带凭据的交互动作。 */
internal enum class PasswordCredentialAction {
    /** 请求原生输入连接获取焦点。 */
    INPUT,

    /** 打开 Android 输入法选择器。 */
    IME_SWITCHER,

    /** 进入原生紧急操作链。 */
    EMERGENCY,
}

/** 密码页面一个可交互区域的固定逻辑几何。 */
internal data class PasswordActionSpec(
    /** 当前区域对应的公开动作。 */
    val action: PasswordCredentialAction,
    /** 区域逻辑左边界。 */
    val left: Int,
    /** 区域逻辑上边界。 */
    val top: Int,
    /** 区域逻辑宽度。 */
    val width: Int,
    /** 区域逻辑高度。 */
    val height: Int,
) {
    /** 判断逻辑坐标是否位于当前动作区域。 */
    fun contains(logicalX: Int, logicalY: Int): Boolean =
        logicalX in left until left + width && logicalY in top until top + height
}

/** 密码页面在 Pixel Engine 固定逻辑视口中的完整布局。 */
internal data class PasswordCredentialLayout(
    /** 场景逻辑宽度。 */
    val logicalWidth: Int,
    /** 场景逻辑高度。 */
    val logicalHeight: Int,
    /** 主提示左边界。 */
    val promptLeft: Int,
    /** 主提示上边界。 */
    val promptTop: Int,
    /** 主提示宽度。 */
    val promptWidth: Int,
    /** 主提示高度。 */
    val promptHeight: Int,
    /** 反馈文字左边界。 */
    val feedbackLeft: Int,
    /** 反馈文字上边界。 */
    val feedbackTop: Int,
    /** 反馈文字宽度。 */
    val feedbackWidth: Int,
    /** 反馈文字高度。 */
    val feedbackHeight: Int,
    /** 密码掩码和输入焦点区域。 */
    val inputAction: PasswordActionSpec,
    /** 输入法选择器区域。 */
    val imeSwitcherAction: PasswordActionSpec,
    /** 紧急入口区域。 */
    val emergencyAction: PasswordActionSpec,
) {
    /** 返回指定逻辑坐标命中的公开动作。 */
    fun actionAt(logicalX: Int, logicalY: Int, includeImeSwitcher: Boolean): PasswordCredentialAction? {
        /** 当前状态实际允许命中的动作集合。 */
        val actions = if (includeImeSwitcher) {
            listOf(inputAction, imeSwitcherAction, emergencyAction)
        } else {
            listOf(inputAction, emergencyAction)
        }
        return actions.firstOrNull { action -> action.contains(logicalX, logicalY) }?.action
    }
}

/** 返回 Titan 2 方屏中不会裁切且为系统 IME 预留空间的密码布局。 */
internal fun passwordCredentialLayout(): PasswordCredentialLayout = PasswordCredentialLayout(
    logicalWidth = LOCKSCREEN_LOGICAL_WIDTH,
    logicalHeight = LOCKSCREEN_LOGICAL_HEIGHT,
    promptLeft = 4,
    promptTop = 8,
    promptWidth = 136,
    promptHeight = 14,
    feedbackLeft = 4,
    feedbackTop = 26,
    feedbackWidth = 136,
    feedbackHeight = 12,
    inputAction = PasswordActionSpec(
        PasswordCredentialAction.INPUT,
        left = 8,
        top = 48,
        width = 128,
        height = 24,
    ),
    imeSwitcherAction = PasswordActionSpec(
        PasswordCredentialAction.IME_SWITCHER,
        left = 8,
        top = 84,
        width = 60,
        height = 20,
    ),
    emergencyAction = PasswordActionSpec(
        PasswordCredentialAction.EMERGENCY,
        left = 76,
        top = 84,
        width = 60,
        height = 20,
    ),
)
