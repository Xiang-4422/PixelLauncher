package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget

/**
 * 宿主级根环境包装。
 *
 * 注入 MediaQuery、Directionality 和默认文本栅格器，供 widget 树中任意节点消费。
 */
internal data class HostRootWidget(
    val screenProfile: ScreenProfile,
    val textDirection: TextDirection,
    val textRasterizer: PixelTextRasterizer,
    val windowInsets: com.purride.pixelui.PixelWindowInsets,
    val child: Widget,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    override fun build(context: com.purride.pixelui.BuildContext): Widget {
        return MediaQuery(
            data = MediaQueryData(
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
                screenProfile = screenProfile,
                viewInsets = windowInsets,
                viewPadding = windowInsets,
                padding = windowInsets,
            ),
            child = Directionality(
                textDirection = textDirection,
                child = DefaultTextRasterizer(
                    rasterizer = textRasterizer,
                    child = child,
                ),
            ),
        )
    }
}
