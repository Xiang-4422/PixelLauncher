package com.purride.pixelui

import com.purride.pixelcore.PixelColor

/**
 * 调试用 FPS / 帧时间 overlay。
 *
 * 在 widget 树某处直接 `PixelDebugOverlay(stats)` 即可。配合
 * [PixelHostView.frameStatsObserver] + `ValueNotifier<PixelHostFrameStats?>`
 * 实现实时刷新：
 *
 * ```kotlin
 * val statsNotifier = ValueNotifier<PixelHostFrameStats?>(null)
 * hostView.frameStatsObserver = { statsNotifier.value = it }
 *
 * // 在 widget 树里
 * Stack(children = listOf(
 *     mainContent,
 *     Positioned(top = 0, right = 0, child = ValueListenableBuilder(statsNotifier) { _, s ->
 *         PixelDebugOverlay(s)
 *     }),
 * ))
 * ```
 *
 * stats 为 null（还没收到第一帧）时返回 0×0 占位；非 null 时画两行文字：
 *
 * ```
 * FPS 60
 * MS  16
 * ```
 *
 * 视觉规格：黑底（半透明）边框 +1px padding + 2 行小字。整块约 14×9 像素。
 * 文本颜色固定为亮黄绿（不依赖 theme），便于在任意背景上识别。
 *
 * 不在 paint 热路径产生持久分配——每次调用只构造少量临时 widget。
 */
public fun PixelDebugOverlay(
    stats: PixelHostFrameStats?,
    key: Any? = null,
    inspector: PixelInspectorSnapshot? = null,
    activeTickerCount: Int? = null,
): Widget {
    if (stats == null && inspector == null && activeTickerCount == null) {
        return SizedBox(width = 0, height = 0, key = key)
    }
    val ink = PixelColor.fromRgb(0xC8, 0xFF, 0x40)
    val bg = PixelColor.fromRgb(0x00, 0x00, 0x00)
    val border = PixelColor.fromRgb(0x60, 0x60, 0x60)
    val style = TextStyle(color = ink)
    val lines = buildList {
        stats?.let {
            add("FPS ${it.fpsAvg.toInt()}")
            add("MS  ${it.deltaMs}")
        }
        inspector?.let {
            val targets = it.targetCounts
            add("TGT C${targets.click} L${targets.list} P${targets.pager} T${targets.textInput}")
            add("SEM ${targets.semantics} PEND ${if (it.hasPendingBuild) 1 else 0}")
            if (it.activePagerCount > 0 || it.activeListCount > 0 || it.activeSlider || it.activeScrollbar || it.activeRefresh) {
                add("ACT P${it.activePagerCount} L${it.activeListCount}")
            }
        }
        activeTickerCount?.let { add("TICK $it") }
    }
    return DecoratedBox(
        fillColor = bg,
        borderColor = border,
        child = Padding(
            all = 1,
            child = Column(
                crossAxisAlignment = CrossAxisAlignment.START,
                mainAxisSize = MainAxisSize.MIN,
                children = lines.map { line -> Text(line, style = style) },
            ),
        ),
        key = key,
    )
}
