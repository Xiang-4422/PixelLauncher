package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 把 inspector target bounds 直接绘制到逻辑画面上的透明调试层。
 *
 * 该 widget 只绘制边框，不导出命中目标，因此放在 [Stack] 最上层也不会拦截点击或拖动。
 * [kinds] 可限制需要观察的 target 类型。
 */
public fun PixelInspectorBoundsOverlay(
    snapshot: PixelInspectorSnapshot,
    width: Int,
    height: Int,
    kinds: Set<PixelInspectorTargetKind> = PixelInspectorTargetKind.entries.toSet(),
    strokeWidth: Int = 1,
    key: Any? = null,
): Widget {
    require(width >= 0) { "width must be >= 0" }
    require(height >= 0) { "height must be >= 0" }
    require(strokeWidth > 0) { "strokeWidth must be > 0" }
    val targets = snapshot.targetSnapshots.filter { it.kind in kinds }
    return CustomPaint(width = width, height = height, key = key) {
        targets.forEach { target ->
            drawRect(
                left = target.left,
                top = target.top,
                width = target.width,
                height = target.height,
                color = target.kind.inspectorBoundsColor(),
                strokeWidth = strokeWidth,
            )
        }
    }
}

private fun PixelInspectorTargetKind.inspectorBoundsColor(): PixelColor {
    return when (this) {
        PixelInspectorTargetKind.CLICK -> PixelColor.fromRgb(255, 220, 40)
        PixelInspectorTargetKind.PAGER -> PixelColor.fromRgb(70, 120, 255)
        PixelInspectorTargetKind.LIST -> PixelColor.fromRgb(70, 220, 110)
        PixelInspectorTargetKind.SCROLLBAR -> PixelColor.fromRgb(40, 220, 220)
        PixelInspectorTargetKind.REFRESH -> PixelColor.fromRgb(255, 140, 40)
        PixelInspectorTargetKind.TEXT_INPUT -> PixelColor.fromRgb(240, 70, 220)
        PixelInspectorTargetKind.SLIDER -> PixelColor.fromRgb(255, 70, 70)
        PixelInspectorTargetKind.SEMANTICS -> PixelColor.White
    }
}
