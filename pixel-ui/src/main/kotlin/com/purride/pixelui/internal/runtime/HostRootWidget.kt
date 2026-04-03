package com.purride.pixelui.internal

import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.Directionality
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Theme
import com.purride.pixelui.ThemeData
import com.purride.pixelui.Widget

/**
 * 宿主级根环境包装。
 *
 * 现在 `PixelHostView` 不再在 `onDraw()` 里手工拼装一串环境组件，
 * 而是统一交给这个 root widget，方便后续继续扩展宿主级环境。
 */
internal data class HostRootWidget(
    val screenProfile: ScreenProfile,
    val textDirection: TextDirection,
    val themeData: ThemeData?,
    val child: Widget,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    override fun build(context: com.purride.pixelui.BuildContext): Widget {
        return MediaQuery(
            data = MediaQueryData(
                logicalWidth = screenProfile.logicalWidth,
                logicalHeight = screenProfile.logicalHeight,
                screenProfile = screenProfile,
            ),
            child = Directionality(
                textDirection = textDirection,
                child = themeData?.let { theme ->
                    Theme(
                        data = theme,
                        child = child,
                    )
                } ?: child,
            ),
        )
    }
}
