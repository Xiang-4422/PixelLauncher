package com.purride.pixeldemo.header

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

// ─── Scene entry ──────────────────────────────────────────────────────────────

object HeaderPreviewScene : DemoScene {
    override val id = "header_preview"
    override val title = "HEADER"
    override val description = "battery divider + launcher header"

    override fun build(env: DemoEnv): Widget = HeaderPreviewWidget(env)
}

// ─── Stateful wrapper for animation tick ──────────────────────────────────────

private class HeaderPreviewWidget(
    private val env: DemoEnv,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = HeaderPreviewState()

    inner class HeaderPreviewState : State<HeaderPreviewWidget>() {
        private var tick: Int = 0

        override fun build(context: BuildContext): Widget {
            val t = tick
            return Padding(
                horizontal = 0,
                vertical = 2,
                child = Column(
                    spacing = 6,
                    mainAxisSize = MainAxisSize.MIN,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    children = listOf(
                        sectionLabel("LAUNCHER HEADER — GREEN PHOSPHOR"),
                        PreviewHeader(
                            timeText = "14:30",
                            screenTitle = "HOME",
                            batteryLevel = 75,
                            isCharging = true,
                            chargeTick = t,
                            primaryColor = PixelColor.fromRgb(151, 255, 167),
                            accentColor  = PixelColor.fromRgb(199, 255, 208),
                        ),
                        sectionLabel("BATTERY DIVIDER — VARIOUS LEVELS"),
                        dividerRow("100%", 100, false, 0),
                        dividerRow(" 75%", 75,  false, 0),
                        dividerRow(" 50%", 50,  false, 0),
                        dividerRow(" 25%", 25,  false, 0),
                        dividerRow("  5%", 5,   false, 0),
                        sectionLabel("CHARGING ANIMATION  (tick=$t)"),
                        dividerRow(" 60% CHG", 60, true, t),
                        dividerRow(" 15% CHG", 15, true, t),
                        tickButton(t),
                    ),
                ),
            )
        }

        private fun sectionLabel(label: String): Widget =
            Text(label, style = TextStyle.Default)

        private fun dividerRow(label: String, level: Int, charging: Boolean, tick: Int): Widget =
            Row(
                spacing = 4,
                children = listOf(
                    SizedBox(
                        width = 36,
                        child = Text(label, style = TextStyle.Default),
                    ),
                    Expanded(
                        child = DemoBatteryDividerWidget(
                            batteryLevel = level,
                            isCharging = charging,
                            chargeTick = tick,
                            primaryColor = PixelColor.fromRgb(151, 255, 167),
                            accentColor  = PixelColor.fromRgb(199, 255, 208),
                        ),
                    ),
                ),
            )

        private fun tickButton(currentTick: Int): Widget =
            com.purride.pixelui.OutlinedButton(
                text = "NEXT TICK → ${(currentTick + 1) % 3}",
                onPressed = { setState { tick = (tick + 1) % 100 } },
                borderColor = PixelColor.fromRgb(200, 100, 0),
            )
    }
}

// ─── Standalone preview header (no app module dependency) ─────────────────────

private fun PreviewHeader(
    timeText: String,
    screenTitle: String,
    batteryLevel: Int,
    isCharging: Boolean,
    chargeTick: Int,
    primaryColor: PixelColor,
    accentColor: PixelColor,
): Widget = Column(
    children = listOf(
        Row(
            children = listOf(
                Text(timeText, style = TextStyle(color = primaryColor)),
                Expanded(child = SizedBox(width = 0, height = 0)),
                Text(screenTitle, style = TextStyle(color = primaryColor)),
            ),
            spacing = 0,
        ),
        DemoBatteryDividerWidget(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            chargeTick = chargeTick,
            primaryColor = primaryColor,
            accentColor = accentColor,
        ),
    ),
    spacing = 0,
    mainAxisSize = MainAxisSize.MIN,
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
)

// ─── Local BatteryDivider (pixel-demo can't import from :app) ─────────────────

private class DemoBatteryDividerWidget(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val chargeTick: Int,
    val primaryColor: PixelColor,
    val accentColor: PixelColor,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        DemoRenderBatteryDivider(batteryLevel, isCharging, chargeTick, primaryColor, accentColor)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        (renderObject as DemoRenderBatteryDivider).update(
            batteryLevel, isCharging, chargeTick, primaryColor, accentColor,
        )
    }
}

private class DemoRenderBatteryDivider(
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
        if (filledW > 0) {
            context.fillRect(offsetX, offsetY, filledW, 1, primaryColor)
        }
        if (isCharging) {
            val dotX = offsetX + filledW + (chargeTick % 3)
            if (dotX < offsetX + w) {
                context.buffer.setPixel(dotX, offsetY, accentColor)
            }
        }
    }

    fun update(
        batteryLevel: Int, isCharging: Boolean, chargeTick: Int,
        primaryColor: PixelColor, accentColor: PixelColor,
    ) {
        val changed = this.batteryLevel != batteryLevel || this.isCharging != isCharging ||
            this.chargeTick != chargeTick || this.primaryColor != primaryColor ||
            this.accentColor != accentColor
        if (!changed) return
        this.batteryLevel = batteryLevel; this.isCharging = isCharging
        this.chargeTick = chargeTick; this.primaryColor = primaryColor
        this.accentColor = accentColor
        markNeedsPaint()
    }
}
