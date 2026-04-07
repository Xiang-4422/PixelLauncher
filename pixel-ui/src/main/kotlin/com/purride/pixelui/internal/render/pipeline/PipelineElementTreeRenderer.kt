package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelTextRasterizer

/**
 * 新渲染管线对 retained element tree 的渲染入口。
 *
 * 第一版先复用现有 bridge tree resolver，把“已能稳定解析的中间树”降成最小
 * `RenderObject` 树；如果整棵树无法完全识别，就返回 null 交给 fallback 路径。
 */
internal class PipelineElementTreeRenderer(
    private val bridgeTreeResolver: BridgeTreeResolving,
    defaultTextRasterizer: PixelTextRasterizer = PixelBitmapFont.Default,
) : ElementTreeRenderer {
    private val capabilityChecker = PipelineTreeCapabilityChecker
    private val treeLowering = PipelineBridgeTreeLowering(
        defaultTextRasterizer = defaultTextRasterizer,
    )

    /**
     * 判断当前 element tree 是否能完整走新 pipeline。
     */
    fun canRender(request: ElementTreeRenderRequest): Boolean {
        return inspect(request).supported
    }

    /**
     * 返回当前 element tree 的 pipeline 能力检查结果。
     */
    fun inspect(request: ElementTreeRenderRequest): PipelineCapabilityReport {
        val bridgeRoot = bridgeTreeResolver.resolve(
            request = BridgeTreeResolveRequest(
                root = request.root,
            ),
        ) ?: return PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.UNSUPPORTED_NODE_TYPE)
        return capabilityChecker.inspect(bridgeRoot)
    }

    /**
     * 尝试用新 pipeline 渲染当前 element tree；不支持时返回 null。
     */
    fun renderOrNull(request: ElementTreeRenderRequest): PixelRenderResult? {
        val bridgeRoot = bridgeTreeResolver.resolve(
            request = BridgeTreeResolveRequest(
                root = request.root,
            ),
        ) ?: return null
        if (!capabilityChecker.inspect(bridgeRoot).supported) {
            return null
        }
        val renderRoot = treeLowering.lower(bridgeRoot) ?: return null
        return PipelineOwner(
            root = renderRoot,
        ).render(
            logicalWidth = request.logicalWidth,
            logicalHeight = request.logicalHeight,
        )
    }

    /**
     * 用新 pipeline 渲染当前 element tree；若不支持则直接抛错交给上层 fallback。
     */
    override fun render(request: ElementTreeRenderRequest): PixelRenderResult {
        return renderOrNull(request)
            ?: error("当前 element tree 还不能完整走新渲染管线。")
    }
}
