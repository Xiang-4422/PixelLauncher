package com.purride.pixelui.internal

import com.purride.pixelui.Alignment
import com.purride.pixelui.AlignmentDirectional
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Directionality
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.EdgeInsetsDirectional
import com.purride.pixelui.InternalBuildContext
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.resolve
import com.purride.pixelui.internal.legacy.PixelAlignment
import com.purride.pixelui.internal.legacy.PixelBox
import com.purride.pixelui.internal.legacy.PixelModifier
import com.purride.pixelui.internal.legacy.fillMaxSize
import com.purride.pixelui.internal.legacy.height
import com.purride.pixelui.internal.legacy.padding
import com.purride.pixelui.internal.legacy.size
import com.purride.pixelui.internal.legacy.width
import com.purride.pixelui.internal.toPixelAlignment

/**
 * Flutter 风格 `Padding` 的直接 render object widget。
 *
 * 它仍实现 `BridgeWidget`，用于尚未迁移的旧父节点 fallback。
 */
internal data class PaddingWidget(
    override val child: Widget,
    val padding: EdgeInsets,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
), BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    /**
     * 创建承接 padding 的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
        )
    }

    /**
     * 同步新的 padding 配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = PixelAlignment.TOP_START,
            contentPaddingLeft = padding.left,
            contentPaddingTop = padding.top,
            contentPaddingRight = padding.right,
            contentPaddingBottom = padding.bottom,
        )
    }

    /**
     * fallback 到 bridge 时生成等价 legacy box 节点。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return PixelBox(
            children = childNodes.asList(),
            modifier = PixelModifier.Empty.padding(
                left = padding.left,
                top = padding.top,
                right = padding.right,
                bottom = padding.bottom,
            ),
            alignment = PixelAlignment.TOP_START,
        )
    }
}

/**
 * 方向性感知的 padding bridge widget。
 */
internal data class PaddingDirectionalWidget(
    val child: Widget,
    val padding: EdgeInsetsDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析 padding。
     */
    override fun build(context: BuildContext): Widget {
        val resolvedPadding = padding.resolve(Directionality.of(context))
        return PaddingWidget(
            child = child,
            padding = resolvedPadding,
            key = key,
        )
    }
}

/**
 * Flutter 风格 `Align` 的直接 render object widget。
 */
internal data class AlignWidget(
    override val child: Widget,
    val alignment: Alignment,
    override val key: Any? = null,
) : SingleChildRenderObjectWidget(
    child = child,
    key = key,
), BridgeWidget {
    override val childWidgets: List<Widget>
        get() = listOf(child)

    /**
     * 创建承接对齐的 surface render object。
     */
    override fun createRenderObject(context: InternalBuildContext): RenderObject {
        return RenderSurface(
            fillTone = null,
            borderTone = null,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
        )
    }

    /**
     * 同步新的对齐配置到既有 surface render object。
     */
    override fun updateRenderObject(
        context: InternalBuildContext,
        renderObject: RenderObject,
    ) {
        (renderObject as RenderSurface).updateSurface(
            fillTone = null,
            borderTone = null,
            alignment = alignment.toPixelAlignment(),
            fillMaxWidth = true,
            fillMaxHeight = true,
        )
    }

    /**
     * fallback 到 bridge 时生成等价 legacy box 节点。
     */
    override fun createBridgeNode(
        context: BuildContext,
        childNodes: BridgeNodeChildren,
    ): BridgeRenderNode {
        return PixelBox(
            children = childNodes.asList(),
            modifier = PixelModifier.Empty.fillMaxSize(),
            alignment = alignment.toPixelAlignment(),
        )
    }
}

/**
 * 方向性感知的 `Align` bridge widget。
 */
internal data class AlignDirectionalWidget(
    val child: Widget,
    val alignment: AlignmentDirectional,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 在 build 时按当前方向解析对齐。
     */
    override fun build(context: BuildContext): Widget {
        return LegacySingleChildWidget(
            key = key,
            child = child,
        ) { _, childNode ->
            PixelBox(
                children = listOf(childNode),
                modifier = PixelModifier.Empty.fillMaxSize(),
                alignment = alignment.toPixelAlignment(Directionality.of(context)),
            )
        }
    }
}

/**
 * Flutter 风格 `SizedBox` 的 bridge widget。
 */
internal data class SizedBoxWidget(
    val width: Int?,
    val height: Int?,
    val child: Widget?,
    override val key: Any? = null,
) : StatelessWidget(
    key = key,
) {
    /**
     * 用 box 和固定尺寸 modifier 承接尺寸约束。
     */
    override fun build(context: BuildContext): Widget {
        return LegacyMultiChildWidget(
            key = key,
            children = child?.let(::listOf) ?: emptyList(),
        ) { _, childNodes ->
            val baseModifier = when {
                width != null && height != null -> PixelModifier.Empty.size(width, height)
                width != null -> PixelModifier.Empty.width(width)
                height != null -> PixelModifier.Empty.height(height)
                else -> PixelModifier.Empty
            }
            PixelBox(
                children = childNodes,
                modifier = baseModifier,
                alignment = PixelAlignment.TOP_START,
            )
        }
    }
}
