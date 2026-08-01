package com.purride.pixellockscreen.ui

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.Positioned
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget

/** 密码认证像素场景的全部非敏感请求。 */
internal data class PasswordCredentialSceneRequest(
    /** 当前提示、输入长度、焦点与系统反馈。 */
    val state: PasswordCredentialUiState,
    /** 当前主题家族。 */
    val family: ProductThemeFamily,
    /** 当前主题实际明暗。 */
    val brightness: ProductThemeBrightness,
    /** 是否使用横向布局。 */
    val isLandscape: Boolean,
    /** 当前手指按下的公开动作。 */
    val pressedAction: PasswordCredentialAction?,
)

/** 构建密码认证的唯一可见 Widget 树。 */
internal fun buildPasswordCredentialScene(request: PasswordCredentialSceneRequest): Widget {
    /** 当前请求解析出的共享产品色板。 */
    val palette = ProductThemeCatalog.resolve(request.family, request.brightness)
    /** 当前方向固定布局。 */
    val layout = passwordCredentialLayout(request.isLandscape)
    /** 系统反馈使用的状态颜色。 */
    val feedbackColor = when (request.state.feedback) {
        PasswordCredentialFeedback.ERROR,
        PasswordCredentialFeedback.LOCKED_OUT,
        -> palette.alert

        PasswordCredentialFeedback.CHECKING -> palette.secondary
        PasswordCredentialFeedback.READY -> palette.muted
    }
    /** 输入框是否应使用焦点或按下高亮。 */
    val inputHighlighted = request.state.hasInputFocus ||
        request.pressedAction == PasswordCredentialAction.INPUT
    return Stack(
        children = listOf(
            CustomPaint(
                width = layout.logicalWidth,
                height = layout.logicalHeight,
                key = "password-credential-canvas",
            ) {
                drawActionFrame(
                    action = layout.inputAction,
                    color = if (request.state.isInputEnabled) palette.primary else palette.muted,
                    pressed = inputHighlighted,
                    fillColor = if (request.state.isInputEnabled) palette.primary else palette.muted,
                )
                drawPasswordIndicator(
                    action = layout.inputAction,
                    inputLength = request.state.inputLength,
                    activeColor = if (inputHighlighted) palette.background else palette.primary,
                    overflowColor = if (inputHighlighted) palette.background else palette.muted,
                )
                if (request.state.isImeSwitcherVisible) {
                    drawActionFrame(
                        action = layout.imeSwitcherAction,
                        color = palette.secondary,
                        pressed = request.pressedAction == PasswordCredentialAction.IME_SWITCHER,
                        fillColor = palette.secondary,
                    )
                }
                drawActionFrame(
                    action = layout.emergencyAction,
                    color = palette.alert,
                    pressed = request.pressedAction == PasswordCredentialAction.EMERGENCY,
                    fillColor = palette.alert,
                )
            },
            Positioned(
                left = layout.promptLeft,
                top = layout.promptTop,
                width = layout.promptWidth,
                height = layout.promptHeight,
                child = Container(
                    alignment = Alignment.CENTER,
                    child = outlinedLockscreenText(
                        request.state.promptText,
                        palette.primary,
                        palette.background,
                        fontScale = 1,
                        key = "password-credential-prompt",
                    ),
                ),
                key = "password-credential-prompt-position",
            ),
            Positioned(
                left = layout.feedbackLeft,
                top = layout.feedbackTop,
                width = layout.feedbackWidth,
                height = layout.feedbackHeight,
                child = Container(
                    alignment = Alignment.CENTER,
                    child = if (request.state.feedbackText.isBlank()) {
                        Container(width = 0, height = 0)
                    } else {
                        outlinedLockscreenText(
                            request.state.feedbackText,
                            feedbackColor,
                            palette.background,
                            fontScale = 1,
                            key = "password-credential-feedback",
                        )
                    },
                ),
                key = "password-credential-feedback-position",
            ),
            passwordActionLabel(
                action = layout.inputAction,
                visible = request.state.inputLength == 0,
                text = request.state.inputHintText,
                foreground = if (inputHighlighted) {
                    palette.background
                } else if (request.state.isInputEnabled) {
                    palette.primary
                } else {
                    palette.muted
                },
                backing = if (inputHighlighted) {
                    if (request.state.isInputEnabled) palette.primary else palette.muted
                } else {
                    palette.background
                },
                key = "password-credential-input-hint",
            ),
            passwordActionLabel(
                action = layout.imeSwitcherAction,
                visible = request.state.isImeSwitcherVisible,
                text = if (request.isLandscape) {
                    request.state.compactImeSwitcherText
                } else {
                    request.state.imeSwitcherText
                },
                foreground = if (
                    request.pressedAction == PasswordCredentialAction.IME_SWITCHER
                ) {
                    palette.background
                } else {
                    palette.secondary
                },
                backing = if (
                    request.pressedAction == PasswordCredentialAction.IME_SWITCHER
                ) {
                    palette.secondary
                } else {
                    palette.background
                },
                key = "password-credential-ime-switcher",
            ),
            passwordActionLabel(
                action = layout.emergencyAction,
                visible = true,
                text = if (request.isLandscape) {
                    request.state.compactEmergencyText
                } else {
                    request.state.emergencyText
                },
                foreground = if (request.pressedAction == PasswordCredentialAction.EMERGENCY) {
                    palette.background
                } else {
                    palette.alert
                },
                backing = if (request.pressedAction == PasswordCredentialAction.EMERGENCY) {
                    palette.alert
                } else {
                    palette.background
                },
                key = "password-credential-emergency",
            ),
        ),
        key = "password-credential-scene",
    )
}

