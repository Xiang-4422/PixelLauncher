package com.purride.pixelui.internal

import com.purride.pixelcore.PixelColorMode
import com.purride.pixelui.BuildContext
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Widget

/**
 * 在 widget 树里传递当前渲染颜色模式。
 *
 * 宿主层通过 `HostRootWidget` 在根处注入，widget 树中任意节点通过
 * `BuildContext.colorMode()` 扩展读取；缺失时 fallback `Mono`，不崩溃。
 */
public class PixelColorModeProvider(
    public val colorMode: PixelColorMode,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return colorMode != (oldWidget as? PixelColorModeProvider)?.colorMode
    }

    public companion object {
        public fun maybeOf(context: BuildContext): PixelColorMode? {
            return context.dependOnInheritedWidgetOfExactType(PixelColorModeProvider::class)?.colorMode
        }

        public fun of(context: BuildContext): PixelColorMode {
            return maybeOf(context) ?: PixelColorMode.Mono
        }
    }
}

/**
 * 从当前 widget 树上下文读取颜色模式，缺省返回 [PixelColorMode.Mono]。
 */
public fun BuildContext.colorMode(): PixelColorMode = PixelColorModeProvider.of(this)
