package com.purride.pixellockscreen.ui

import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelui.Alignment
import com.purride.pixelui.Container
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.Positioned
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget

/** PIN 认证像素场景的全部非敏感请求。 */
internal data class PinCredentialSceneRequest(
    /** 当前提示、输入长度与系统反馈。 */
    val state: PinCredentialUiState,
    /** 当前主题家族。 */
    val family: ProductThemeFamily,
    /** 当前主题实际明暗。 */
    val brightness: ProductThemeBrightness,
    /** 是否使用横向布局。 */
    val isLandscape: Boolean,
    /** 当前手指按下的公开按键编号。 */
    val pressedKeyId: Int?,
)

/** 构建 PIN 认证的唯一可见 Widget 树。 */
internal fun buildPinCredentialScene(request: PinCredentialSceneRequest): Widget {
    /** 当前请求解析出的共享产品色板。 */
    val palette = ProductThemeCatalog.resolve(request.family, request.brightness)
    /** 当前方向固定布局。 */
    val layout = pinCredentialLayout(request.isLandscape)
    /** 系统反馈使用的状态颜色。 */
    val feedbackColor = when (request.state.feedback) {
        PinCredentialFeedback.ERROR,
        PinCredentialFeedback.LOCKED_OUT,
        -> palette.alert

        PinCredentialFeedback.CHECKING -> palette.secondary
        PinCredentialFeedback.READY -> palette.muted
    }
    /** 所有按键标签 Widget。 */
    val keyLabels = layout.keys.map { key ->
        /** 当前按键是否处于按下高亮。 */
        val pressed = request.pressedKeyId == key.id
        Positioned(
            left = key.left + 1,
            top = key.top + 1,
            width = key.width - 2,
            height = key.height - 2,
            child = Container(
                alignment = Alignment.CENTER,
                child = outlinedLockscreenText(
                    text = key.label,
                    foreground = if (pressed) palette.background else palette.primary,
                    backing = if (pressed) palette.primary else palette.background,
                    fontScale = 1,
                    key = "pin-key-label-${key.id}",
                ),
            ),
            key = "pin-key-position-${key.id}",
        )
    }
    return Stack(
        children = listOf(
            CustomPaint(
                width = layout.logicalWidth,
                height = layout.logicalHeight,
                key = "pin-credential-canvas",
            ) {
                layout.keys.forEach { key ->
                    if (request.pressedKeyId == key.id) {
                        fillRect(key.left, key.top, key.width, key.height, palette.primary)
                    } else {
                        drawRect(key.left, key.top, key.width, key.height, palette.primary, strokeWidth = 1)
                    }
                }
                drawPinIndicator(layout, request.state.inputLength, palette.primary, palette.muted)
                drawRect(
                    layout.emergencyLeft,
                    layout.emergencyTop,
                    layout.emergencyWidth,
                    layout.emergencyHeight,
                    palette.alert,
                    strokeWidth = 1,
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
                        key = "pin-credential-prompt",
                    ),
                ),
                key = "pin-credential-prompt-position",
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
                            key = "pin-credential-feedback",
                        )
                    },
                ),
                key = "pin-credential-feedback-position",
            ),
            Positioned(
                left = layout.emergencyLeft + 1,
                top = layout.emergencyTop + 1,
                width = layout.emergencyWidth - 2,
                height = layout.emergencyHeight - 2,
                child = Container(
                    alignment = Alignment.CENTER,
                    child = outlinedLockscreenText(
                        if (request.isLandscape) request.state.compactEmergencyText else request.state.emergencyText,
                        palette.alert,
                        palette.background,
                        fontScale = 1,
                        key = "pin-credential-emergency",
                    ),
                ),
                key = "pin-credential-emergency-position",
            ),
        ) + keyLabels,
        key = "pin-credential-scene",
    )
}

/** 只按输入长度绘制最多十二个不可还原的 PIN 圆点。 */
private fun com.purride.pixelui.PixelCanvas.drawPinIndicator(
    layout: PinCredentialLayout,
    inputLength: Int,
    activeColor: com.purride.pixelcore.PixelColor,
    overflowColor: com.purride.pixelcore.PixelColor,
) {
    /** 当前视图允许直接显示的最大圆点数量。 */
    val visibleCount = inputLength.coerceAtMost(MAXIMUM_VISIBLE_PIN_DOTS)
    if (visibleCount == 0) {
        return
    }
    /** 所有圆点占用的逻辑宽度。 */
    val contentWidth = visibleCount * PIN_DOT_STEP - (PIN_DOT_STEP - 3)
    /** 居中后的首个圆点中心横坐标。 */
    val firstCenterX = layout.indicatorLeft + (layout.indicatorWidth - contentWidth) / 2 + 1
    /** 圆点中心纵坐标。 */
    val centerY = layout.indicatorTop + layout.indicatorHeight / 2
    repeat(visibleCount) { index ->
        drawCircle(firstCenterX + index * PIN_DOT_STEP, centerY, radius = 1, color = activeColor, filled = true)
    }
    if (inputLength > MAXIMUM_VISIBLE_PIN_DOTS) {
        drawRect(
            layout.indicatorLeft + layout.indicatorWidth - 4,
            centerY - 1,
            width = 3,
            height = 3,
            color = overflowColor,
            strokeWidth = 1,
        )
    }
}

/** 判断下一帧是否包含需要重建的非敏感变化。 */
internal fun shouldSubmitPinCredentialRequest(
    previous: PinCredentialSceneRequest?,
    next: PinCredentialSceneRequest,
): Boolean = previous != next

/** 指示器直接显示的最大圆点数量。 */
private const val MAXIMUM_VISIBLE_PIN_DOTS: Int = 12

/** 相邻 PIN 圆点中心间距。 */
private const val PIN_DOT_STEP: Int = 5
