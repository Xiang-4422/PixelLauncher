package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.PixelMotionThemeData
import com.purride.pixelui.PixelTheme

/**
 * widgets artifact 向 runtime 兼容入口提供的非稳定桥。
 *
 * 该对象只服务 sibling artifact，位于 internal package，不属于消费者稳定 API。
 */
public object PixelWidgetArtifactAccess {
    /** 返回最近 PixelTheme 中的 motion token；未安装主题时返回 null。 */
    public fun motionTheme(context: BuildContext): PixelMotionThemeData? {
        return PixelTheme.maybeTokensOf(context)?.motion
    }
}
