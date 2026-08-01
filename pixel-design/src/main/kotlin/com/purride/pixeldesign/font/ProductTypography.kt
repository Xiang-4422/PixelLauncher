package com.purride.pixeldesign.font

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelTextRasterizer
import com.purride.pixelui.TextStyle

/**
 * 向 Launcher、锁屏和预览共享当前字体选择，并允许组件请求语义角色或显式原生字号。
 *
 * 未传入 [size] 的文本继承设置页选择；传入字号时仍保持同一字体家族和宽度模式，
 * 请求不存在的字号会立即失败，禁止运行时缩放或寻找最近字号。
 */
public class ProductTypography(
    /** 设置页当前选择的字体家族、宽度模式和默认字号。 */
    val selection: ProductFontSelection,
    /** 把一个已归一化选择解析为共享栅格器。 */
    private val rasterizerResolver: (ProductFontSelection) -> PixelTextRasterizer,
) {

    /** 返回同一家族和宽度模式下指定字号的共享栅格器。 */
    fun rasterizer(size: ProductFontSize = selection.size): PixelTextRasterizer {
        /** 经过字体能力矩阵校验的组件级选择。 */
        val resolvedSelection = ProductFontCatalog.resolveRenderable(selection.copy(size = size))
        return rasterizerResolver(resolvedSelection)
    }

    /** 返回当前家族和宽度模式承担语义角色的原生栅格器。 */
    fun rasterizer(role: ProductTextRole): PixelTextRasterizer {
        val roleSelection = ProductFontCatalog.selectionForRole(selection.family, selection.widthMode, role)
        return rasterizerResolver(roleSelection)
    }

    /** 创建带显式字号栅格器的文本样式；省略字号时使用设置页默认值。 */
    fun textStyle(
        color: PixelColor,
        size: ProductFontSize = selection.size,
        lineSpacing: Int = 0,
        letterSpacing: Int = 0,
        lineHeight: Int? = null,
    ): TextStyle {
        return TextStyle(
            color = color,
            textRasterizer = rasterizer(size),
            lineSpacing = lineSpacing,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        )
    }

    /** 创建使用同家族原生语义 face 的文本样式。 */
    fun textStyle(
        color: PixelColor,
        role: ProductTextRole,
        lineSpacing: Int = 0,
        letterSpacing: Int = 0,
        lineHeight: Int? = null,
    ): TextStyle {
        return TextStyle(
            color = color,
            textRasterizer = rasterizer(role),
            lineSpacing = lineSpacing,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        )
    }

    /** 提供主题解析期间使用的最小字体实现，真实 Host 首帧会替换它。 */
    companion object {
        /** 不依赖 Android assets 的安全默认 typography。 */
        val Default: ProductTypography = ProductTypography(
            selection = ProductFontCatalog.defaultUiFontSelection,
            rasterizerResolver = { PixelBitmapFont.Default },
        )
    }
}
