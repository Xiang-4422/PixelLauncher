package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.PixelTextOverflow
import com.purride.pixelui.PixelTextStyle
import com.purride.pixelui.TextAlign
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelText
import com.purride.pixelui.internal.toPixelTextAlign

/**
 * Flutter 风格 `Text` 的直接 render object widget。
 *
 * 当前仍实现 `BridgeWidget`，用于旧 bridge 父节点 fallback；但正常 pipeline
 * 可以直接从 retained element 拿到 `RenderText`，不再绕一圈 legacy node lowering。
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
) : RenderObjectWidget(
    key = key,
), BridgeWidget {
    override val childWidgets: List<Widget> = emptyList()

    /**
     * 只有首批 pipeline 文本能力内的配置直接创建 render object，其余继续走 bridge fallback。
     */
    override fun createElement(): Element {
        return if (canCreateRenderObject()) {
            super.createElement()
        } else {
            BridgeAdapterElement(this)
        }
    }

    /**
     * 创建文本 render object，直接接入新 pipeline。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            defaultTextRasterizer = PixelBitmapFont.Default,
        )
    }

    /**
     * 把新的 widget 配置同步到既有文本 render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderText).updateText(
            text = data,
            style = resolveTextStyle(context),
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
        )
    }

    /**
     * fallback 到 bridge 时生成等价 legacy 文本节点。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return PixelText(
            text = data,
            modifier = PixelModifier.Empty,
            style = resolveTextStyle(context),
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign.toPixelTextAlign(),
            textDirection = Directionality.of(context),
            key = key,
        )
    }

    /**
     * 解析当前上下文下的最终文本样式。
     */
    private fun resolveTextStyle(context: BuildContext): PixelTextStyle {
        val resolvedTheme = context.resolveTheme(theme)
        return when (style) {
            PixelTextStyle.Default -> resolvedTheme.textStyle
            PixelTextStyle.Accent -> resolvedTheme.accentTextStyle
            else -> style
        }
    }

    /**
     * 判断当前文本配置是否属于首批直接 render object 能力。
     */
    private fun canCreateRenderObject(): Boolean {
        return !softWrap &&
            maxLines == 1 &&
            overflow == PixelTextOverflow.CLIP &&
            '\n' !in data
    }
}
