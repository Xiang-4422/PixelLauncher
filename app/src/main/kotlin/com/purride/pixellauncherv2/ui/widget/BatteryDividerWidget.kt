package com.purride.pixellauncherv2.ui.widget

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize

/**
 * 1px 高的电量分隔线。
 *
 * 视觉规格：
 * - 整行底色由 `PixelHostView.pixelGridColor` 填充（透明像素显示为 dead-pixel 格）
 * - 左侧 `batteryLevel / 100 * width` 像素填充 [primaryColor]（电量满格高亮）
 * - 充电中：在填充区末端右移 `chargeTick % 3` 个像素处点亮一个 [accentColor]（闪烁点）
 *
 * @param batteryLevel  电量百分比 0–100
 * @param isCharging    是否正在充电
 * @param chargeTick    外部动画帧计数（增量不限），用于计算闪烁位置；未充电时忽略
 * @param primaryColor  电量填充色（来自当前 LauncherTheme）
 * @param accentColor   充电闪烁色（来自当前 LauncherTheme）
 */
class BatteryDividerWidget(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val chargeTick: Int,
    val primaryColor: PixelColor,
    val accentColor: PixelColor,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        RenderBatteryDivider(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = primaryColor,
            accentColor = accentColor,
        )

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as RenderBatteryDivider).update(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = primaryColor,
            accentColor = accentColor,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

internal class RenderBatteryDivider(
    private var batteryLevel: Int,
    private var isCharging: Boolean,
    private var chargeTick: Int,
    private var primaryColor: PixelColor,
    private var accentColor: PixelColor,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(width = constraints.maxWidth, height = 1)
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        val w = size.width
        if (w <= 0) return

        val filledW = (w.toLong() * batteryLevel.coerceIn(0, 100) / 100L)
            .toInt().coerceIn(0, w)

        // Battery fill: left portion in primaryColor
        if (filledW > 0) {
            context.fillRect(offsetX, offsetY, filledW, 1, primaryColor)
        }

        // Charging dot: one pixel just after the fill edge, offset by (chargeTick % 3)
        if (isCharging) {
            val dotX = offsetX + filledW + (chargeTick % 3)
            if (dotX < offsetX + w) {
                context.buffer.setPixel(dotX, offsetY, accentColor)
            }
        }
    }

    fun update(
        batteryLevel: Int,
        isCharging: Boolean,
        chargeTick: Int,
        primaryColor: PixelColor,
        accentColor: PixelColor,
    ) {
        val changed = this.batteryLevel != batteryLevel ||
            this.isCharging != isCharging ||
            this.chargeTick != chargeTick ||
            this.primaryColor != primaryColor ||
            this.accentColor != accentColor
        if (!changed) return
        this.batteryLevel = batteryLevel
        this.isCharging = isCharging
        this.chargeTick = chargeTick
        this.primaryColor = primaryColor
        this.accentColor = accentColor
        markNeedsPaint()
    }
}
