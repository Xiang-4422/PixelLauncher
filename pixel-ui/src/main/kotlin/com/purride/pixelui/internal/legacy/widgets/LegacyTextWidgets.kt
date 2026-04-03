package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.TextAlign
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelText
import com.purride.pixelui.internal.toPixelTextAlign

/**
 * Flutter 风格 `Text` 的 legacy bridge widget。
 */
internal data class TextWidget(
    val data: String,
    val style: PixelTextStyle,
    val theme: com.purride.pixelui.PixelThemeData?,
    val softWrap: Boolean,
    val maxLines: Int,
    val overflow: PixelTextOverflow,
    val textAlign: TextAlign,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时解析主题文本样式，并生成 legacy 文本节点。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedTheme = context.resolveTheme(theme)
        val resolvedStyle = when (style) {
            PixelTextStyle.Default -> resolvedTheme.textStyle
            PixelTextStyle.Accent -> resolvedTheme.accentTextStyle
            else -> style
        }
        return LegacyLeafWidget(
            key = key,
        ) {
            PixelText(
                text = data,
                modifier = PixelModifier.Empty,
                style = resolvedStyle,
                softWrap = softWrap,
                maxLines = maxLines,
                overflow = overflow,
                textAlign = textAlign.toPixelTextAlign(),
                textDirection = Directionality.of(context),
                key = key,
            )
        }
    }
}