/** 绘制一个密码页面动作的边框或按下底色。 */
private fun com.purride.pixelui.PixelCanvas.drawActionFrame(
    action: PasswordActionSpec,
    color: PixelColor,
    pressed: Boolean,
    fillColor: PixelColor,
) {
    if (pressed) {
        fillRect(action.left, action.top, action.width, action.height, fillColor)
    } else {
        drawRect(action.left, action.top, action.width, action.height, color, strokeWidth = 1)
    }
}

/** 只按输入长度绘制最多十二个不可还原的密码圆点。 */
private fun com.purride.pixelui.PixelCanvas.drawPasswordIndicator(
    action: PasswordActionSpec,
    inputLength: Int,
    activeColor: PixelColor,
    overflowColor: PixelColor,
) {
    /** 当前视图允许直接显示的最大圆点数量。 */
    val visibleCount = inputLength.coerceAtMost(MAXIMUM_VISIBLE_PASSWORD_DOTS)
    if (visibleCount == 0) {
        return
    }
    /** 所有圆点占用的逻辑宽度。 */
    val contentWidth = visibleCount * PASSWORD_DOT_STEP - (PASSWORD_DOT_STEP - 3)
    /** 居中后的首个圆点中心横坐标。 */
    val firstCenterX = action.left + (action.width - contentWidth) / 2 + 1
    /** 圆点中心纵坐标。 */
    val centerY = action.top + action.height / 2
    repeat(visibleCount) { index ->
        drawCircle(
            firstCenterX + index * PASSWORD_DOT_STEP,
            centerY,
            radius = 1,
            color = activeColor,
            filled = true,
        )
    }
    if (inputLength > MAXIMUM_VISIBLE_PASSWORD_DOTS) {
        drawRect(
            action.left + action.width - 5,
            centerY - 1,
            width = 3,
            height = 3,
            color = overflowColor,
            strokeWidth = 1,
        )
    }
}

/** 构建一个带固定动作边界的可选标签。 */
private fun passwordActionLabel(
    action: PasswordActionSpec,
    visible: Boolean,
    text: String,
    foreground: PixelColor,
    backing: PixelColor,
    key: String,
): Widget = Positioned(
    left = action.left + 1,
    top = action.top + 1,
    width = action.width - 2,
    height = action.height - 2,
    child = Container(
        alignment = Alignment.CENTER,
        child = if (visible) {
            outlinedLockscreenText(
                text,
                foreground,
                backing,
                fontScale = 1,
                key = "$key-label",
            )
        } else {
            Container(width = 0, height = 0)
        },
    ),
    key = "$key-position",
)

/** 判断下一帧是否包含需要重建的非敏感变化。 */
internal fun shouldSubmitPasswordCredentialRequest(
    previous: PasswordCredentialSceneRequest?,
    next: PasswordCredentialSceneRequest,
): Boolean = previous != next

/** 指示器直接显示的最大圆点数量。 */
private const val MAXIMUM_VISIBLE_PASSWORD_DOTS: Int = 12

/** 相邻密码圆点中心间距。 */
private const val PASSWORD_DOT_STEP: Int = 5
