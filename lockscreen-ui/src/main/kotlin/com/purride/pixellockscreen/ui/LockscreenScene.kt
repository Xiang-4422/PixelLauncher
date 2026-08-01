package com.purride.pixellockscreen.ui

import com.purride.pixelcore.PixelColor
import com.purride.pixeldesign.ProductPalette
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixeldesign.ProductThemeCatalog
import com.purride.pixeldesign.ProductThemeFamily
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SafeArea
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset

/** 纵向锁屏使用的固定逻辑宽度。 */
internal const val LOCKSCREEN_PORTRAIT_WIDTH = 96

/** 纵向锁屏使用的固定逻辑高度。 */
internal const val LOCKSCREEN_PORTRAIT_HEIGHT = 192

/** 横向锁屏使用的固定逻辑宽度。 */
internal const val LOCKSCREEN_LANDSCAPE_WIDTH = 192

/** 横向锁屏使用的固定逻辑高度。 */
internal const val LOCKSCREEN_LANDSCAPE_HEIGHT = 96

/** 锁屏内容和安全区之间保留的最小逻辑像素留白。 */
private const val LOCKSCREEN_CONTENT_INSET = 6

/** 锁屏中央信息组内部的逻辑像素间距。 */
private const val LOCKSCREEN_INFO_SPACING = 4

/** 透明锁屏静态场景所需的全部不可变输入。 */
internal data class LockscreenSceneRequest(
    /** 当前格式化后的展示状态。 */
    val state: LockscreenUiState,
    /** 当前主题家族。 */
    val family: ProductThemeFamily,
    /** 当前主题实际亮度。 */
    val brightness: ProductThemeBrightness,
    /** 当前宿主是否采用横向布局。 */
    val isLandscape: Boolean,
)

/** 判断下一帧是否包含需要重新提交给像素引擎的实际变化。 */
internal fun shouldSubmitLockscreenRequest(
    previous: LockscreenSceneRequest?,
    next: LockscreenSceneRequest,
): Boolean = previous != next

/** 根据不可变请求构建不含系统服务、手势或连续动画的锁屏 Widget 树。 */
internal fun buildLockscreenScene(request: LockscreenSceneRequest): Widget {
    /** 当前请求解析出的共享产品色板。 */
    val palette = ProductThemeCatalog.resolve(request.family, request.brightness)
    /** 横屏降低时间倍率，避免较短安全高度裁切其他状态。 */
    val timeScale = lockscreenTimeScale(request.isLandscape)
    /** 横屏将辅助信息保持为单倍字体，纵屏日期使用双倍字体强化层级。 */
    val dateScale = if (request.isLandscape) 1 else 2
    return SafeArea(
        minimum = com.purride.pixelui.PixelWindowInsets(
            left = LOCKSCREEN_CONTENT_INSET,
            top = LOCKSCREEN_CONTENT_INSET,
            right = LOCKSCREEN_CONTENT_INSET,
            bottom = LOCKSCREEN_CONTENT_INSET,
        ),
        child = Column(
            mainAxisSize = MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            children = listOf(
                Expanded(
                    child = Container(
                        alignment = Alignment.CENTER,
                        child = Column(
                            mainAxisSize = MainAxisSize.MIN,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                            crossAxisAlignment = CrossAxisAlignment.CENTER,
                            spacing = LOCKSCREEN_INFO_SPACING,
                            children = listOf(
                                outlinedLockscreenText(
                                    text = request.state.timeText,
                                    foreground = palette.primary,
                                    backing = palette.background,
                                    fontScale = timeScale,
                                    key = "lockscreen-time",
                                ),
                                outlinedLockscreenText(
                                    text = request.state.dateText,
                                    foreground = palette.secondary,
                                    backing = palette.background,
                                    fontScale = dateScale,
                                    key = "lockscreen-date",
                                ),
                                batteryStatus(request.state, palette),
                            ),
                        ),
                    ),
                ),
                outlinedLockscreenText(
                    text = request.state.unlockHint,
                    foreground = palette.muted,
                    backing = palette.background,
                    fontScale = 1,
                    key = "lockscreen-unlock-hint",
                ),
            ),
        ),
    )
}

/** 返回当前方向下的大时钟整数像素倍率。 */
internal fun lockscreenTimeScale(isLandscape: Boolean): Int = if (isLandscape) 3 else 4

/** 组合私有电池图形和百分比文字，不向通用图标目录引入锁屏专用状态。 */
private fun batteryStatus(state: LockscreenUiState, palette: ProductPalette): Widget {
    /** 充电状态使用警示色加强识别，普通状态沿用主题主色。 */
    val statusColor = if (state.isCharging) palette.alert else palette.primary
    /** 电量百分比和充电状态组成的稳定只读文本。 */
    val statusText = buildString {
        append(state.batteryPercent)
        append('%')
        if (state.isCharging) append(" CHARGING")
    }
    return Row(
        mainAxisSize = MainAxisSize.MIN,
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        spacing = 3,
        children = listOf(
            batteryGlyph(state.batteryPercent, statusColor, palette.background),
            outlinedLockscreenText(
                text = statusText,
                foreground = statusColor,
                backing = palette.background,
                fontScale = 1,
                key = "lockscreen-battery-text",
            ),
        ),
    )
}

/** 绘制一个包含真实电量填充的紧凑像素电池图形。 */
private fun batteryGlyph(level: Int, color: PixelColor, backing: PixelColor): Widget {
    /** 九像素内部宽度按百分比向上取整，1% 仍保留一个可见像素。 */
    val filledWidth = if (level == 0) 0 else ((level * 9) + 99) / 100
    return CustomPaint(width = 13, height = 7, key = "lockscreen-battery-glyph") {
        drawRect(left = 0, top = 1, width = 11, height = 5, color = backing)
        drawRect(left = 0, top = 1, width = 11, height = 5, color = color)
        fillRect(left = 11, top = 2, width = 2, height = 3, color = color)
        if (filledWidth > 0) {
            fillRect(left = 1, top = 2, width = filledWidth, height = 3, color = color)
        }
    }
}

/** 通过四向单像素底色副本给透明壁纸上的文本提供硬边反差。 */
internal fun outlinedLockscreenText(
    text: String,
    foreground: PixelColor,
    backing: PixelColor,
    fontScale: Int,
    key: Any,
): Widget {
    /** 主文字和描边文字共享的整数倍率与单行约束。 */
    fun textLayer(color: PixelColor, layerKey: String): Widget = Text(
        data = text,
        style = TextStyle(color = color, fontScale = fontScale),
        softWrap = false,
        maxLines = 1,
        overflow = TextOverflow.ELLIPSIS,
        textAlign = TextAlign.CENTER,
        key = "$key-$layerKey",
    )
    /** 描边副本相对主文字采用的四个正交偏移。 */
    val outlineOffsets = listOf(
        IntOffset(-1, 0),
        IntOffset(1, 0),
        IntOffset(0, -1),
        IntOffset(0, 1),
    )
    return Padding(
        padding = EdgeInsets.all(1),
        child = Stack(
            alignment = Alignment.CENTER,
            children = outlineOffsets.mapIndexed { index, offset ->
                Transform.translate(
                    offset = offset,
                    child = textLayer(backing, "outline-$index"),
                    key = "$key-outline-transform-$index",
                )
            } + textLayer(foreground, "foreground"),
            key = "$key-stack",
        ),
        key = "$key-padding",
    )
}
