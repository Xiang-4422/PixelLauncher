package com.purride.pixelui.internal

/**
 * 新渲染管线对 retained element tree 的渲染入口。
 *
 * 当前 renderer 只消费 direct `RenderObject` tree，不再从 bridge/legacy 中间表示
 * lowering。未接入 render object 的 widget 会直接失败，避免生产路径静默回退。
 */
internal class PipelineElementTreeRenderer : ElementTreeRenderer {
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
        return if (request.root.findPipelineRenderRoot() != null) {
            PipelineCapabilityReport.supported()
        } else {
            PipelineCapabilityReport.unsupported(PipelineUnsupportedReason.MISSING_RENDER_OBJECT_ROOT)
        }
    }

    /**
     * 尝试用新 pipeline 渲染当前 element tree；未找到 direct render root 时返回 null。
     */
    fun renderOrNull(request: ElementTreeRenderRequest): PixelRenderResult? {
        val renderRoot = request.root.findPipelineRenderRoot() ?: return null
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

    /**
     * 直接从 retained element tree 查找可挂载到 pipeline 的 render root。
     */
    private fun Element?.findPipelineRenderRoot(): RenderBox? {
        this ?: return null
        return findRenderObject() as? RenderBox
    }
}

/**
 * 描述 retained element tree 是否存在 direct pipeline render root。
 */
internal data class PipelineCapabilityReport(
    val supported: Boolean,
    val reason: PipelineUnsupportedReason? = null,
) {
    companion object {
        /**
         * 创建支持 direct pipeline 的检查结果。
         */
        fun supported(): PipelineCapabilityReport {
            return PipelineCapabilityReport(supported = true)
        }

        /**
         * 创建不支持 direct pipeline 的检查结果。
         */
        fun unsupported(reason: PipelineUnsupportedReason): PipelineCapabilityReport {
            return PipelineCapabilityReport(
                supported = false,
                reason = reason,
            )
        }
    }
}

/**
 * 当前 direct pipeline 入口失败原因。
 */
internal enum class PipelineUnsupportedReason {
    MISSING_RENDER_OBJECT_ROOT,
}
