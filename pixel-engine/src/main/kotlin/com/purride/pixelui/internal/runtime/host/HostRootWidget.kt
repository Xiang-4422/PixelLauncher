package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelHostBridge
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.host.PixelHostBridgeScope

/**
 * 宿主级根环境包装。
 *
 * 注入 MediaQuery、Directionality 和默认文本栅格器，供 widget 树中任意节点消费。
 */
internal data class HostRootWidget(
    val screenProfile: ScreenProfile,
    val textDirection: TextDirection,
    val textRasterizer: PixelTextRasterizer,
    val windowInsets: PixelWindowInsets,
    val viewInsets: PixelWindowInsets,
    val hostBridge: PixelHostBridge? = null,
    val child: Widget,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: com.purride.pixelui.BuildContext): Widget {
        return MediaQuery(
            data = MediaQueryData(
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
                screenProfile = screenProfile,
                viewInsets = viewInsets,
                viewPadding = windowInsets,
                padding = windowInsets.exclude(viewInsets),
            ),
            child = Directionality(
                textDirection = textDirection,
                child = PixelHostBridgeScope(
                    bridge = hostBridge,
                    child = DefaultTextRasterizer(
                        rasterizer = textRasterizer,
                        child = child,
                    ),
                ),
            ),
        )
    }

    private fun PixelWindowInsets.exclude(overlap: PixelWindowInsets): PixelWindowInsets {
        return PixelWindowInsets(
            left = (left - overlap.left).coerceAtLeast(0),
            top = (top - overlap.top).coerceAtLeast(0),
            right = (right - overlap.right).coerceAtLeast(0),
            bottom = (bottom - overlap.bottom).coerceAtLeast(0),
        )
    }
}
