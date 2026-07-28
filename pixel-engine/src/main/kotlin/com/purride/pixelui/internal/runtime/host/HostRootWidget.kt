package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.DefaultTextRasterizer
import com.purride.pixelui.Directionality
import com.purride.pixelui.PixelAdaptiveEnvironment
import com.purride.pixelui.PixelAdaptiveLayoutData
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelHostCapabilitySet
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextDirection
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.host.PixelHostCapabilityScope
import com.purride.pixelui.animation.PixelTickerProvider

/**
 * 宿主级根环境包装。
 *
 * 注入 HostCapabilities、MediaQuery、Directionality、motion scope 和默认文本栅格器，
 * 供 widget 树中任意节点消费。
 */
internal data class HostRootWidget(
    /** Logical screen metrics inherited by layout widgets. */
    val screenProfile: ScreenProfile,
    /** Current physical Host width used for dp size classes. */
    val physicalWidthPx: Int = 0,
    /** Current physical Host height used for dp size classes. */
    val physicalHeightPx: Int = 0,
    /** Host default bitmap text rasterizer. */
    val textRasterizer: PixelTextRasterizer,
    /** Stable system-bar padding in logical pixels. */
    val windowInsets: PixelWindowInsets,
    /** Temporary IME or obstruction inset in logical pixels. */
    val viewInsets: PixelWindowInsets,
    /** 当前 Engine 提供给本 Host 的聚焦 Host capability。 */
    val hostServices: PixelHostCapabilitySet = PixelHostCapabilitySet.Empty,
    /** 当前 Engine 注入根树的主题 token；null 保留历史无显式 Theme 包装行为。 */
    val themeTokens: PixelThemeTokens? = null,
    /** 绑定生命周期的 ticker provider，由动效相关 widget 继承。 */
    val motionVsync: PixelTickerProvider? = null,
    /**
     * Atomic Host environment snapshot used by every capability-aware root provider.
     *
     * 布局方向、文字缩放、对比度和动效偏好全部来自这一份快照；Host 在渲染前已经解析好它，
     * 离屏渲染与直接 render 测试则使用 headless 默认值。
     */
    val capabilities: HostCapabilitiesData = HostCapabilitiesData.Default,
    /** Application widget subtree receiving the complete Host environment. */
    val child: Widget,
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Builds the inherited Host environment around the application subtree. */
    override fun build(context: com.purride.pixelui.BuildContext): Widget {
        /** Stable safe padding after transient obstruction overlap is removed. */
        val padding = windowInsets.exclude(viewInsets)
        /** Atomic physical/logical adaptive snapshot parallel to HostCapabilities and MediaQuery. */
        val adaptiveData = PixelAdaptiveLayoutData(
            physicalWidthPx = physicalWidthPx.coerceAtLeast(0),
            physicalHeightPx = physicalHeightPx.coerceAtLeast(0),
            logicalWidth = screenProfile.logicalWidth,
            logicalHeight = screenProfile.logicalHeight,
            density = capabilities.density,
            viewInsets = viewInsets,
            viewPadding = windowInsets,
            padding = padding,
            displayFeatures = capabilities.displayFeatures,
        )
        /** Engine 主题只包装当前 Host 子树，不会进入其他 Engine 或 Host。 */
        val themedChild: Widget = themeTokens?.let { tokens ->
            PixelTheme(tokens = tokens, child = child)
        } ?: child
        /** 唯一的 Host capability 作用域，widget 树只从这里读取平台能力。 */
        val capabilityChild = PixelHostCapabilityScope(
            capabilities = hostServices,
            child = DefaultTextRasterizer(
                rasterizer = textRasterizer,
                child = themedChild,
            ),
        )
        /** 有 ticker 时再追加 motion scope；其余环境顺序保持历史语义。 */
        val motionChild = motionVsync?.let { vsync ->
            PixelMotionScope(
                vsync = vsync,
                settings = capabilities.motionSettings,
                child = capabilityChild,
            )
        } ?: capabilityChild
        return HostCapabilities(
            data = capabilities,
            child = PixelAdaptiveEnvironment(
                data = adaptiveData,
                child = MediaQuery(
                    data = MediaQueryData(
                        logicalWidth = screenProfile.logicalWidth,
                        logicalHeight = screenProfile.logicalHeight,
                        screenProfile = screenProfile,
                        viewInsets = viewInsets,
                        viewPadding = windowInsets,
                        padding = padding,
                    ),
                    child = Directionality(
                        textDirection = capabilities.layoutDirection,
                        child = motionChild,
                    ),
                ),
            ),
        )
    }

    /** Removes transient overlap from stable padding without producing negative logical insets. */
    private fun PixelWindowInsets.exclude(overlap: PixelWindowInsets): PixelWindowInsets {
        return PixelWindowInsets(
            left = (left - overlap.left).coerceAtLeast(0),
            top = (top - overlap.top).coerceAtLeast(0),
            right = (right - overlap.right).coerceAtLeast(0),
            bottom = (bottom - overlap.bottom).coerceAtLeast(0),
        )
    }
}
