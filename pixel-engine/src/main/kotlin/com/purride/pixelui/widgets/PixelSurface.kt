package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSurface
import com.purride.pixelui.internal.SingleChildRenderObjectWidget
import com.purride.pixelui.internal.toPixelAlignment

/**
 * 定义 `PixelSurfaceDecoration` 在 `PixelSurface` 中承担的数据与行为边界。
 *
 * Hard-edged decoration for a pixel-aligned [PixelSurface].
 *
 * Radius and elevation are rendered as integer stair steps without blur or anti-aliasing. The
 * positive diagonal [shadowOffset] is included in layout, so a themed overlay never paints its
 * hard shadow outside the measured box.
 *
 * @property fillColor Optional surface fill.
 * @property borderColor Optional border color.
 * @property borderWidth Number of nested one-pixel border layers.
 * @property cornerRadius Stair-step corner radius in logical pixels.
 * @property shadowColor Optional hard-shadow color.
 * @property shadowOffset Positive diagonal hard-shadow offset in logical pixels.
 */
public data class PixelSurfaceDecoration(
    public val fillColor: PixelColor? = null,
    public val borderColor: PixelColor? = null,
    public val borderWidth: Int = 1,
    public val cornerRadius: Int = 0,
    public val shadowColor: PixelColor? = null,
    public val shadowOffset: Int = 0,
) {
    init {
        require(borderWidth >= 0) { "PixelSurfaceDecoration.borderWidth must be >= 0" }
        require(cornerRadius >= 0) { "PixelSurfaceDecoration.cornerRadius must be >= 0" }
        require(shadowOffset >= 0) { "PixelSurfaceDecoration.shadowOffset must be >= 0" }
    }

    /** 集中提供 `PixelSurface` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 公开 `PixelSurface` 的 `None` 配置或运行值。
 *
 * Decoration that paints no fill, border, radius, or elevation.
 */
        public val None: PixelSurfaceDecoration = PixelSurfaceDecoration(borderWidth = 0)
    }
}

/**
 * 执行 `PixelSurface` 的 `PixelSurface` 公开行为；具体参数、返回和副作用见下文。
 *
 * Paints one pixel-aligned surface with optional stair-step radius and hard elevation shadow.
 *
 * Unlike the legacy [Container], the complete [decoration] is one required value, so this API can
 * evolve independently without changing the existing Container JVM descriptor. Standard
 * components resolve [PixelComponentColorTokens] into this primitive at build time.
 *
 * @param decoration Concrete pixel decoration, normally resolved from the current theme.
 * @param child Optional retained child painted above the surface.
 * @param width Optional exact main-surface width, excluding margin and shadow.
 * @param height Optional exact main-surface height, excluding margin and shadow.
 * @param padding Insets between the main surface and [child].
 * @param margin Transparent outer insets around the decorated box.
 * @param alignment Alignment of [child] within remaining padded space.
 * @param key Stable retained and render identity.
 */
public fun PixelSurface(
    decoration: PixelSurfaceDecoration,
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return PixelSurfaceWidget(
        child = child,
        width = width,
        height = height,
        padding = padding,
        margin = margin,
        decoration = decoration,
        alignment = alignment,
        key = key,
    )
}

/** Retained bridge from the public surface primitive to the shared RenderSurface implementation. */
private data class PixelSurfaceWidget(
    /** Optional retained surface content. */
    override val child: Widget?,
    /** Optional exact main-surface width. */
    val width: Int?,
    /** Optional exact main-surface height. */
    val height: Int?,
    /** Content padding inside the main surface. */
    val padding: EdgeInsets?,
    /** Transparent space outside the surface and its shadow. */
    val margin: EdgeInsets?,
    /** Concrete paint properties resolved by the caller. */
    val decoration: PixelSurfaceDecoration,
    /** Child alignment inside the padded content box. */
    val alignment: Alignment,
    /** Stable retained and render identity. */
    override val key: Any?,
) : SingleChildRenderObjectWidget(child = child, key = key) {
    /** Creates the shared surface render object with complete pixel decoration. */
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSurface(
            fillColor = decoration.fillColor,
            borderColor = decoration.borderColor,
            borderWidth = decoration.borderWidth,
            cornerRadius = decoration.cornerRadius,
            shadowColor = decoration.shadowColor,
            shadowOffset = decoration.shadowOffset,
            alignment = alignment.toPixelAlignment(),
            explicitWidth = width,
            explicitHeight = height,
            outerPaddingLeft = margin?.left ?: 0,
            outerPaddingTop = margin?.top ?: 0,
            outerPaddingRight = margin?.right ?: 0,
            outerPaddingBottom = margin?.bottom ?: 0,
            contentPaddingLeft = padding?.left ?: 0,
            contentPaddingTop = padding?.top ?: 0,
            contentPaddingRight = padding?.right ?: 0,
            contentPaddingBottom = padding?.bottom ?: 0,
        )
    }

    /** Updates paint and layout properties without replacing the retained render object. */
    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSurface).updateSurface(
            fillColor = decoration.fillColor,
            borderColor = decoration.borderColor,
            borderWidth = decoration.borderWidth,
            cornerRadius = decoration.cornerRadius,
            shadowColor = decoration.shadowColor,
            shadowOffset = decoration.shadowOffset,
            alignment = alignment.toPixelAlignment(),
            explicitWidth = width,
            explicitHeight = height,
            outerPaddingLeft = margin?.left ?: 0,
            outerPaddingTop = margin?.top ?: 0,
            outerPaddingRight = margin?.right ?: 0,
            outerPaddingBottom = margin?.bottom ?: 0,
            contentPaddingLeft = padding?.left ?: 0,
            contentPaddingTop = padding?.top ?: 0,
            contentPaddingRight = padding?.right ?: 0,
            contentPaddingBottom = padding?.bottom ?: 0,
        )
    }
}
