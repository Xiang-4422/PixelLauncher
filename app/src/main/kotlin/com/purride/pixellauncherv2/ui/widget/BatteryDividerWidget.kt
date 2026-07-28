package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixellauncherv2.launcher.LauncherHeaderLayout

fun StatusBarBatteryFrame(
    /** 状态栏内容和电量线共同使用的完整逻辑宽度。 */
    width: Int,
    contentHeight: Int,
    row: Widget,
    divider: Widget,
): Widget {
    val rowHeight = (contentHeight - LauncherHeaderLayout.dividerHeight).coerceAtLeast(0)
    return Column(
        children = listOf(
            Container(
                width = width.coerceAtLeast(1),
                height = rowHeight,
                child = row,
            ),
            divider,
        ),
        spacing = 0,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

/**
 * 1px 高的电量分隔线。
 *
 * 视觉规格：
 * - 整行按当前电量等级显示绿色、黄色或红色
 * - 当前电量区域使用不透明颜色，剩余容量区域使用同色 50% 透明度
 * - 充电中：同色实心像素只在剩余容量区域内从右向左循环移动
 *
 * @param batteryLevel  电量百分比 0–100
 * @param isCharging    是否正在充电
 * @param chargeTick    外部动画帧计数（增量不限），用于计算充电像素位置；未充电时忽略
 * @param highColor     高电量颜色（51%–100%）
 * @param mediumColor   中电量颜色（21%–50%）
 * @param lowColor      低电量颜色（0%–20%）
 */
class BatteryDividerWidget(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val chargeTick: Int,
    val highColor: PixelColor,
    val mediumColor: PixelColor,
    val lowColor: PixelColor,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderBatteryDivider(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderBatteryDivider).update(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

internal class RenderBatteryDivider(
    private var batteryLevel: Int,
    private var isCharging: Boolean,
    private var chargeTick: Int,
    private var highColor: PixelColor,
    private var mediumColor: PixelColor,
    private var lowColor: PixelColor,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(width = constraints.maxWidth, height = 1)
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        val w = size.width
        if (w <= 0) return

        val safeLevel = batteryLevel.coerceIn(0, 100)
        val filledW = batteryFilledWidth(width = w, batteryLevel = safeLevel)
        val levelColor = batteryLevelColor(
            batteryLevel = safeLevel,
            highColor = highColor,
            mediumColor = mediumColor,
            lowColor = lowColor,
        )
        val remainingColor = levelColor.withAlpha(REMAINING_BATTERY_ALPHA)

        context.fillRect(offsetX, offsetY, w, 1, remainingColor)

        if (filledW > 0) {
            context.fillRect(offsetX, offsetY, filledW, 1, levelColor)
        }

        val remainingW = w - filledW
        if (isCharging && remainingW > 0) {
            val dotX = offsetX + filledW + remainingW - 1 - Math.floorMod(chargeTick, remainingW)
            context.buffer.setPixel(dotX, offsetY, levelColor)
        }
    }

    fun update(
        batteryLevel: Int,
        isCharging: Boolean,
        chargeTick: Int,
        highColor: PixelColor,
        mediumColor: PixelColor,
        lowColor: PixelColor,
    ) {
        val changed = this.batteryLevel != batteryLevel ||
            this.isCharging != isCharging ||
            this.chargeTick != chargeTick ||
            this.highColor != highColor ||
            this.mediumColor != mediumColor ||
            this.lowColor != lowColor
        if (!changed) return
        this.batteryLevel = batteryLevel
        this.isCharging = isCharging
        this.chargeTick = chargeTick
        this.highColor = highColor
        this.mediumColor = mediumColor
        this.lowColor = lowColor
        markNeedsPaint()
    }
}

internal const val LOW_BATTERY_MAX_PERCENT = 20
internal const val MEDIUM_BATTERY_MAX_PERCENT = 50
internal const val REMAINING_BATTERY_ALPHA = 128

internal fun batteryFilledWidth(width: Int, batteryLevel: Int): Int {
    if (width <= 0 || batteryLevel <= 0) return 0
    return (width.toLong() * batteryLevel.coerceAtMost(100) / 100L)
        .toInt()
        .coerceAtLeast(1)
        .coerceAtMost(width)
}

internal fun batteryLevelColor(
    batteryLevel: Int,
    highColor: PixelColor,
    mediumColor: PixelColor,
    lowColor: PixelColor,
): PixelColor = when {
    batteryLevel <= LOW_BATTERY_MAX_PERCENT -> lowColor
    batteryLevel <= MEDIUM_BATTERY_MAX_PERCENT -> mediumColor
    else -> highColor
}

private fun PixelColor.withAlpha(alpha: Int): PixelColor = PixelColor.fromArgb(
    a = alpha,
    r = red,
    g = green,
    b = blue,
)
