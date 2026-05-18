package com.purride.pixelui

import com.purride.pixelcore.ScreenProfile

public enum class TextDirection {
    LTR,
    RTL,
}

public data class MediaQueryData(
    val logicalWidth: Int,
    val logicalHeight: Int,
    val screenProfile: ScreenProfile,
)

public class Theme(
    public val data: ThemeData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? Theme)?.data
    }

    public companion object {
        public fun maybeOf(context: BuildContext): ThemeData? {
            return context.dependOnInheritedWidgetOfExactType<Theme>()?.data
        }

        public fun of(context: BuildContext): ThemeData {
            return maybeOf(context) ?: ThemeData.Default
        }
    }
}

public class Directionality(
    public val textDirection: TextDirection,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return textDirection != (oldWidget as? Directionality)?.textDirection
    }

    public companion object {
        public fun maybeOf(context: BuildContext): TextDirection? {
            return context.dependOnInheritedWidgetOfExactType<Directionality>()?.textDirection
        }

        public fun of(context: BuildContext): TextDirection {
            return maybeOf(context) ?: TextDirection.LTR
        }
    }
}

public class MediaQuery(
    public val data: MediaQueryData,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(
    child = child,
    key = key,
) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return data != (oldWidget as? MediaQuery)?.data
    }

    public companion object {
        public fun maybeOf(context: BuildContext): MediaQueryData? {
            return context.dependOnInheritedWidgetOfExactType<MediaQuery>()?.data
        }

        public fun of(context: BuildContext): MediaQueryData {
            return maybeOf(context)
                ?: error("当前上下文里没有 MediaQuery，宿主需要先包一层 MediaQuery。")
        }
    }
}
