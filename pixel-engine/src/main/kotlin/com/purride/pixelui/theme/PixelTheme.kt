package com.purride.pixelui

/**
 * 向 widget 子树提供唯一的 Pixel UI 主题模型。
 *
 * 只覆盖 widget 层的语义 token；宿主显示、输入法、insets 和生命周期仍由
 * PixelHostView / PixelHostSetupConfig 管理。
 *
 * Provides the complete [PixelThemeTokens] graph inherited by every standard component.
 */
public class PixelTheme(
    /** 子树继承的完整语义 token 图。
 *
 * Complete semantic token graph inherited by standard components.
 */
    public val tokens: PixelThemeTokens,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    /** token 图发生任何变化时通知全部依赖该主题的子树重建。 */
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        /** 上一帧提供者；类型不同时必须无条件通知。 */
        val oldTheme = oldWidget as? PixelTheme ?: return true
        return oldTheme.tokens != tokens
    }

    /** 集中提供 `PixelTheme` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 查询最近的完整 token 图；没有 `PixelTheme` 提供者时返回 null。
 *
 * Returns the nearest complete token graph, or null when no PixelTheme is inherited.
 */
        public fun maybeOf(context: BuildContext): PixelThemeTokens? {
            return context.dependOnInheritedWidgetOfExactType<PixelTheme>()?.tokens
        }

        /** 查询最近的完整 token 图，缺少提供者时回落到 [PixelThemeTokens.Default]。
 *
 * Returns the nearest complete token graph or [PixelThemeTokens.Default].
 */
        public fun of(context: BuildContext): PixelThemeTokens {
            return maybeOf(context) ?: PixelThemeTokens.Default
        }
    }
}
