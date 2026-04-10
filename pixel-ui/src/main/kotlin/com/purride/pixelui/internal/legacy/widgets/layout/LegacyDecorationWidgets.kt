package com.purride.pixelui.internal

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.Alignment
import com.purride.pixelui.BuildContext
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.PixelSurface
import com.purride.pixelui.internal.legacy.clickable
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `GestureDetector` 的 bridge widget。
 */
internal data class GestureDetectorWidget(
    val child: Widget,
    val onTap: () -> Unit,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 把点击语义折叠进 child 的 modifier。
     */
    override fun build(context: BuildContext): Widget {
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            childNode.withExtraModifier(PixelModifier.Empty.clickable(onTap))
        }
    }
}

/**
 * Flutter 风格 `DecoratedBox` 的直接 render object widget。
 *
 * 它仍实现 `BridgeWidget`，用于尚未迁移的旧父节点 fallback。
 */
internal data class DecoratedBoxWidget(
    override val child: Widget?,
    val fillTone: PixelTone,
    val borderTone: PixelTone?,
    val padding: Int,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
), BridgeWidget {
    override val childWidgets: List<Widget>
        get() = child?.let(::listOf) ?: emptyList()

    /**
     * 创建直接接入 pipeline 的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }

    /**
     * 同步新的装饰配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = fillTone,
            borderTone = borderTone,
            alignment = alignment.toPixelAlignment(),
            contentPaddingLeft = padding,
            contentPaddingTop = padding,
            contentPaddingRight = padding,
            contentPaddingBottom = padding,
        )
    }

    /**
     * fallback 到 bridge 时生成等价 legacy surface 节点。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return PixelSurface(
            child = childNodes.asList().singleOrNull(),
            modifier = PixelModifier.Empty,
            fillTone = fillTone,
            borderTone = borderTone,
            padding = padding,
            alignment = alignment.toPixelAlignment(),
            key = key,
        )
    }
}
