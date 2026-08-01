package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Widget
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.launcher.SnakeController
import com.purride.pixellauncherv2.launcher.SnakeFieldLayer
import com.purride.pixellauncherv2.launcher.SnakeModel
import com.purride.pixellauncherv2.ui.text.PreparedLauncherFont
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.launcherInlineIconSize
import com.purride.pixellauncherv2.ui.widget.launcherSystemIcon

/**
 * 贪吃蛇：上方自绘场地（蛇/食物/HUD/终局提示全在画布里），下方十字方向键。
 *
 * 引擎手势只有水平滑动，四方向操控用**屏下按键**——顺便也是点阵掌机的本色布局。
 * 硬件方向键在 MainActivity 按 DIALER 先例接入，按钮是触摸主通路。
 */
internal fun SnakeScreen(
    controller: SnakeController,
    preparedFont: PreparedLauncherFont,
    theme: LauncherTheme,
): Widget {
    /** 方向键图标随游戏使用的正文字号选择规格。 */
    val iconSize = launcherInlineIconSize(preparedFont.selection.size.px)
    return Padding(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MAX,
            spacing = LauncherSpacing.ROW_SPACING,
            children = listOf(
                Expanded(
                    child = SnakeFieldLayer(
                        controller = controller,
                        rasterizer = preparedFont.defaultRasterizer,
                        fieldColor = theme.text.primary,
                        dimColor = theme.sms.timestamp,
                        dangerColor = theme.semantic.danger,
                        key = "snake-field",
                    ),
                ),
                // 裸 Row 不能 STRETCH：Column 给非弹性子项的高度约束是"剩余全部"，
                // 交叉轴拉伸会把按钮行撑满全屏、把场地挤成零高。高度由按钮内容自撑。
                Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = listOf(
                        directionKey(PixelSystemIcon.BACK, iconSize, SnakeModel.Direction.LEFT, controller, theme),
                        directionKey(PixelSystemIcon.ARROW_UP, iconSize, SnakeModel.Direction.UP, controller, theme),
                        directionKey(PixelSystemIcon.ARROW_DOWN, iconSize, SnakeModel.Direction.DOWN, controller, theme),
                        directionKey(PixelSystemIcon.FORWARD, iconSize, SnakeModel.Direction.RIGHT, controller, theme),
                    ),
                ),
            ),
        ),
    )
}

/** 方向键：等宽边框按钮；一行四键，拇指横扫可达。 */
private fun directionKey(
    icon: PixelSystemIcon,
    iconSize: PixelSystemIconSize,
    direction: SnakeModel.Direction,
    controller: SnakeController,
    theme: LauncherTheme,
): Widget = Expanded(
    child = Semantics(
        label = direction.name,
        role = PixelSemanticRole.BUTTON,
        child = GestureDetector(
            onTap = { controller.turn(direction) },
            child = Container(
                alignment = Alignment.CENTER,
                borderColor = theme.button.border,
                padding = EdgeInsets.symmetric(
                    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                    vertical = LauncherSpacing.ROW_SPACING * 2,
                ),
                child = launcherSystemIcon(icon = icon, size = iconSize, color = theme.button.text),
            ),
        ),
    ),
)
