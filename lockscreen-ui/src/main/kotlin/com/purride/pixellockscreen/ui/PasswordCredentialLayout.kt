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

/** 返回指定逻辑方屏中不会裁切且为系统 IME 预留空间的密码布局。 */
internal fun passwordCredentialLayout(
    logicalWidth: Int = LOCKSCREEN_LOGICAL_WIDTH,
    logicalHeight: Int = LOCKSCREEN_LOGICAL_HEIGHT,
): PasswordCredentialLayout {
    require(logicalWidth >= 48 && logicalHeight >= 64) { "password_logical_viewport_too_small" }
    /** 页面公共横向安全边距。 */
    val horizontalInset = if (logicalWidth >= 120) 8 else 5
    /** 输入区域高度。 */
    val inputHeight = (logicalHeight / 6).coerceIn(16, 24)
    /** 输入区域上边界。 */
    val inputTop = (logicalHeight / 3).coerceIn(28, 48)
    /** 底部动作高度。 */
    val actionHeight = (logicalHeight / 7).coerceIn(12, 20)
    /** 底部动作上边界，始终位于输入区域下方。 */
    val actionTop = (inputTop + inputHeight + 6).coerceAtMost(logicalHeight - actionHeight - 4)
    /** 两个底部动作之间的逻辑间距。 */
    val actionGap = if (logicalWidth >= 100) 8 else 4
    /** 两个底部动作平分安全宽度。 */
    val actionWidth = (logicalWidth - horizontalInset * 2 - actionGap) / 2
    return PasswordCredentialLayout(
        logicalWidth = logicalWidth,
        logicalHeight = logicalHeight,
        promptLeft = 4,
        promptTop = 4,
        promptWidth = logicalWidth - 8,
        promptHeight = 10,
        feedbackLeft = 4,
        feedbackTop = 16,
        feedbackWidth = logicalWidth - 8,
        feedbackHeight = 9,
        inputAction = PasswordActionSpec(
            PasswordCredentialAction.INPUT,
            left = horizontalInset,
            top = inputTop,
            width = logicalWidth - horizontalInset * 2,
            height = inputHeight,
        ),
        imeSwitcherAction = PasswordActionSpec(
            PasswordCredentialAction.IME_SWITCHER,
            left = horizontalInset,
            top = actionTop,
            width = actionWidth,
            height = actionHeight,
        ),
        emergencyAction = PasswordActionSpec(
            PasswordCredentialAction.EMERGENCY,
            left = horizontalInset + actionWidth + actionGap,
            top = actionTop,
            width = actionWidth,
            height = actionHeight,
        ),
    )
}
