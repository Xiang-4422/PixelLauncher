package com.purride.pixellockscreen.ui

import com.purride.pixeldesign.ProductPalette
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

/** 图案认证像素场景的全部非敏感请求。 */
internal data class PatternCredentialSceneRequest(
    /** 当前提示和系统反馈。 */
    val state: PatternCredentialUiState,
    /** 当前主题家族。 */
    val family: ProductThemeFamily,
    /** 当前主题实际明暗。 */
    val brightness: ProductThemeBrightness,
    /** 是否使用横向布局。 */
    val isLandscape: Boolean,
)

/** 构建图案认证的唯一可见 Widget 树。 */
internal fun buildPatternCredentialScene(
    request: PatternCredentialSceneRequest,
    path: PatternVisualPath,
): Widget {
    /** 当前请求解析出的共享产品色板。 */
    val palette = ProductThemeCatalog.resolve(request.family, request.brightness)
    /** 当前方向的固定逻辑布局。 */
    val layout = patternCredentialLayout(request.isLandscape)
    /** 反馈文字使用的状态颜色。 */
    val feedbackColor = when (request.state.feedback) {
        PatternCredentialFeedback.ERROR,
        PatternCredentialFeedback.LOCKED_OUT,
        -> palette.alert

        PatternCredentialFeedback.CHECKING -> palette.secondary
        PatternCredentialFeedback.READY -> palette.muted
    }
    return Stack(
        children = listOf(
            CustomPaint(
                width = layout.logicalWidth,
                height = layout.logicalHeight,
                key = "pattern-credential-canvas",
            ) {
                drawPatternPath(layout, path, palette)
                drawPatternNodes(layout, path, palette)
                drawRect(
                    left = layout.emergencyLeft,
                    top = layout.emergencyTop,
                    width = layout.emergencyWidth,
                    height = layout.emergencyHeight,
                    color = palette.alert,
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
                        text = request.state.promptText,
                        foreground = palette.primary,
                        backing = palette.background,
                        fontScale = 1,
                        key = "pattern-credential-prompt",
                    ),
                ),
                key = "pattern-credential-prompt-position",
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
                            text = request.state.feedbackText,
                            foreground = feedbackColor,
                            backing = palette.background,
                            fontScale = 1,
                            key = "pattern-credential-feedback",
                        )
                    },
                ),
                key = "pattern-credential-feedback-position",
            ),
            Positioned(
                left = layout.emergencyLeft + 1,
                top = layout.emergencyTop + 1,
                width = layout.emergencyWidth - 2,
                height = layout.emergencyHeight - 2,
                child = Container(
                    alignment = Alignment.CENTER,
                    child = outlinedLockscreenText(
                        text = if (request.isLandscape) {
                            request.state.compactEmergencyText
                        } else {
                            request.state.emergencyText
                        },
                        foreground = palette.alert,
                        backing = palette.background,
                        fontScale = 1,
                        key = "pattern-credential-emergency",
                    ),
                ),
                key = "pattern-credential-emergency-position",
            ),
        ),
        key = "pattern-credential-scene",
    )
}

/** 绘制当前按下序列中的有序连接线。 */
private fun com.purride.pixelui.PixelCanvas.drawPatternPath(
    layout: PatternCredentialLayout,
    path: PatternVisualPath,
    palette: ProductPalette,
) {
    if (path.size < 2) {
        return
    }
    for (index in 1 until path.size) {
        /** 线段起点格子。 */
        val previousCell = path.cellAt(index - 1)
        /** 线段终点格子。 */
        val currentCell = path.cellAt(index)
        drawLine(
            startX = layout.centerX(previousCell),
            startY = layout.centerY(previousCell),
            endX = layout.centerX(currentCell),
            endY = layout.centerY(currentCell),
            color = palette.primary,
            strokeWidth = 2,
        )
    }
}

/** 绘制九个像素圆点及当前路径的实心选中态。 */
private fun com.purride.pixelui.PixelCanvas.drawPatternNodes(
    layout: PatternCredentialLayout,
    path: PatternVisualPath,
    palette: ProductPalette,
) {
    /** 当前路径命中掩码，仅存活于一次绘制回调。 */
    val selected = BooleanArray(9)
    repeat(path.size) { index -> selected[path.cellAt(index)] = true }
    repeat(9) { cellId ->
        /** 当前圆心横坐标。 */
        val centerX = layout.centerX(cellId)
        /** 当前圆心纵坐标。 */
        val centerY = layout.centerY(cellId)
        drawCircle(centerX, centerY, radius = 6, color = palette.background, filled = true)
        drawCircle(
            centerX,
            centerY,
            radius = 5,
            color = palette.primary,
            filled = false,
            strokeWidth = 1,
        )
        if (selected[cellId]) {
            drawCircle(centerX, centerY, radius = 3, color = palette.primary, filled = true)
        } else {
            drawCircle(centerX, centerY, radius = 1, color = palette.muted, filled = true)
        }
    }
    selected.fill(false)
}

/** 返回场景是否需要因非敏感请求变化而重建。 */
internal fun shouldSubmitPatternCredentialRequest(
    previous: PatternCredentialSceneRequest?,
    next: PatternCredentialSceneRequest,
): Boolean = previous != next
